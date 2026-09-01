package com.qixuan.channelvideoflow.player

import com.qixuan.channelvideoflow.model.video.VideoFeedOrder
import com.qixuan.channelvideoflow.model.video.VideoKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackTransitionMetricsTest {
    @Test
    fun randomContextRefreshAndFirstByteRemainAttachedToFirstFrameSample() {
        var now = 100L
        val metrics = PlaybackTransitionMetrics(nowMillis = { now })
        val key = VideoKey(chatId = 61L, messageId = 62L)

        metrics.onEvent(PlaybackTransitionEvent.GestureStarted(observedAtMillis = 100L))
        now = 120L
        metrics.onEvent(
            PlaybackTransitionEvent.TargetKnown(
                key = key,
                order = VideoFeedOrder.RANDOM,
                direction = PlaybackTransitionDirection.FORWARD,
                randomRoundBoundary = true,
            ),
        )
        now = 200L
        metrics.onEvent(
            PlaybackTransitionEvent.PageSettled(
                key = key,
                order = VideoFeedOrder.RANDOM,
                direction = PlaybackTransitionDirection.FORWARD,
                randomRoundBoundary = true,
            ),
        )
        now = 205L
        metrics.onEvent(
            PlaybackTransitionEvent.PlanStarted(
                key = key,
                promoted = true,
                planAgeMillis = 40L,
                preparedRefreshOutcome = PlaybackPlanRefreshOutcome.SUCCESS,
                preparedRefreshMillis = 25L,
            ),
        )
        now = 210L
        metrics.onBindStarted(key)
        metrics.onFirstByte(key, observedAtMillis = 240L)
        metrics.onReady(key, observedAtMillis = 300L)
        val completed = metrics.onFirstFrame(key, observedAtMillis = 330L)

        requireNotNull(completed)
        assertEquals(VideoFeedOrder.RANDOM, completed.order)
        assertEquals(PlaybackTransitionDirection.FORWARD, completed.direction)
        assertEquals(true, completed.randomRoundBoundary)
        assertEquals(true, completed.promoted)
        assertEquals(40L, completed.planAgeMillis)
        assertEquals(PlaybackPlanRefreshOutcome.SUCCESS, completed.refreshOutcome)
        assertEquals(25L, completed.refreshMillis)
        assertEquals(30L, completed.bindToFirstByteMillis)
        assertEquals(90L, completed.bindToReadyMillis)
        assertEquals(120L, completed.bindToTerminalMillis)
    }

    @Test
    fun releaseTargetAndSettledSegmentsSeparateInputAnimationAndPlaybackTime() {
        var now = 100L
        val metrics = PlaybackTransitionMetrics(nowMillis = { now })
        val key = VideoKey(chatId = 31L, messageId = 32L)

        metrics.onEvent(PlaybackTransitionEvent.GestureStarted(observedAtMillis = 100L))
        now = 250L
        metrics.onEvent(PlaybackTransitionEvent.TargetKnown(key))
        metrics.onEvent(PlaybackTransitionEvent.PlanPreparationStarted(key))
        now = 300L
        metrics.onEvent(PlaybackTransitionEvent.PlanPrepared(key))
        now = 400L
        metrics.onEvent(PlaybackTransitionEvent.GestureReleased(observedAtMillis = 400L))
        now = 620L
        metrics.onEvent(PlaybackTransitionEvent.PageSettled(key))
        now = 621L
        metrics.onEvent(PlaybackTransitionEvent.PlanStarted(key, promoted = true))
        metrics.onBindStarted(key)
        now = 800L
        val completed = metrics.onFirstFrame(key)

        requireNotNull(completed)
        assertEquals(300L, completed.gestureToReleaseMillis)
        assertEquals(150L, completed.gestureToTargetKnownMillis)
        assertEquals(220L, completed.releaseToSettledMillis)
        assertEquals(370L, completed.targetKnownToSettledMillis)
        assertEquals(50L, completed.targetKnownToPlanPreparedMillis)
        assertEquals(320L, completed.planPreparedToSettledMillis)
        assertEquals(400L, completed.releaseToTerminalMillis)
        assertEquals(550L, completed.targetKnownToTerminalMillis)
    }

    @Test
    fun changedTargetSupersedesOldTargetAndKeepsTheSameGestureBoundaries() {
        var now = 0L
        val metrics = PlaybackTransitionMetrics(nowMillis = { now })
        val oldTarget = VideoKey(chatId = 41L, messageId = 42L)
        val finalTarget = VideoKey(chatId = 41L, messageId = 43L)

        metrics.onEvent(PlaybackTransitionEvent.GestureStarted(observedAtMillis = 0L))
        now = 100L
        metrics.onEvent(PlaybackTransitionEvent.TargetKnown(oldTarget))
        now = 180L
        metrics.onEvent(PlaybackTransitionEvent.GestureReleased(observedAtMillis = 180L))
        now = 200L
        val superseded = metrics.onEvent(PlaybackTransitionEvent.TargetKnown(finalTarget))
        now = 400L
        metrics.onEvent(PlaybackTransitionEvent.PageSettled(finalTarget))
        now = 500L
        metrics.onBindStarted(finalTarget)
        now = 600L
        val completed = metrics.onFirstFrame(finalTarget)

        requireNotNull(superseded)
        assertEquals(oldTarget, superseded.key)
        assertEquals(PlaybackTransitionOutcome.SUPERSEDED, superseded.outcome)
        requireNotNull(completed)
        assertEquals(180L, completed.gestureToReleaseMillis)
        assertEquals(200L, completed.gestureToTargetKnownMillis)
        assertEquals(420L, completed.releaseToTerminalMillis)
        assertEquals(400L, completed.targetKnownToTerminalMillis)
    }

    @Test
    fun dragThatNeverTargetsAnotherPageEndsAsUnchangedWithoutFirstFrameWait() {
        var now = 0L
        val metrics = PlaybackTransitionMetrics(nowMillis = { now })
        val current = VideoKey(chatId = 51L, messageId = 52L)

        metrics.onEvent(PlaybackTransitionEvent.GestureStarted(observedAtMillis = 0L))
        now = 240L
        metrics.onEvent(PlaybackTransitionEvent.GestureReleased(observedAtMillis = 240L))
        now = 360L
        val unchanged = metrics.onEvent(PlaybackTransitionEvent.PageSettled(current))

        requireNotNull(unchanged)
        assertEquals(PlaybackTransitionOutcome.UNCHANGED, unchanged.outcome)
        assertEquals(240L, unchanged.gestureToReleaseMillis)
        assertEquals(120L, unchanged.releaseToSettledMillis)
        assertNull(unchanged.targetKnownToTerminalMillis)
    }

    @Test
    fun prepareAndFirstFrameBufferingCompleteStartupSegments() {
        var now = 100L
        val metrics = PlaybackTransitionMetrics(nowMillis = { now })
        val key = VideoKey(chatId = 21L, messageId = 22L)

        metrics.onEvent(PlaybackTransitionEvent.PageSettled(key))
        now = 110L
        metrics.onBindStarted(key)
        now = 135L
        metrics.onPrepare(key)
        now = 260L
        metrics.onReady(key)
        now = 300L
        val completed = metrics.onFirstFrame(
            key = key,
            bufferedDurationMillis = 1_750L,
        )

        requireNotNull(completed)
        assertEquals(25L, completed.bindToPrepareMillis)
        assertEquals(125L, completed.prepareToReadyMillis)
        assertEquals(1_750L, completed.firstFrameBufferedDurationMillis)
    }

    @Test
    fun firstFrameCompletesEveryAvailableTransitionSegment() {
        var now = 100L
        val metrics = PlaybackTransitionMetrics(nowMillis = { now })
        val key = VideoKey(chatId = 11L, messageId = 22L)

        assertNull(metrics.onEvent(PlaybackTransitionEvent.PageUnstable))
        now = 180L
        assertNull(metrics.onEvent(PlaybackTransitionEvent.PageSettled(key)))
        now = 430L
        assertNull(metrics.onEvent(PlaybackTransitionEvent.PlanStarted(key)))
        now = 450L
        assertNull(metrics.onEvent(PlaybackTransitionEvent.RefreshStarted(key)))
        now = 750L
        assertNull(
            metrics.onEvent(
                PlaybackTransitionEvent.RefreshFinished(
                    key = key,
                    outcome = PlaybackPlanRefreshOutcome.SUCCESS,
                ),
            ),
        )
        now = 770L
        assertNull(metrics.onBindStarted(key))
        now = 1_270L
        assertNull(metrics.onReady(key))
        now = 1_320L
        val completed = metrics.onFirstFrame(key)

        requireNotNull(completed)
        assertEquals(PlaybackTransitionOutcome.FIRST_FRAME, completed.outcome)
        assertEquals(PlaybackPlanRefreshOutcome.SUCCESS, completed.refreshOutcome)
        assertEquals(80L, completed.gestureToSettledMillis)
        assertEquals(250L, completed.settledToPlanMillis)
        assertEquals(300L, completed.refreshMillis)
        assertEquals(340L, completed.planToBindMillis)
        assertEquals(500L, completed.bindToReadyMillis)
        assertEquals(50L, completed.readyToFirstFrameMillis)
        assertEquals(1_140L, completed.settledToTerminalMillis)
        assertEquals(1_220L, completed.gestureToTerminalMillis)
    }

    @Test
    fun firstSettledPageWithoutGestureKeepsGestureDurationsUnknown() {
        var now = 1_000L
        val metrics = PlaybackTransitionMetrics(nowMillis = { now })
        val key = VideoKey(chatId = 1L, messageId = 2L)

        metrics.onEvent(PlaybackTransitionEvent.PageSettled(key))
        now = 1_010L
        metrics.onEvent(PlaybackTransitionEvent.PlanStarted(key))
        now = 1_020L
        metrics.onBindStarted(key)
        now = 1_200L
        metrics.onReady(key)
        now = 1_240L
        val completed = metrics.onFirstFrame(key)

        requireNotNull(completed)
        assertNull(completed.gestureToSettledMillis)
        assertNull(completed.gestureToTerminalMillis)
        assertEquals(240L, completed.settledToTerminalMillis)
    }

    @Test
    fun newerGestureCompletesAnInflightTransitionAsSuperseded() {
        var now = 0L
        val metrics = PlaybackTransitionMetrics(nowMillis = { now })
        val key = VideoKey(chatId = 3L, messageId = 4L)

        metrics.onEvent(PlaybackTransitionEvent.PageUnstable)
        now = 50L
        metrics.onEvent(PlaybackTransitionEvent.PageSettled(key))
        now = 300L
        metrics.onEvent(PlaybackTransitionEvent.PlanStarted(key))
        now = 500L
        metrics.onBindStarted(key)
        now = 600L
        val superseded = metrics.onEvent(PlaybackTransitionEvent.PageUnstable)

        requireNotNull(superseded)
        assertEquals(PlaybackTransitionOutcome.SUPERSEDED, superseded.outcome)
        assertEquals(600L, superseded.gestureToTerminalMillis)
        assertEquals(550L, superseded.settledToTerminalMillis)
    }

    @Test
    fun callbacksForAnotherVideoCannotCompleteTheActiveTransition() {
        var now = 0L
        val metrics = PlaybackTransitionMetrics(nowMillis = { now })
        val current = VideoKey(chatId = 5L, messageId = 6L)
        val stale = VideoKey(chatId = 5L, messageId = 7L)

        metrics.onEvent(PlaybackTransitionEvent.PageSettled(current))
        now = 10L
        metrics.onBindStarted(current)
        now = 20L
        assertNull(metrics.onReady(stale))
        now = 30L
        assertNull(metrics.onFirstFrame(stale))
        now = 40L
        val failed = metrics.onFailure(current)

        requireNotNull(failed)
        assertEquals(PlaybackTransitionOutcome.FAILED, failed.outcome)
        assertEquals(30L, failed.bindToTerminalMillis)
    }

    @Test
    fun repeatedReadyCallbackKeepsTheEarliestObservedBoundary() {
        var now = 0L
        val metrics = PlaybackTransitionMetrics(nowMillis = { now })
        val key = VideoKey(chatId = 8L, messageId = 9L)

        metrics.onEvent(PlaybackTransitionEvent.PageSettled(key))
        now = 10L
        metrics.onBindStarted(key)
        now = 110L
        metrics.onReady(key)
        now = 150L
        metrics.onReady(key)
        now = 180L
        val completed = metrics.onFirstFrame(key)

        requireNotNull(completed)
        assertEquals(100L, completed.bindToReadyMillis)
        assertEquals(70L, completed.readyToFirstFrameMillis)
    }

    @Test
    fun firstFrameUsesItsCallbackEntryTimeInsteadOfLaterSynchronousWork() {
        var now = 0L
        val metrics = PlaybackTransitionMetrics(nowMillis = { now })
        val key = VideoKey(chatId = 8L, messageId = 10L)

        metrics.onEvent(PlaybackTransitionEvent.PageSettled(key))
        now = 10L
        metrics.onBindStarted(key)
        val firstFrameObservedAtMillis = 210L
        now = 260L
        metrics.onReady(key, observedAtMillis = firstFrameObservedAtMillis)
        val completed = metrics.onFirstFrame(
            key = key,
            observedAtMillis = firstFrameObservedAtMillis,
        )

        requireNotNull(completed)
        assertEquals(200L, completed.bindToReadyMillis)
        assertEquals(0L, completed.readyToFirstFrameMillis)
        assertEquals(200L, completed.bindToTerminalMillis)
    }

    @Test
    fun repeatedUnstableCallbackKeepsTheEarliestGestureBoundary() {
        var now = 0L
        val metrics = PlaybackTransitionMetrics(nowMillis = { now })
        val key = VideoKey(chatId = 10L, messageId = 11L)

        assertNull(metrics.onEvent(PlaybackTransitionEvent.PageUnstable))
        now = 100L
        assertNull(metrics.onEvent(PlaybackTransitionEvent.PageUnstable))
        now = 200L
        metrics.onEvent(PlaybackTransitionEvent.PageSettled(key))
        now = 210L
        metrics.onBindStarted(key)
        now = 230L
        val completed = metrics.onFirstFrame(key)

        requireNotNull(completed)
        assertEquals(200L, completed.gestureToSettledMillis)
        assertEquals(230L, completed.gestureToTerminalMillis)
    }

    @Test
    fun promotedPlanReportsOnlySanitizedPromotionAgeAndPreparedRefreshOutcome() {
        var now = 1_000L
        val metrics = PlaybackTransitionMetrics(nowMillis = { now })
        val key = VideoKey(chatId = 12L, messageId = 13L)

        metrics.onEvent(PlaybackTransitionEvent.PageSettled(key))
        metrics.onEvent(
            PlaybackTransitionEvent.PlanStarted(
                key = key,
                promoted = true,
                planAgeMillis = 42L,
                preparedRefreshOutcome = PlaybackPlanRefreshOutcome.SUCCESS,
            ),
        )
        now = 1_020L
        metrics.onBindStarted(key)
        now = 1_040L
        val completed = metrics.onFirstFrame(key)

        requireNotNull(completed)
        assertEquals(true, completed.promoted)
        assertEquals(42L, completed.planAgeMillis)
        assertEquals(PlaybackPlanRefreshOutcome.SUCCESS, completed.refreshOutcome)
    }

    @Test
    fun transparentRecoveryRemainsPartOfTheSameFirstFrameTransition() {
        var now = 1_000L
        val metrics = PlaybackTransitionMetrics(nowMillis = { now })
        val key = VideoKey(chatId = 21L, messageId = 22L)

        metrics.onEvent(PlaybackTransitionEvent.PageSettled(key))
        metrics.onEvent(PlaybackTransitionEvent.PlanStarted(key))
        metrics.onBindStarted(key)
        now = 1_100L
        metrics.onEvent(PlaybackTransitionEvent.TransparentRecoveryStarted(key))
        now = 1_200L
        metrics.onEvent(
            PlaybackTransitionEvent.TransparentRecoveryFinished(
                key,
                TransparentRecoveryOutcome.REBOUND,
            ),
        )
        metrics.onBindStarted(key)
        now = 1_300L
        val completed = metrics.onFirstFrame(key)

        requireNotNull(completed)
        assertEquals(PlaybackTransitionOutcome.FIRST_FRAME, completed.outcome)
        assertEquals(1, completed.transparentRecoveryAttemptCount)
        assertEquals(TransparentRecoveryOutcome.REBOUND, completed.transparentRecoveryOutcome)
        assertEquals(100L, completed.bindToTerminalMillis)
    }
}
