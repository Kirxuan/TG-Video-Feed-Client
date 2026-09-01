package com.qixuan.channelvideoflow.player

import com.qixuan.channelvideoflow.model.video.VideoFeedOrder
import com.qixuan.channelvideoflow.model.video.VideoKey

internal enum class PlaybackTransitionOutcome {
    FIRST_FRAME,
    FAILED,
    UNSUPPORTED,
    SUPERSEDED,
    UNCHANGED,
    RELEASED,
}

internal data class PlaybackTransitionSnapshot(
    val key: VideoKey?,
    val outcome: PlaybackTransitionOutcome,
    val order: VideoFeedOrder?,
    val direction: PlaybackTransitionDirection?,
    val randomRoundBoundary: Boolean?,
    val refreshOutcome: PlaybackPlanRefreshOutcome?,
    val transparentRecoveryAttemptCount: Int,
    val transparentRecoveryOutcome: TransparentRecoveryOutcome?,
    val promoted: Boolean?,
    val planAgeMillis: Long?,
    val gestureToReleaseMillis: Long?,
    val gestureToTargetKnownMillis: Long?,
    val gestureToSettledMillis: Long?,
    val releaseToSettledMillis: Long?,
    val targetKnownToSettledMillis: Long?,
    val targetKnownToPlanPreparedMillis: Long?,
    val planPreparedToSettledMillis: Long?,
    val settledToPlanMillis: Long?,
    val refreshMillis: Long?,
    val planToBindMillis: Long?,
    val bindToPrepareMillis: Long?,
    val prepareToReadyMillis: Long?,
    val bindToFirstByteMillis: Long?,
    val bindToReadyMillis: Long?,
    val readyToFirstFrameMillis: Long?,
    val firstFrameBufferedDurationMillis: Long?,
    val bindToTerminalMillis: Long?,
    val settledToTerminalMillis: Long?,
    val releaseToTerminalMillis: Long?,
    val targetKnownToTerminalMillis: Long?,
    val gestureToTerminalMillis: Long?,
)

/**
 * Measures one pager transition with a monotonic clock and no retained video metadata.
 *
 * A transition begins when the pager moves or, for the initial item, when it first settles.
 * It ends at the first rendered frame, a playback failure, release, or a newer gesture.
 */
