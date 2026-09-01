package com.qixuan.channelvideoflow.domain.cache

import com.qixuan.channelvideoflow.model.video.VideoQualityPreference
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.Flow

object MediaCacheLimits {
    const val MEBIBYTE: Long = 1024L * 1024L
    const val GIBIBYTE: Long = 1024L * MEBIBYTE
    const val DEFAULT_BYTES: Long = 500L * MEBIBYTE

    val allowedBytes: List<Long> = listOf(
        200L * MEBIBYTE,
        500L * MEBIBYTE,
        1L * GIBIBYTE,
        2L * GIBIBYTE,
        5L * GIBIBYTE,
        10L * GIBIBYTE,
        15L * GIBIBYTE,
        20L * GIBIBYTE,
    )

    fun requireAllowed(bytes: Long): Long =
        bytes.also { require(it in allowedBytes) { "unsupported media cache limit" } }
}

data class MediaCacheState(
    val usedBytes: Long = 0L,
    val limitBytes: Long = MediaCacheLimits.DEFAULT_BYTES,
    val mobileDataPreloadEnabled: Boolean = false,
    val videoQualityPreference: VideoQualityPreference = VideoQualityPreference.AUTO,
    val isExactUsage: Boolean = false,
    val isRefreshing: Boolean = false,
    val operation: MediaCacheOperation = MediaCacheOperation.Idle,
)

sealed interface MediaCacheOperation {
    data object Idle : MediaCacheOperation
    data class Trimmed(val releasedBytes: Long) : MediaCacheOperation
    data class Cleared(val releasedBytes: Long) : MediaCacheOperation
    data class Partial(
        val releasedBytes: Long,
        val remainingBytes: Long,
    ) : MediaCacheOperation

    data object Failed : MediaCacheOperation
}

interface MediaCacheController {
    val state: StateFlow<MediaCacheState>

    fun start()

    suspend fun refresh()

    suspend fun setLimitBytes(bytes: Long)

    suspend fun setMobileDataPreloadEnabled(enabled: Boolean)

    suspend fun setVideoQualityPreference(preference: VideoQualityPreference)

    suspend fun trimToLimit()

    suspend fun clearMediaCache()
}

data class MediaCachePreferencesState(
    val limitBytes: Long = MediaCacheLimits.DEFAULT_BYTES,
    val mobileDataPreloadEnabled: Boolean = false,
    val videoQualityPreference: VideoQualityPreference = VideoQualityPreference.AUTO,
)

interface MediaCachePreferences {
    val preferences: Flow<MediaCachePreferencesState>

    suspend fun setLimitBytes(bytes: Long)

    suspend fun setMobileDataPreloadEnabled(enabled: Boolean)

    suspend fun setVideoQualityPreference(preference: VideoQualityPreference)
}

data class MediaCacheEntry(
    val fileId: Int,
    val cachedBytes: Long,
    val lastAccessedAtMillis: Long,
)

data class MediaCacheEvictionPlan(
    val fileIds: List<Int>,
    val expectedRemainingBytes: Long,
    val canReachTarget: Boolean,
)

/**
 * Pure LRU selection. Protection is supplied at decision time and is never persisted.
 */
object MediaCacheEvictionPlanner {
    fun plan(
        currentBytes: Long,
        targetBytes: Long,
        entries: List<MediaCacheEntry>,
        protectedFileIds: Set<Int>,
    ): MediaCacheEvictionPlan {
        require(currentBytes >= 0L) { "currentBytes must be non-negative" }
        require(targetBytes >= 0L) { "targetBytes must be non-negative" }
        if (currentBytes <= targetBytes) {
            return MediaCacheEvictionPlan(emptyList(), currentBytes, canReachTarget = true)
        }

        var remaining = currentBytes
        val selected = mutableListOf<Int>()
        entries
            .asSequence()
            .filter { entry -> entry.fileId !in protectedFileIds && entry.cachedBytes > 0L }
            .distinctBy(MediaCacheEntry::fileId)
            .sortedWith(
                compareBy<MediaCacheEntry>(MediaCacheEntry::lastAccessedAtMillis)
                    .thenBy(MediaCacheEntry::fileId),
            )
            .forEach { entry ->
                if (remaining <= targetBytes) return@forEach
                selected += entry.fileId
                remaining = (remaining - entry.cachedBytes).coerceAtLeast(0L)
            }

        return MediaCacheEvictionPlan(
            fileIds = selected,
            expectedRemainingBytes = remaining,
            canReachTarget = remaining <= targetBytes,
        )
    }
}
