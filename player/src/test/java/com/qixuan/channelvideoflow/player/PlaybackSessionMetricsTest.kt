package com.qixuan.channelvideoflow.player

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackSessionMetricsTest {
    @Test
    fun bufferingCountsAsRebufferOnlyAfterTheFirstRenderedFrame() {
        var now = 0L
        val metrics = PlaybackSessionMetrics(nowMillis = { now })
        metrics.start()

        metrics.onPlaybackStateChanged(Player.STATE_READY, playbackExpected = true)
        now = 100L
        val beforeFirstFrame = metrics.onPlaybackStateChanged(
            Player.STATE_BUFFERING,
            playbackExpected = true,
        )
        now = 200L
        metrics.onPlaybackStateChanged(Player.STATE_READY, playbackExpected = true)
        metrics.markFirstFrame()
        now = 300L
        val afterFirstFrame = metrics.onPlaybackStateChanged(
            Player.STATE_BUFFERING,
            playbackExpected = true,
        )

        assertEquals(0, beforeFirstFrame.rebufferCount)
        assertEquals(1, afterFirstFrame.rebufferCount)
    }

    @Test
    fun thirtyAndSixtySecondWindowsStartAtTheFirstRenderedFrame() {
        var now = 1_000L
        val metrics = PlaybackSessionMetrics(nowMillis = { now })
        metrics.start()
        metrics.onPlaybackStateChanged(Player.STATE_READY, playbackExpected = true)
        metrics.markFirstFrame()

        now = 10_000L
        metrics.onPlaybackStateChanged(Player.STATE_BUFFERING, playbackExpected = true)
        now = 12_000L
        metrics.onPlaybackStateChanged(Player.STATE_READY, playbackExpected = true)
        now = 40_000L
        metrics.onPlaybackStateChanged(Player.STATE_BUFFERING, playbackExpected = true)
        now = 45_000L
        metrics.onPlaybackStateChanged(Player.STATE_READY, playbackExpected = true)
        now = 61_000L
        val snapshot = metrics.snapshot()

        assertEquals(60_000L, snapshot.firstFrameElapsedMillis)
        assertEquals(1, snapshot.rebufferAt30Seconds?.count)
        assertEquals(2_000L, snapshot.rebufferAt30Seconds?.durationMillis)
        assertEquals(2, snapshot.rebufferAt60Seconds?.count)
        assertEquals(7_000L, snapshot.rebufferAt60Seconds?.durationMillis)
    }

    @Test
    fun firstFrameBeforeReadyStillOpensRebufferMeasurementAfterBothArrive() {
        var now = 100L
        val metrics = PlaybackSessionMetrics(nowMillis = { now })
        metrics.start()

        metrics.markFirstFrame()
        now = 150L
        metrics.onPlaybackStateChanged(Player.STATE_READY, playbackExpected = true)
        now = 200L
        val buffering = metrics.onPlaybackStateChanged(
            Player.STATE_BUFFERING,
            playbackExpected = true,
        )

        assertEquals(1, buffering.rebufferCount)
        assertEquals(100L, buffering.firstFrameElapsedMillis)
    }

    @Test
    fun initialBufferingIsNotCountedButAutomaticRebufferIsMeasured() {
        var now = 0L
        val metrics = PlaybackSessionMetrics(nowMillis = { now })
        metrics.start()

        assertEquals(
            0,
            metrics.onPlaybackStateChanged(
                Player.STATE_BUFFERING,
                playbackExpected = true,
            ).rebufferCount,
        )
        now = 2_500L
        metrics.onPlaybackStateChanged(Player.STATE_READY, playbackExpected = true)
        metrics.markFirstFrame()
        now = 8_000L
        val buffering = metrics.onPlaybackStateChanged(
            Player.STATE_BUFFERING,
            playbackExpected = true,
        )

        assertEquals(1, buffering.rebufferCount)
        assertEquals(0L, buffering.activeRebufferDurationMillis)

        now = 11_000L
        assertEquals(3_000L, metrics.snapshot().activeRebufferDurationMillis)
        now = 12_500L
        val recovered = metrics.onPlaybackStateChanged(
            Player.STATE_READY,
            playbackExpected = true,
        )

        assertEquals(1, recovered.rebufferCount)
        assertEquals(4_500L, recovered.recoveredRebufferDurationMillis)
        assertEquals(4_500L, recovered.totalRebufferDurationMillis)
        assertEquals(0L, recovered.activeRebufferDurationMillis)
    }

    @Test
    fun seekBufferingAndPausedBufferingAreNotCounted() {
        var now = 0L
        val metrics = PlaybackSessionMetrics(nowMillis = { now })
        metrics.start()
        metrics.onPlaybackStateChanged(Player.STATE_READY, playbackExpected = true)
        metrics.markFirstFrame()

        metrics.markSeek()
        now = 1_000L
        val seeking = metrics.onPlaybackStateChanged(
            Player.STATE_BUFFERING,
            playbackExpected = true,
        )
        now = 2_000L
        metrics.onPlaybackStateChanged(Player.STATE_READY, playbackExpected = true)
        now = 3_000L
        val paused = metrics.onPlaybackStateChanged(
            Player.STATE_BUFFERING,
            playbackExpected = false,
        )

        assertEquals(0, seeking.rebufferCount)
        assertEquals(0, paused.rebufferCount)
        assertEquals(0L, paused.totalRebufferDurationMillis)
        assertNull(paused.recoveredRebufferDurationMillis)
    }

    @Test
    fun pausingDuringAnActiveRebufferStopsAccumulatingPausedTime() {
        var now = 0L
        val metrics = PlaybackSessionMetrics(nowMillis = { now })
        metrics.start()
        metrics.onPlaybackStateChanged(Player.STATE_READY, playbackExpected = true)
        metrics.markFirstFrame()
        now = 1_000L
        metrics.onPlaybackStateChanged(Player.STATE_BUFFERING, playbackExpected = true)

        now = 3_000L
        metrics.markPaused()
        now = 10_000L
        val paused = metrics.snapshot()

        assertEquals(1, paused.rebufferCount)
        assertEquals(0L, paused.activeRebufferDurationMillis)
        assertEquals(2_000L, paused.totalRebufferDurationMillis)
    }

    @Test
    fun seekingDuringAnActiveRebufferStopsAccumulatingSeekTime() {
        var now = 0L
        val metrics = PlaybackSessionMetrics(nowMillis = { now })
        metrics.start()
        metrics.onPlaybackStateChanged(Player.STATE_READY, playbackExpected = true)
        metrics.markFirstFrame()
        now = 1_000L
        metrics.onPlaybackStateChanged(Player.STATE_BUFFERING, playbackExpected = true)

        now = 2_500L
        metrics.markSeek()
        now = 5_000L
        metrics.onPlaybackStateChanged(Player.STATE_BUFFERING, playbackExpected = true)
        now = 7_000L
        val ready = metrics.onPlaybackStateChanged(Player.STATE_READY, playbackExpected = true)

        assertEquals(1, ready.rebufferCount)
        assertEquals(0L, ready.activeRebufferDurationMillis)
        assertEquals(1_500L, ready.totalRebufferDurationMillis)
    }

    @Test
    fun repeatedBufferingCallbacksDoNotDoubleCountAndResetClearsSession() {
        var now = 0L
        val metrics = PlaybackSessionMetrics(nowMillis = { now })
        metrics.start()
        metrics.onPlaybackStateChanged(Player.STATE_READY, playbackExpected = true)
        metrics.markFirstFrame()
        now = 1_000L
        metrics.onPlaybackStateChanged(Player.STATE_BUFFERING, playbackExpected = true)
        now = 2_000L
        val repeated = metrics.onPlaybackStateChanged(
            Player.STATE_BUFFERING,
            playbackExpected = true,
        )

        assertEquals(1, repeated.rebufferCount)
        assertEquals(1_000L, repeated.activeRebufferDurationMillis)

        metrics.reset()
        val reset = metrics.snapshot()
        assertEquals("IDLE", reset.stateName)
        assertEquals(0, reset.rebufferCount)
        assertEquals(0L, reset.totalRebufferDurationMillis)
    }
}
