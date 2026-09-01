package com.qixuan.channelvideoflow.player

import com.qixuan.channelvideoflow.domain.media.AdaptivePreloadDecision
import com.qixuan.channelvideoflow.domain.media.AdaptivePreloadState

internal enum class StartupPreloadCandidate {
    BASELINE,
    TAIL_64,
    TAIL_128,
    HEAD_512_WIFI;

    companion object {
        fun fromBuildValue(value: String): StartupPreloadCandidate =
            entries.singleOrNull { it.name == value }
                ?: error("Unsupported startup range candidate: $value")
    }
}

internal data class StartupPreloadSegment(
    val offset: Long,
    val length: Long,
)

internal data class StartupPreloadPlan(
    val candidate: StartupPreloadCandidate,
    val head: StartupPreloadSegment?,
    val tail: StartupPreloadSegment?,
    val totalBytes: Long,
    val extraBytes: Long,
) {
    init {
        require(totalBytes >= 0L)
        require(extraBytes >= 0L)
        require(totalBytes == listOfNotNull(head, tail).sumOf(StartupPreloadSegment::length))
        require(totalBytes <= StartupPreloadPlanner.byteLimit(candidate))
    }
}

internal object StartupPreloadPlanner {
    fun plan(
        candidate: StartupPreloadCandidate,
        decision: AdaptivePreloadDecision,
        fileSize: Long?,
    ): StartupPreloadPlan {
        if (decision.state == AdaptivePreloadState.OFF || decision.maxPreloadBytes <= 0L) {
            return emptyPlan(candidate)
        }

        val knownSize = fileSize?.takeIf { it > 0L }
        val requestedHeadBytes = when {
            candidate == StartupPreloadCandidate.HEAD_512_WIFI && decision.isUnmeteredWifi ->
                HEAD_512_BYTES
            else -> BASELINE_HEAD_BYTES
        }
        val headLength = knownSize?.coerceAtMost(requestedHeadBytes) ?: requestedHeadBytes
        val head = StartupPreloadSegment(offset = 0L, length = headLength)

        val requestedTailBytes = when (candidate) {
            StartupPreloadCandidate.TAIL_64 -> TAIL_64_BYTES
            StartupPreloadCandidate.TAIL_128 -> TAIL_128_BYTES
            StartupPreloadCandidate.BASELINE,
            StartupPreloadCandidate.HEAD_512_WIFI,
            -> 0L
        }
        val tail = if (knownSize != null && requestedTailBytes > 0L) {
            val tailOffset = (knownSize - requestedTailBytes).coerceAtLeast(headLength)
            val tailLength = (knownSize - tailOffset).coerceAtLeast(0L)
            tailLength.takeIf { it > 0L }?.let {
                StartupPreloadSegment(offset = tailOffset, length = it)
            }
        } else {
            null
        }
        val totalBytes = head.length + (tail?.length ?: 0L)
        return StartupPreloadPlan(
            candidate = candidate,
            head = head,
            tail = tail,
            totalBytes = totalBytes,
            extraBytes = (totalBytes - headLength.coerceAtMost(BASELINE_HEAD_BYTES)).coerceAtLeast(0L),
        )
    }

    private fun emptyPlan(candidate: StartupPreloadCandidate) = StartupPreloadPlan(
        candidate = candidate,
        head = null,
        tail = null,
        totalBytes = 0L,
        extraBytes = 0L,
    )

    const val BASELINE_HEAD_BYTES = 256L * 1024L
    const val TAIL_64_BYTES = 64L * 1024L
    const val TAIL_128_BYTES = 128L * 1024L
    const val HEAD_512_BYTES = 512L * 1024L

    fun byteLimit(candidate: StartupPreloadCandidate): Long =
        when (candidate) {
            StartupPreloadCandidate.BASELINE -> BASELINE_HEAD_BYTES
            StartupPreloadCandidate.TAIL_64 -> BASELINE_HEAD_BYTES + TAIL_64_BYTES
            StartupPreloadCandidate.TAIL_128 -> BASELINE_HEAD_BYTES + TAIL_128_BYTES
            StartupPreloadCandidate.HEAD_512_WIFI -> HEAD_512_BYTES
        }
}
