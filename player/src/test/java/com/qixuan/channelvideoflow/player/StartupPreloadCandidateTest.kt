package com.qixuan.channelvideoflow.player

import com.qixuan.channelvideoflow.domain.media.AdaptivePreloadDecision
import com.qixuan.channelvideoflow.domain.media.AdaptivePreloadReason
import com.qixuan.channelvideoflow.domain.media.AdaptivePreloadState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupPreloadCandidateTest {
    @Test
    fun everyCandidateHasAnExactHardSpeculativeByteLimit() {
        val sizes = StartupPreloadCandidate.entries.associateWith { candidate ->
            StartupPreloadPlanner.plan(candidate, wifiDecision(), fileSize = 4L * 1024L * 1024L)
        }

        assertEquals(256L * 1024L, sizes.getValue(StartupPreloadCandidate.BASELINE).totalBytes)
        assertEquals(320L * 1024L, sizes.getValue(StartupPreloadCandidate.TAIL_64).totalBytes)
        assertEquals(384L * 1024L, sizes.getValue(StartupPreloadCandidate.TAIL_128).totalBytes)
        assertEquals(512L * 1024L, sizes.getValue(StartupPreloadCandidate.HEAD_512_WIFI).totalBytes)
        sizes.forEach { (candidate, plan) ->
            assertTrue(plan.totalBytes <= StartupPreloadPlanner.byteLimit(candidate))
        }
    }

    @Test
    fun unknownFileSizeNeverProducesATailGuess() {
        listOf(StartupPreloadCandidate.TAIL_64, StartupPreloadCandidate.TAIL_128).forEach {
            val plan = StartupPreloadPlanner.plan(it, wifiDecision(), fileSize = null)

            assertEquals(StartupPreloadPlanner.BASELINE_HEAD_BYTES, plan.head?.length)
            assertNull(plan.tail)
            assertEquals(StartupPreloadPlanner.BASELINE_HEAD_BYTES, plan.totalBytes)
        }
    }

    @Test
    fun smallFilesNeverProduceNegativeOrOverlappingTailRanges() {
        val smallerThanHead = StartupPreloadPlanner.plan(
            StartupPreloadCandidate.TAIL_128,
            wifiDecision(),
            fileSize = 64L * 1024L,
        )
        val barelyLargerThanHead = StartupPreloadPlanner.plan(
            StartupPreloadCandidate.TAIL_128,
            wifiDecision(),
            fileSize = 300L * 1024L,
        )

        assertEquals(64L * 1024L, smallerThanHead.head?.length)
        assertNull(smallerThanHead.tail)
        assertEquals(256L * 1024L, barelyLargerThanHead.tail?.offset)
        assertEquals(44L * 1024L, barelyLargerThanHead.tail?.length)
        assertTrue(barelyLargerThanHead.tail!!.offset >= barelyLargerThanHead.head!!.length)
    }

    @Test
    fun head512IsLimitedToUnmeteredWifiAndNeverStacksWithTail() {
        val wifi = StartupPreloadPlanner.plan(
            StartupPreloadCandidate.HEAD_512_WIFI,
            wifiDecision(),
            fileSize = 2L * 1024L * 1024L,
        )
        val metered = StartupPreloadPlanner.plan(
            StartupPreloadCandidate.HEAD_512_WIFI,
            wifiDecision(isUnmeteredWifi = false),
            fileSize = 2L * 1024L * 1024L,
        )

        assertEquals(512L * 1024L, wifi.head?.length)
        assertNull(wifi.tail)
        assertEquals(256L * 1024L, wifi.extraBytes)
        assertEquals(256L * 1024L, metered.head?.length)
        assertNull(metered.tail)
        assertEquals(0L, metered.extraBytes)
    }

    @Test
    fun hardBlockedPolicyProducesZeroRequestsForEveryCandidate() {
        StartupPreloadCandidate.entries.forEach { candidate ->
            val plan = StartupPreloadPlanner.plan(candidate, offDecision(), fileSize = 1_000_000L)
            assertNull(plan.head)
            assertNull(plan.tail)
            assertEquals(0L, plan.totalBytes)
        }
    }

    @Test
    fun buildValuesMapToExactlyOneAuditableCandidate() {
        StartupPreloadCandidate.entries.forEach { candidate ->
            assertEquals(candidate, StartupPreloadCandidate.fromBuildValue(candidate.name))
        }
    }

    private fun wifiDecision(isUnmeteredWifi: Boolean = true) = AdaptivePreloadDecision(
        state = AdaptivePreloadState.NORMAL,
        reason = AdaptivePreloadReason.STABLE,
        maxPreloadBytes = 256L * 1024L,
        recentSampleCount = 5,
        recentP90Millis = 300L,
        isUnmeteredWifi = isUnmeteredWifi,
    )

    private fun offDecision() = AdaptivePreloadDecision(
        state = AdaptivePreloadState.OFF,
        reason = AdaptivePreloadReason.NETWORK_NOT_ALLOWED,
        maxPreloadBytes = 0L,
        recentSampleCount = 0,
        recentP90Millis = null,
        isUnmeteredWifi = false,
    )
}
