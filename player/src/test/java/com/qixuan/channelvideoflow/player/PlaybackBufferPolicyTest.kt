package com.qixuan.channelvideoflow.player

import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackBufferPolicyTest {
    @Test
    fun sizeBackBufferAndStartOrderUseBoundedDiscreteValues() {
        val policy = PlaybackBufferPolicy(
            candidateId = "safe",
            minBufferMillis = 50_000,
            maxBufferMillis = 60_000,
            bufferForPlaybackMillis = 2_500,
            bufferForPlaybackAfterRebufferMillis = 12_000,
            prioritizeTimeOverSizeThresholds = true,
            backBufferMillis = 0,
            targetBufferBytes = -1,
            startOrder = PlaybackStartOrder.PREPARE_THEN_PLAY,
        )

        assertTrue(policy.prioritizeTimeOverSizeThresholds)
        assertThrows(IllegalArgumentException::class.java) {
            policy.copy(backBufferMillis = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            policy.copy(targetBufferBytes = 0)
        }
    }

    @Test
    fun startupAndRebufferThresholdsCannotExceedTheMinimumBuffer() {
        assertThrows(IllegalArgumentException::class.java) {
            PlaybackBufferPolicy(
                candidateId = "unsafe-startup",
                minBufferMillis = 2_000,
                maxBufferMillis = 60_000,
                bufferForPlaybackMillis = 2_001,
                bufferForPlaybackAfterRebufferMillis = 1_000,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            PlaybackBufferPolicy(
                candidateId = "unsafe-rebuffer",
                minBufferMillis = 10_000,
                maxBufferMillis = 60_000,
                bufferForPlaybackMillis = 1_000,
                bufferForPlaybackAfterRebufferMillis = 10_001,
            )
        }
    }
}
