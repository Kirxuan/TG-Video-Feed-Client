package com.qixuan.channelvideoflow.domain.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackRiskControllerTest {
    @Test
    fun requestThatCannotBeatStarvationDeadlineIsAbandonedImmediately() {
        val controller = PlaybackRiskController()
        val decision = controller.evaluate(
            input(
                fast = 500_000L,
                slow = 450_000L,
                bufferMillis = 3_000L,
                currentBitrate = 1_500_000L,
                candidateBitrate = 1_500_000L,
                downloaded = 64L * 1024L,
                remaining = 512L * 1024L,
            ),
        )

        assertEquals(PlaybackRiskAction.ABANDON_REQUEST, decision.action)
        assertEquals(PlaybackRiskReason.STARVATION_DEADLINE, decision.reason)
        assertTrue(decision.predictedCompletionMillis!! > decision.starvationDeadlineMillis)
    }

    @Test
    fun throughputDropDowngradesWithoutWaitingForUpgradeCooldown() {
        val controller = PlaybackRiskController()
        val decision = controller.evaluate(
            input(
                fast = 800_000L,
                slow = 900_000L,
                bufferMillis = 30_000L,
                currentBitrate = 1_500_000L,
                candidateBitrate = 1_500_000L,
                downloaded = 1_000_000L,
                remaining = 0L,
            ),
        )

        assertEquals(PlaybackRiskAction.DOWNGRADE, decision.action)
        assertEquals(PlaybackRiskReason.UNSUSTAINABLE_BITRATE, decision.reason)
    }

    @Test
    fun upgradeNeedsFourStableWindowsAndMinimumSwitchInterval() {
        val controller = PlaybackRiskController(
            upgradeStableWindowsRequired = 4,
            minimumSwitchIntervalMillis = 12_000L,
        )
        val downgrade = controller.evaluate(
            input(
                fast = 400_000L,
                slow = 400_000L,
                bufferMillis = 20_000L,
                currentBitrate = 800_000L,
                candidateBitrate = 800_000L,
                downloaded = 0L,
                remaining = 0L,
                nowMillis = 1_000L,
            ),
        )
        assertEquals(PlaybackRiskAction.DOWNGRADE, downgrade.action)

        repeat(3) { index ->
            assertEquals(
                PlaybackRiskAction.KEEP,
                controller.evaluate(
                    input(
                        fast = 3_000_000L,
                        slow = 3_000_000L,
                        bufferMillis = 30_000L,
                        currentBitrate = 450_000L,
                        candidateBitrate = 1_000_000L,
                        downloaded = 0L,
                        remaining = 0L,
                        nowMillis = 14_000L + index,
                    ),
                ).action,
            )
        }
        assertEquals(
            PlaybackRiskAction.UPGRADE,
            controller.evaluate(
                input(
                    fast = 3_000_000L,
                    slow = 3_000_000L,
                    bufferMillis = 30_000L,
                    currentBitrate = 450_000L,
                    candidateBitrate = 1_000_000L,
                    downloaded = 0L,
                    remaining = 0L,
                    nowMillis = 14_100L,
                ),
            ).action,
        )
    }

    @Test
    fun unstableWindowsDevicePressureAndNetworkSwitchPreventOscillation() {
        val controller = PlaybackRiskController(upgradeStableWindowsRequired = 2)
        val stable = input(
            fast = 3_000_000L,
            slow = 3_000_000L,
            bufferMillis = 30_000L,
            currentBitrate = 450_000L,
            candidateBitrate = 1_000_000L,
            downloaded = 0L,
            remaining = 0L,
            nowMillis = 20_000L,
        )
        assertEquals(PlaybackRiskAction.KEEP, controller.evaluate(stable).action)
        assertEquals(
            PlaybackRiskAction.KEEP,
            controller.evaluate(stable.copy(bufferSlopeSecondsPerSecond = -0.1)).action,
        )
        assertEquals(PlaybackRiskAction.KEEP, controller.evaluate(stable).action)
        assertEquals(
            PlaybackRiskAction.KEEP,
            controller.evaluate(stable.copy(hasThermalPressure = true)).action,
        )
        assertEquals(
            PlaybackRiskAction.KEEP,
            controller.evaluate(stable.copy(networkGeneration = 2L)).action,
        )
    }

    @Test
    fun missingEstimateStartsAtLowestSafeRepresentationAndSamePlanDoesNotChurn() {
        val controller = PlaybackRiskController()
        val cold = controller.evaluate(
            input(
                fast = null,
                slow = null,
                bufferMillis = 0L,
                currentBitrate = 1_500_000L,
                candidateBitrate = 1_500_000L,
                downloaded = 0L,
                remaining = 0L,
                state = PlaybackRiskState.STARTUP,
            ),
        )
        assertEquals(PlaybackRiskAction.DOWNGRADE, cold.action)
        val same = controller.evaluate(
            input(
                fast = 2_000_000L,
                slow = 2_000_000L,
                bufferMillis = 20_000L,
                currentBitrate = 450_000L,
                candidateBitrate = 450_000L,
                downloaded = 0L,
                remaining = 0L,
            ),
        )
        assertEquals(PlaybackRiskAction.KEEP, same.action)
        assertEquals(PlaybackRiskReason.SAME_REPRESENTATION, same.reason)
    }

    private fun input(
        fast: Long?,
        slow: Long?,
        bufferMillis: Long,
        currentBitrate: Long,
        candidateBitrate: Long,
        downloaded: Long,
        remaining: Long,
        nowMillis: Long = 20_000L,
        state: PlaybackRiskState = PlaybackRiskState.PLAYING,
    ) = PlaybackRiskInput(
        fastThroughputBitsPerSecond = fast,
        slowThroughputBitsPerSecond = slow,
        timeToFirstByteP50Millis = 80L,
        timeToFirstByteP90Millis = 180L,
        currentBufferedDurationMillis = bufferMillis,
        bufferSlopeSecondsPerSecond = 0.1,
        currentRepresentationBitrate = currentBitrate,
        candidatePeakBitrate = candidateBitrate,
        minimumRepresentationBitrate = 450_000L,
        currentRequestDownloadedBytes = downloaded,
        currentRequestRemainingBytes = remaining,
        nextPlayableSeconds = 0.0,
        playbackState = state,
        isMetered = false,
        isPowerSaver = false,
        hasThermalPressure = false,
        hasStoragePressure = false,
        networkGeneration = 1L,
        nowMillis = nowMillis,
    )
}
