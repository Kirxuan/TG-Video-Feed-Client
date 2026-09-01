package com.qixuan.channelvideoflow.player

import androidx.media3.common.Player

internal data class PlaybackBufferingMetrics(
    val stateName: String,
    val rebufferCount: Int,
    val activeRebufferDurationMillis: Long,
    val totalRebufferDurationMillis: Long,
    val recoveredRebufferDurationMillis: Long? = null,
    val firstFrameElapsedMillis: Long? = null,
    val rebufferAt30Seconds: RebufferWindowMetrics? = null,
    val rebufferAt60Seconds: RebufferWindowMetrics? = null,
)

internal data class RebufferWindowMetrics(
    val count: Int,
    val durationMillis: Long,
)

/**
 * Tracks one bound video's buffering lifecycle without retaining video metadata.
 *
 * Initial buffering, user-paused buffering, and buffering caused by an explicit seek are not
 * counted as rebuffer events.
 */
internal class PlaybackSessionMetrics(
    private val nowMillis: () -> Long = { System.nanoTime() / NANOS_PER_MILLISECOND },
) {
    private var active = false
    private var hasReachedReady = false
    private var hasRenderedFirstFrame = false
    private var firstFrameAtMillis: Long? = null
    private var seekPending = false
    private var rebufferStartedAtMillis: Long? = null
    private var completedRebufferDurationMillis = 0L
    private var rebufferCount = 0
    private val rebufferEvents = mutableListOf<RebufferEvent>()
    private var playbackState = Player.STATE_IDLE

    val isActive: Boolean
        get() = active

    fun start() {
        active = true
        hasReachedReady = false
        hasRenderedFirstFrame = false
        firstFrameAtMillis = null
        seekPending = false
        rebufferStartedAtMillis = null
        completedRebufferDurationMillis = 0L
        rebufferCount = 0
        rebufferEvents.clear()
        playbackState = Player.STATE_IDLE
    }

    fun reset() {
        active = false
        hasReachedReady = false
        hasRenderedFirstFrame = false
        firstFrameAtMillis = null
        seekPending = false
        rebufferStartedAtMillis = null
        completedRebufferDurationMillis = 0L
        rebufferCount = 0
        rebufferEvents.clear()
        playbackState = Player.STATE_IDLE
    }

    fun markSeek() {
        if (active) {
            finishActiveRebuffer(nowMillis())
            seekPending = true
        }
    }

    fun markPaused() {
        if (active) finishActiveRebuffer(nowMillis())
    }

    fun markFirstFrame() {
        if (active && !hasRenderedFirstFrame) {
            hasRenderedFirstFrame = true
            firstFrameAtMillis = nowMillis()
        }
    }

    fun onPlaybackStateChanged(
        newPlaybackState: Int,
        playbackExpected: Boolean,
    ): PlaybackBufferingMetrics {
        if (!active) return snapshot()
        val now = nowMillis()
        var recoveredDurationMillis: Long? = null
        when (newPlaybackState) {
            Player.STATE_BUFFERING -> {
                if (
                    hasReachedReady &&
                    hasRenderedFirstFrame &&
                    playbackExpected &&
                    !seekPending &&
                    rebufferStartedAtMillis == null
                ) {
                    rebufferCount += 1
                    rebufferStartedAtMillis = now
                    rebufferEvents += RebufferEvent(startedAtMillis = now)
                }
            }

            Player.STATE_READY -> {
                hasReachedReady = true
                seekPending = false
                recoveredDurationMillis = finishActiveRebuffer(now)
            }

            Player.STATE_IDLE,
            Player.STATE_ENDED,
            -> {
                seekPending = false
                finishActiveRebuffer(now)
            }
        }
        playbackState = newPlaybackState
        return snapshot(now, recoveredDurationMillis)
    }

    fun snapshot(): PlaybackBufferingMetrics = snapshot(nowMillis(), recoveredDurationMillis = null)

    private fun finishActiveRebuffer(now: Long): Long? =
        rebufferStartedAtMillis?.let { startedAt ->
            val durationMillis = (now - startedAt).coerceAtLeast(0L)
            completedRebufferDurationMillis += durationMillis
            rebufferStartedAtMillis = null
            rebufferEvents.lastOrNull { event -> event.endedAtMillis == null }
                ?.endedAtMillis = now
            durationMillis
        }

    private fun snapshot(
        now: Long,
        recoveredDurationMillis: Long?,
    ): PlaybackBufferingMetrics {
        val activeDuration = rebufferStartedAtMillis
            ?.let { startedAt -> (now - startedAt).coerceAtLeast(0L) }
            ?: 0L
        val firstFrameAt = firstFrameAtMillis
        val firstFrameElapsed = firstFrameAt?.let { startedAt ->
            (now - startedAt).coerceAtLeast(0L)
        }
        return PlaybackBufferingMetrics(
            stateName = playbackStateName(playbackState),
            rebufferCount = rebufferCount,
            activeRebufferDurationMillis = activeDuration,
            totalRebufferDurationMillis = completedRebufferDurationMillis + activeDuration,
            recoveredRebufferDurationMillis = recoveredDurationMillis,
            firstFrameElapsedMillis = firstFrameElapsed,
            rebufferAt30Seconds = firstFrameAt?.let { startedAt ->
                completedWindow(startedAt, THIRTY_SECONDS_MILLIS, now)
            },
            rebufferAt60Seconds = firstFrameAt?.let { startedAt ->
                completedWindow(startedAt, SIXTY_SECONDS_MILLIS, now)
            },
        )
    }

    private fun completedWindow(
        firstFrameAtMillis: Long,
        windowMillis: Long,
        now: Long,
    ): RebufferWindowMetrics? {
        val windowEnd = firstFrameAtMillis + windowMillis
        if (now < windowEnd) return null
        val included = rebufferEvents.filter { event -> event.startedAtMillis < windowEnd }
        return RebufferWindowMetrics(
            count = included.size,
            durationMillis = included.sumOf { event ->
                val end = minOf(event.endedAtMillis ?: now, windowEnd)
                (end - event.startedAtMillis).coerceAtLeast(0L)
            },
        )
    }

    private data class RebufferEvent(
        val startedAtMillis: Long,
        var endedAtMillis: Long? = null,
    )

    private companion object {
        const val NANOS_PER_MILLISECOND = 1_000_000L
        const val THIRTY_SECONDS_MILLIS = 30_000L
        const val SIXTY_SECONDS_MILLIS = 60_000L
    }
}

internal fun playbackStateName(playbackState: Int): String = when (playbackState) {
    Player.STATE_IDLE -> "IDLE"
    Player.STATE_BUFFERING -> "BUFFERING"
    Player.STATE_READY -> "READY"
    Player.STATE_ENDED -> "ENDED"
    else -> "UNKNOWN"
}
