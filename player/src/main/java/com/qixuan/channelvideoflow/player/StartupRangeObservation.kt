package com.qixuan.channelvideoflow.player

import com.qixuan.channelvideoflow.domain.media.TelegramFileSnapshot

internal enum class StartupDataSpecOffsetCategory {
    HEAD,
    TAIL,
    MIDDLE,
    UNKNOWN,
}

internal data class StartupRangeObservationSnapshot(
    val firstMissCategory: StartupDataSpecOffsetCategory?,
    val coveredBeforeCurrentBytes: Long,
    val dataSpecOpenCount: Int,
    val extractorRangeSwitchCount: Int,
)

/**
 * Debug-only input is collected by the shared per-bind range session. The state contains only
 * byte ranges and categories; it never retains a path, remote id, owner token, or media bytes.
 */
internal class StartupRangeObservation(
    private val headWindowBytes: Long = HEAD_WINDOW_BYTES,
    private val tailWindowBytes: Long = TAIL_WINDOW_BYTES,
    private val coverageObservationLimitBytes: Long = COVERAGE_OBSERVATION_LIMIT_BYTES,
) {
    private val lock = Any()
    private var firstMissCategory: StartupDataSpecOffsetCategory? = null
    private var coveredBeforeCurrentBytes = 0L
    private var dataSpecOpenCount = 0
    private var extractorRangeSwitchCount = 0

    fun onDataSpecOpened(
        position: Long,
        requestedLength: Long,
        snapshot: TelegramFileSnapshot?,
    ) {
        if (position < 0L || requestedLength <= 0L) return
        synchronized(lock) {
            if (dataSpecOpenCount == 0) {
                coveredBeforeCurrentBytes = snapshot.contiguousBytesFromHead(
                    coverageObservationLimitBytes,
                )
            } else {
                extractorRangeSwitchCount += 1
            }
            dataSpecOpenCount += 1
            if (firstMissCategory == null && snapshot?.covers(position, requestedLength) != true) {
                firstMissCategory = classify(position, snapshot?.size)
            }
        }
    }

    fun snapshot(): StartupRangeObservationSnapshot = synchronized(lock) {
        StartupRangeObservationSnapshot(
            firstMissCategory = firstMissCategory,
            coveredBeforeCurrentBytes = coveredBeforeCurrentBytes,
            dataSpecOpenCount = dataSpecOpenCount,
            extractorRangeSwitchCount = extractorRangeSwitchCount,
        )
    }

    private fun classify(position: Long, fileSize: Long?): StartupDataSpecOffsetCategory {
        if (position < headWindowBytes) return StartupDataSpecOffsetCategory.HEAD
        val knownSize = fileSize?.takeIf { it > 0L }
            ?: return StartupDataSpecOffsetCategory.UNKNOWN
        val tailStart = (knownSize - tailWindowBytes).coerceAtLeast(headWindowBytes)
        return if (position >= tailStart) {
            StartupDataSpecOffsetCategory.TAIL
        } else {
            StartupDataSpecOffsetCategory.MIDDLE
        }
    }

    private fun TelegramFileSnapshot?.contiguousBytesFromHead(limitBytes: Long): Long {
        val current = this ?: return 0L
        if (current.localPath == null || limitBytes <= 0L) return 0L
        val availableEnd = if (current.isDownloadingCompleted && current.size > 0L) {
            current.size
        } else if (current.downloadOffset <= 0L) {
            current.downloadOffset.saturatedAdd(current.downloadedPrefixSize)
        } else {
            0L
        }
        return availableEnd.coerceAtLeast(0L).coerceAtMost(limitBytes)
    }

    private fun Long.saturatedAdd(increment: Long): Long =
        if (this > Long.MAX_VALUE - increment) Long.MAX_VALUE else this + increment

    internal companion object {
        const val HEAD_WINDOW_BYTES = 256L * 1024L
        const val TAIL_WINDOW_BYTES = 256L * 1024L
        const val COVERAGE_OBSERVATION_LIMIT_BYTES = 512L * 1024L
    }
}
