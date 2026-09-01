package com.qixuan.channelvideoflow.domain.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NextPreloadBudgetControllerTest {
    @Test
    fun bufferReservoirProducesZeroTwoFiveAndTenMibTiers() {
        val zero = evaluate(buffer = 7.9, bitrate = 8_000_000L)
        val two = evaluate(buffer = 20.0, bitrate = 8_000_000L)
        val five = evaluate(buffer = 30.0, bitrate = 8_000_000L)
        val ten = evaluate(buffer = 40.0, bitrate = 8_000_000L)

        assertEquals(NextPreloadBudgetTier.BLOCKED, zero.allowedBudgetTier)
        assertEquals(0L, zero.calculatedTargetBytes)
        assertEquals(2L * MIB, two.calculatedTargetBytes)
        assertEquals(5L * MIB, five.calculatedTargetBytes)
        assertEquals(10L * MIB, ten.calculatedTargetBytes)
    }

    @Test
    fun mobileMeteredStartupSeekAndRebufferAlwaysHaveZeroMediaBudget() {
        listOf(
            safe(40.0).copy(isMobileNetwork = true),
            safe(40.0).copy(isMetered = true),
            safe(40.0).copy(playbackState = PlaybackRiskState.STARTUP),
            safe(40.0).copy(playbackState = PlaybackRiskState.SEEK),
            safe(40.0).copy(playbackState = PlaybackRiskState.REBUFFER),
        ).forEach { safety ->
            val decision = NextPreloadBudgetController.evaluate(
                NextPreloadBudgetInput(safety, 8_000_000L, 0L, 0L),
            )
            assertEquals(0L, decision.remainingNewNetworkBudgetBytes)
        }
    }

    @Test
    fun fallingLowBufferCancelsWhileEightToFifteenSecondsIsMetadataOnly() {
        val falling = NextPreloadBudgetController.evaluate(
            NextPreloadBudgetInput(
                safe(20.0).copy(bufferSlopeSecondsPerSecond = -0.2),
                1_000_000L,
                0L,
                0L,
            ),
        )
        val metadata = evaluate(buffer = 12.0, bitrate = 1_000_000L)

        assertEquals(NextPreloadStopReason.BUFFER_FALLING, falling.preloadStopReason)
        assertEquals(NextPreloadBudgetTier.METADATA_ONLY, metadata.allowedBudgetTier)
        assertEquals(0L, metadata.calculatedTargetBytes)
    }

    @Test
    fun cachedBytesDoNotCountAsNewNetworkAndHardCeilingCanNeverBeExceeded() {
        val target = NextPreloadBudgetController.evaluate(
            NextPreloadBudgetInput(
                safety = safe(40.0),
                peakBitrateBitsPerSecond = 20_000_000L,
                cachedCoveredBytes = 3L * MIB,
                downloadedNewNetworkBytes = 6L * MIB,
            ),
        )

        assertEquals(10L * MIB, target.calculatedTargetBytes)
        assertEquals(1L * MIB, target.remainingNewNetworkBudgetBytes)
        val exceededInput = target.copy(downloadedNewNetworkBytes = 10L * MIB)
        assertTrue(exceededInput.downloadedNewNetworkBytes <= NextPreloadBudgetController.ABSOLUTE_MAX_BYTES)
    }

    @Test
    fun unknownBitrateStaysAtMinimumInsteadOfJumpingToTenMib() {
        val decision = NextPreloadBudgetController.evaluate(
            NextPreloadBudgetInput(safe(40.0), null, 0L, 0L),
        )

        assertEquals(256L * 1024L, decision.calculatedTargetBytes)
        assertEquals(NextPreloadStopReason.UNRELIABLE_BITRATE, decision.preloadStopReason)
    }

    @Test
    fun hlsRoundsToCompleteSegmentAndRefusesSegmentBeyondCurrentTier() {
        val boundaries = listOf(
            HlsPlayableBoundary(3.0, 1L * MIB),
            HlsPlayableBoundary(6.0, 3L * MIB),
            HlsPlayableBoundary(10.0, 7L * MIB),
        )
        val fiveSecond = NextPreloadBudgetController.evaluate(
            NextPreloadBudgetInput(safe(30.0), 8_000_000L, 0L, 0L, boundaries),
        )
        assertEquals(3L * MIB, fiveSecond.calculatedTargetBytes)

        val tooLargeFirstSegment = NextPreloadBudgetController.evaluate(
            NextPreloadBudgetInput(
                safe(20.0),
                8_000_000L,
                0L,
                0L,
                listOf(HlsPlayableBoundary(3.0, 3L * MIB)),
            ),
        )
        assertEquals(0L, tooLargeFirstSegment.calculatedTargetBytes)
        assertEquals(NextPreloadStopReason.SEGMENT_EXCEEDS_TIER, tooLargeFirstSegment.preloadStopReason)
    }

    @Test
    fun eachReevaluationExposesAtMostOneBoundedChunk() {
        val decision = evaluate(buffer = 40.0, bitrate = 20_000_000L)
        val chunk = minOf(
            decision.remainingNewNetworkBudgetBytes,
            NextPreloadBudgetController.RANGE_CHUNK_BYTES,
        )
        assertEquals(512L * 1024L, chunk)
    }

    private fun evaluate(buffer: Double, bitrate: Long?) = NextPreloadBudgetController.evaluate(
        NextPreloadBudgetInput(safe(buffer), bitrate, 0L, 0L),
    )

    private fun safe(buffer: Double) = NextPreloadSafetySnapshot(
        playbackState = PlaybackRiskState.PLAYING,
        currentBufferedSeconds = buffer,
        bufferSlopeSecondsPerSecond = 0.1,
        fastThroughputBitsPerSecond = 12_000_000L,
        slowThroughputBitsPerSecond = 11_000_000L,
        timeToFirstByteP90Millis = 120L,
        isMetered = false,
        isMobileNetwork = false,
    )

    private companion object {
        const val MIB = 1024L * 1024L
    }
}