internal class PlaybackTransitionMetrics(
    private val nowMillis: () -> Long = { System.nanoTime() / NANOS_PER_MILLISECOND },
) {
    private var active: ActiveTransition? = null

    fun onEvent(event: PlaybackTransitionEvent): PlaybackTransitionSnapshot? {
        val now = nowMillis()
        return when (event) {
            is PlaybackTransitionEvent.GestureStarted -> {
                val superseded = finishActive(
                    PlaybackTransitionOutcome.SUPERSEDED,
                    event.observedAtMillis,
                )
                active = ActiveTransition(
                    gestureStartedAtMillis = event.observedAtMillis,
                    hasReliableGestureBoundaries = true,
                )
                superseded
            }

            is PlaybackTransitionEvent.GestureReleased -> {
                val target = active ?: ActiveTransition(
                    hasReliableGestureBoundaries = true,
                ).also { active = it }
                if (target.gestureReleasedAtMillis == null) {
                    target.gestureReleasedAtMillis = event.observedAtMillis
                }
                null
            }

            is PlaybackTransitionEvent.TargetKnown -> onTargetKnown(event, now)

            PlaybackTransitionEvent.TargetAbandoned -> {
                val previous = active ?: return null
                val snapshot = finishActive(PlaybackTransitionOutcome.SUPERSEDED, now)
                active = ActiveTransition(
                    gestureStartedAtMillis = previous.gestureStartedAtMillis,
                    gestureReleasedAtMillis = previous.gestureReleasedAtMillis,
                    hasReliableGestureBoundaries = previous.hasReliableGestureBoundaries,
                )
                snapshot
            }

            is PlaybackTransitionEvent.PlanPreparationStarted -> {
                active.matching(event.key)?.let { transition ->
                    if (transition.planPreparationStartedAtMillis == null) {
                        transition.planPreparationStartedAtMillis = now
                    }
                }
                null
            }

            is PlaybackTransitionEvent.PlanPrepared -> {
                active.matching(event.key)?.let { transition ->
                    if (transition.planPreparedAtMillis == null) {
                        transition.planPreparedAtMillis = now
                    }
                }
                null
            }

            PlaybackTransitionEvent.PageUnstable -> {
                val current = active
                if (current != null && current.key == null) {
                    null
                } else {
                    val superseded = finishActive(PlaybackTransitionOutcome.SUPERSEDED, now)
                    active = ActiveTransition(gestureStartedAtMillis = now)
                    superseded
                }
            }

            is PlaybackTransitionEvent.PageSettled -> {
                val current = active
                val superseded = if (current?.key != null && current.key != event.key) {
                    finishActive(PlaybackTransitionOutcome.SUPERSEDED, now)
                } else {
                    null
                }
                val target = active ?: ActiveTransition().also { active = it }
                target.key = event.key
                target.applyContext(
                    order = event.order,
                    direction = event.direction,
                    randomRoundBoundary = event.randomRoundBoundary,
                )
                target.settledAtMillis = now
                if (
                    superseded == null &&
                    target.hasReliableGestureBoundaries &&
                    target.targetKnownAtMillis == null
                ) {
                    finishActive(PlaybackTransitionOutcome.UNCHANGED, now)
                } else {
                    superseded
                }
            }

            is PlaybackTransitionEvent.PlanStarted -> {
                active.matching(event.key)?.let { transition ->
                    transition.planStartedAtMillis = now
                    transition.promoted = event.promoted
                    transition.planAgeMillis = event.planAgeMillis
                    if (event.preparedRefreshOutcome != null) {
                        transition.refreshOutcome = event.preparedRefreshOutcome
                    }
                    if (event.preparedRefreshMillis != null) {
                        transition.preparedRefreshMillis = event.preparedRefreshMillis
                    }
                }
                null
            }

            is PlaybackTransitionEvent.RefreshStarted -> {
                active.matching(event.key)?.refreshStartedAtMillis = now
                null
            }

            is PlaybackTransitionEvent.RefreshFinished -> {
                active.matching(event.key)?.let { transition ->
                    transition.refreshFinishedAtMillis = now
                    transition.refreshOutcome = event.outcome
                }
                null
            }

            is PlaybackTransitionEvent.TransparentRecoveryStarted -> {
                active.matching(event.key)?.let { transition ->
                    transition.transparentRecoveryAttemptCount += 1
                }
                null
            }

            is PlaybackTransitionEvent.TransparentRecoveryFinished -> {
                active.matching(event.key)?.let { transition ->
                    transition.transparentRecoveryOutcome = event.outcome
                }
                null
            }
        }
    }

    fun onBindStarted(key: VideoKey): PlaybackTransitionSnapshot? {
        active.matching(key)?.bindStartedAtMillis = nowMillis()
        return null
    }

    fun onReady(
        key: VideoKey,
        observedAtMillis: Long = nowMillis(),
    ): PlaybackTransitionSnapshot? {
        active.matching(key)?.let { transition ->
            if (transition.readyAtMillis == null) {
                transition.readyAtMillis = observedAtMillis
            }
        }
        return null
    }

    fun onFirstByte(
        key: VideoKey,
        observedAtMillis: Long = nowMillis(),
    ): PlaybackTransitionSnapshot? {
        active.matching(key)?.let { transition ->
            if (transition.firstByteAtMillis == null) {
                transition.firstByteAtMillis = observedAtMillis
            }
        }
        return null
    }

    fun onPrepare(
        key: VideoKey,
        observedAtMillis: Long = nowMillis(),
    ): PlaybackTransitionSnapshot? {
        active.matching(key)?.let { transition ->
            if (transition.prepareAtMillis == null) {
                transition.prepareAtMillis = observedAtMillis
            }
        }
        return null
    }

    fun onFirstFrame(
        key: VideoKey,
        observedAtMillis: Long = nowMillis(),
        bufferedDurationMillis: Long? = null,
    ): PlaybackTransitionSnapshot? =
        finishMatching(
            key = key,
            outcome = PlaybackTransitionOutcome.FIRST_FRAME,
            terminalAtMillis = observedAtMillis,
            firstFrameBufferedDurationMillis = bufferedDurationMillis,
        )

    fun onFailure(key: VideoKey): PlaybackTransitionSnapshot? =
        finishMatching(key, PlaybackTransitionOutcome.FAILED, nowMillis())

    fun onUnsupported(key: VideoKey): PlaybackTransitionSnapshot? =
        finishMatching(key, PlaybackTransitionOutcome.UNSUPPORTED, nowMillis())

    fun onRelease(): PlaybackTransitionSnapshot? =
        finishActive(PlaybackTransitionOutcome.RELEASED, nowMillis())

    private fun onTargetKnown(
        event: PlaybackTransitionEvent.TargetKnown,
        observedAtMillis: Long,
    ): PlaybackTransitionSnapshot? {
        val key = event.key
        val current = active
        if (current == null) {
            active = ActiveTransition(
                key = key,
                targetKnownAtMillis = observedAtMillis,
                order = event.order,
                direction = event.direction,
                randomRoundBoundary = event.randomRoundBoundary,
            )
            return null
        }
        if (current.key == null || current.key == key) {
            current.key = key
            current.applyContext(
                order = event.order,
                direction = event.direction,
                randomRoundBoundary = event.randomRoundBoundary,
            )
            if (current.targetKnownAtMillis == null) {
                current.targetKnownAtMillis = observedAtMillis
            }
            return null
        }
        val gestureStartedAtMillis = current.gestureStartedAtMillis
        val gestureReleasedAtMillis = current.gestureReleasedAtMillis
        val hasReliableGestureBoundaries = current.hasReliableGestureBoundaries
        val superseded = finishActive(
            PlaybackTransitionOutcome.SUPERSEDED,
            observedAtMillis,
        )
        active = ActiveTransition(
            key = key,
            gestureStartedAtMillis = gestureStartedAtMillis,
            gestureReleasedAtMillis = gestureReleasedAtMillis,
            targetKnownAtMillis = observedAtMillis,
            hasReliableGestureBoundaries = hasReliableGestureBoundaries,
            order = event.order,
            direction = event.direction,
            randomRoundBoundary = event.randomRoundBoundary,
        )
        return superseded
    }

    private fun finishMatching(
        key: VideoKey,
        outcome: PlaybackTransitionOutcome,
        terminalAtMillis: Long,
        firstFrameBufferedDurationMillis: Long? = null,
    ): PlaybackTransitionSnapshot? {
        if (active.matching(key) == null) return null
        return finishActive(outcome, terminalAtMillis, firstFrameBufferedDurationMillis)
    }

    private fun finishActive(
        outcome: PlaybackTransitionOutcome,
        terminalAtMillis: Long,
        firstFrameBufferedDurationMillis: Long? = null,
    ): PlaybackTransitionSnapshot? {
        val transition = active ?: return null
        active = null
        return transition.snapshot(
            outcome = outcome,
            terminalAtMillis = terminalAtMillis,
            firstFrameBufferedDurationMillis = firstFrameBufferedDurationMillis,
        )
    }

    private fun ActiveTransition?.matching(key: VideoKey): ActiveTransition? =
        this?.takeIf { transition -> transition.key == key }

    private data class ActiveTransition(
        var key: VideoKey? = null,
        var gestureStartedAtMillis: Long? = null,
        var gestureReleasedAtMillis: Long? = null,
        var targetKnownAtMillis: Long? = null,
        var planPreparationStartedAtMillis: Long? = null,
        var planPreparedAtMillis: Long? = null,
        var settledAtMillis: Long? = null,
        var planStartedAtMillis: Long? = null,
        var refreshStartedAtMillis: Long? = null,
        var refreshFinishedAtMillis: Long? = null,
        var bindStartedAtMillis: Long? = null,
        var prepareAtMillis: Long? = null,
        var readyAtMillis: Long? = null,
        var firstByteAtMillis: Long? = null,
        var order: VideoFeedOrder? = null,
        var direction: PlaybackTransitionDirection? = null,
        var randomRoundBoundary: Boolean? = null,
        var refreshOutcome: PlaybackPlanRefreshOutcome? = null,
        var transparentRecoveryAttemptCount: Int = 0,
        var transparentRecoveryOutcome: TransparentRecoveryOutcome? = null,
        var preparedRefreshMillis: Long? = null,
        var promoted: Boolean? = null,
        var planAgeMillis: Long? = null,
        var hasReliableGestureBoundaries: Boolean = false,
    ) {
        fun applyContext(
            order: VideoFeedOrder?,
            direction: PlaybackTransitionDirection?,
            randomRoundBoundary: Boolean?,
        ) {
            if (order != null) this.order = order
            if (direction != null) this.direction = direction
            if (randomRoundBoundary != null) this.randomRoundBoundary = randomRoundBoundary
        }

        fun snapshot(
            outcome: PlaybackTransitionOutcome,
            terminalAtMillis: Long,
            firstFrameBufferedDurationMillis: Long?,
        ): PlaybackTransitionSnapshot = PlaybackTransitionSnapshot(
            key = key,
            outcome = outcome,
            order = order,
            direction = direction,
            randomRoundBoundary = randomRoundBoundary,
            refreshOutcome = refreshOutcome,
            transparentRecoveryAttemptCount = transparentRecoveryAttemptCount,
            transparentRecoveryOutcome = transparentRecoveryOutcome,
            promoted = promoted,
            planAgeMillis = planAgeMillis,
            gestureToReleaseMillis = duration(
                gestureStartedAtMillis,
                gestureReleasedAtMillis,
            ),
            gestureToTargetKnownMillis = duration(
                gestureStartedAtMillis,
                targetKnownAtMillis,
            ),
            gestureToSettledMillis = duration(gestureStartedAtMillis, settledAtMillis),
            releaseToSettledMillis = duration(gestureReleasedAtMillis, settledAtMillis),
            targetKnownToSettledMillis = duration(targetKnownAtMillis, settledAtMillis),
            targetKnownToPlanPreparedMillis = duration(
                targetKnownAtMillis,
                planPreparedAtMillis,
            ),
            planPreparedToSettledMillis = duration(planPreparedAtMillis, settledAtMillis),
            settledToPlanMillis = duration(settledAtMillis, planStartedAtMillis),
            refreshMillis = duration(refreshStartedAtMillis, refreshFinishedAtMillis)
                ?: preparedRefreshMillis,
            planToBindMillis = duration(planStartedAtMillis, bindStartedAtMillis),
            bindToPrepareMillis = duration(bindStartedAtMillis, prepareAtMillis),
            prepareToReadyMillis = duration(prepareAtMillis, readyAtMillis),
            bindToFirstByteMillis = duration(bindStartedAtMillis, firstByteAtMillis),
            bindToReadyMillis = duration(bindStartedAtMillis, readyAtMillis),
            readyToFirstFrameMillis = if (outcome == PlaybackTransitionOutcome.FIRST_FRAME) {
                duration(readyAtMillis, terminalAtMillis)
            } else {
                null
            },
            firstFrameBufferedDurationMillis = if (
                outcome == PlaybackTransitionOutcome.FIRST_FRAME
            ) {
                firstFrameBufferedDurationMillis
            } else {
                null
            },
            bindToTerminalMillis = duration(bindStartedAtMillis, terminalAtMillis),
            settledToTerminalMillis = duration(settledAtMillis, terminalAtMillis),
            releaseToTerminalMillis = duration(gestureReleasedAtMillis, terminalAtMillis),
            targetKnownToTerminalMillis = duration(targetKnownAtMillis, terminalAtMillis),
            gestureToTerminalMillis = duration(gestureStartedAtMillis, terminalAtMillis),
        )

        private fun duration(startMillis: Long?, endMillis: Long?): Long? =
            if (startMillis == null || endMillis == null) {
                null
            } else {
                (endMillis - startMillis).coerceAtLeast(0L)
            }
    }

    private companion object {
        const val NANOS_PER_MILLISECOND = 1_000_000L
    }
}
