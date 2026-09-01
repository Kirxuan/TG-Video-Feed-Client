package com.qixuan.channelvideoflow.domain.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingNetworkMetricsEstimatorTest {
    @Test
    fun publishesConservativeWeightedMedianAfterThreeReliableSamples() {
        val estimator = StreamingNetworkMetricsEstimator()
        estimator.resetNetworkContext(NetworkTransport.WIFI, 1L)

        estimator.recordAtBitsPerSecond(900_000L)
        estimator.recordAtBitsPerSecond(1_000_000L)
        assertNull(estimator.estimate.value)
        estimator.recordAtBitsPerSecond(1_100_000L)

        assertEquals(1_000_000L, estimator.estimate.value?.medianBitsPerSecond)
        assertEquals(700_000L, estimator.estimate.value?.availableBitsPerSecond)
        assertEquals(3, estimator.estimate.value?.reliableSampleCount)
    }

    @Test
    fun ignoresSmallZeroOutlierAndOldContextSamples() {
        val estimator = StreamingNetworkMetricsEstimator()
        estimator.resetNetworkContext(NetworkTransport.WIFI, 1L)
        val oldContext = estimator.contextRevision
        estimator.recordNetworkProgress(1L, 1_000_000L, oldContext)
        estimator.recordNetworkProgress(64L * 1024L, 0L, oldContext)
        estimator.recordNetworkProgress(64L * 1024L, 1L, oldContext)
        estimator.resetNetworkContext(NetworkTransport.MOBILE, 2L)
        repeat(3) {
            estimator.recordNetworkProgress(64L * 1024L, 500_000_000L, oldContext)
        }

        assertNull(estimator.estimate.value)
    }

    @Test
    fun rebufferDowngradesImmediatelyAndUpgradeNeedsReliableStreak() {
        val estimator = StreamingNetworkMetricsEstimator()
        estimator.resetNetworkContext(NetworkTransport.WIFI, 1L)
        repeat(3) { estimator.recordAtBitsPerSecond(2_000_000L) }
        val before = estimator.estimate.value!!.availableBitsPerSecond

        estimator.onRebuffer()

        val degraded = estimator.estimate.value!!.availableBitsPerSecond
        assertEquals((before * 0.60).toLong(), degraded)
        assertEquals(0, estimator.estimate.value!!.reliableSampleCount)
        repeat(4) { estimator.recordAtBitsPerSecond(4_000_000L) }
        assertEquals(degraded, estimator.estimate.value!!.availableBitsPerSecond)
        estimator.recordAtBitsPerSecond(4_000_000L)
        assertTrue(estimator.estimate.value!!.availableBitsPerSecond > degraded)
    }

    @Test
    fun networkAndSessionResetClearAllSamples() {
        val estimator = StreamingNetworkMetricsEstimator()
        estimator.resetNetworkContext(NetworkTransport.WIFI, 1L)
        repeat(3) { estimator.recordAtBitsPerSecond(1_000_000L) }
        assertTrue(estimator.estimate.value != null)

        estimator.resetNetworkContext(NetworkTransport.WIFI, 2L)
        assertNull(estimator.estimate.value)
        repeat(3) { estimator.recordAtBitsPerSecond(1_000_000L) }
        estimator.resetSession()
        assertNull(estimator.estimate.value)
    }

    @Test
    fun fastEwmaDowngradesOnTheFirstLargeDropAndSecondDropConfirmsIt() {
        val estimator = StreamingNetworkMetricsEstimator()
        estimator.resetNetworkContext(NetworkTransport.WIFI, 1L)
        repeat(5) { estimator.recordAtBitsPerSecond(4_000_000L) }
        val fast = estimator.estimate.value!!.availableBitsPerSecond

        estimator.recordAtBitsPerSecond(1_000_000L)
        assertTrue(estimator.estimate.value!!.availableBitsPerSecond < fast)
        estimator.recordAtBitsPerSecond(1_000_000L)

        assertEquals(700_000L, estimator.estimate.value!!.availableBitsPerSecond)
    }

    @Test
    fun fastSlowEwmaAndTtfbPercentilesUseOnlyActiveNetworkSamples() {
        val estimator = StreamingNetworkMetricsEstimator()
        estimator.resetNetworkContext(NetworkTransport.WIFI, 44L)
        repeat(5) { index ->
            estimator.recordTdLibTransfer(
                TdLibNetworkTransferSample(
                    bytes = 64L * 1024L,
                    durationNanos = 524_288_000L,
                    contextRevision = estimator.contextRevision,
                    timeToFirstByteNanos = (listOf(30L, 80L, 120L, 180L, 1_000L)[index]) * 1_000_000L,
                ),
            )
        }

        val estimate = estimator.estimate.value!!
        assertEquals(1_000_000L, estimate.fastBitsPerSecond)
        assertEquals(1_000_000L, estimate.slowBitsPerSecond)
        assertEquals(120L, estimate.timeToFirstByteP50Millis)
        assertEquals(1_000L, estimate.timeToFirstByteP90Millis)
        assertEquals(NetworkTransport.WIFI, estimate.network)
        assertEquals(44L, estimate.networkGeneration)
    }

    @Test
    fun cachedLocalAndExtremelyFastSamplesCannotPolluteTheEstimator() {
        val estimator = StreamingNetworkMetricsEstimator()
        estimator.resetNetworkContext(NetworkTransport.WIFI, 1L)
        repeat(5) {
            estimator.recordTdLibTransfer(
                TdLibNetworkTransferSample(
                    bytes = 2L * 1024L * 1024L,
                    durationNanos = 1_000L,
                    contextRevision = estimator.contextRevision,
                    isCached = it % 2 == 0,
                    isActiveNetworkDownload = it % 2 == 0,
                ),
            )
        }
        assertNull(estimator.estimate.value)
    }

    private fun StreamingNetworkMetricsEstimator.recordAtBitsPerSecond(bitsPerSecond: Long) {
        val bytes = 64L * 1024L
        val durationNanos = bytes * 8_000_000_000L / bitsPerSecond
        recordNetworkProgress(bytes, durationNanos, contextRevision)
    }
}
