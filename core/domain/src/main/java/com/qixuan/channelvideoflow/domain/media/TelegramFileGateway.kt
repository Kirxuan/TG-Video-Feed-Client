package com.qixuan.channelvideoflow.domain.media

import kotlinx.coroutines.flow.Flow

/**
 * App-owned boundary around TDLib's private file store.
 *
 * The returned path is only ever used internally by the player. It is not a URL
 * and must not be exposed to the UI or written to a public directory.
 */
interface TelegramFileGateway {
    fun acquireRange(
        fileId: Int,
        offset: Long,
        length: Long,
        priority: TelegramFileRequestPriority,
        ownerToken: String,
        ownerKind: TelegramFileOwnerKind = TelegramFileOwnerKind.CURRENT_PLAYBACK,
        readAheadBytes: Long = length,
    ): TelegramFileRangeLease

    fun pinFile(
        fileId: Int,
        ownerToken: String,
        ownerKind: TelegramFileOwnerKind,
    ): TelegramFileProtectionLease

    fun observeFile(fileId: Int): Flow<TelegramFileSnapshot>

    fun currentSnapshot(fileId: Int): TelegramFileSnapshot?

    /**
     * Invalidates a TDLib local-path snapshot that the Media3 loading thread
     * could no longer open. Implementations must ignore the call when the
     * current snapshot points at a different path.
     */
    fun invalidateLocalSnapshot(fileId: Int, expectedLocalPath: String) = Unit

    fun protectedFileIds(): Set<Int>

    suspend fun deleteCachedFile(fileId: Int): TelegramFileDeleteResult

    fun release(ownerToken: String)

    /** Current in-memory account generation. It changes on ready/logout and is never persisted. */
    fun currentAccountGeneration(): Long = 0L

    /** Registers a file behind an opaque, short-lived handle for the internal HLS DataSource. */
    fun registerInternalResource(
        fileId: Int,
        ownerToken: String,
        kind: TelegramInternalResourceKind,
        expectedSize: Long? = null,
        referencedResources: Map<Int, TelegramInternalResourceHandle> = emptyMap(),
        timeToLiveMillis: Long = DEFAULT_INTERNAL_RESOURCE_TTL_MILLIS,
    ): TelegramInternalResourceHandle = throw UnsupportedOperationException(
        "internal resources are unavailable",
    )

    fun resolveInternalResource(
        accountGeneration: Long,
        opaqueToken: String,
    ): TelegramInternalResourceResolution? = null

    fun revokeInternalResources(ownerToken: String) = Unit

    /** Redacted active-request progress for deadline decisions; never contains paths or bytes. */
    fun currentNetworkRequest(): TelegramNetworkRequestSnapshot? = null

    companion object {
        const val DEFAULT_INTERNAL_RESOURCE_TTL_MILLIS = 5L * 60L * 1_000L
    }
}

enum class TelegramInternalResourceKind {
    HLS_MANIFEST,
    HLS_MEDIA,
}

data class TelegramInternalResourceHandle(
    val accountGeneration: Long,
    val opaqueToken: String,
    val kind: TelegramInternalResourceKind,
)

data class TelegramInternalResourceResolution(
    val fileId: Int,
    val kind: TelegramInternalResourceKind,
    val expectedSize: Long?,
    val referencedResources: Map<Int, TelegramInternalResourceHandle>,
)

data class TelegramNetworkRequestSnapshot(
    val fileId: Int,
    val downloadedBytes: Long,
    val remainingBytes: Long,
    val priority: TelegramFileRequestPriority,
    val ownerKind: TelegramFileOwnerKind,
)

enum class TelegramFileOwnerKind {
    CURRENT_PLAYBACK,
    NEXT_PRELOAD,
}

enum class TelegramFileRequestPriority(val tdLibPriority: Int) {
    CURRENT_STARTUP(32),
    CURRENT_SEEK(30),
    CURRENT_CONTINUATION(24),
    NEXT_PRELOAD(8),
    ;

    companion object {
        fun fromTdLibPriority(priority: Int): TelegramFileRequestPriority =
            entries.firstOrNull { candidate -> candidate.tdLibPriority == priority }
                ?: error("unsupported TDLib file priority")
    }
}

interface TelegramFileRangeLease {
    val fileId: Int
    val offset: Long
    val length: Long

    /**
     * Blocks only the Media3 loading thread until the requested range is readable.
     */
    fun awaitAvailable(timeoutMillis: Long): TelegramFileSnapshot

    fun updatePriority(priority: TelegramFileRequestPriority)

    fun close()
}

interface TelegramFileProtectionLease {
    val fileId: Int
    val ownerKind: TelegramFileOwnerKind

    fun close()
}

enum class TelegramFileDeleteResult {
    DELETED,
    PROTECTED,
    FAILED,
}

data class TelegramFileSnapshot(
    val fileId: Int,
    val size: Long,
    val expectedSize: Long,
    val localPath: String?,
    val canBeDownloaded: Boolean,
    val isDownloadingActive: Boolean,
    val isDownloadingCompleted: Boolean,
    val downloadOffset: Long,
    val downloadedPrefixSize: Long,
    val downloadedSize: Long,
) {
    fun covers(start: Long, length: Long): Boolean {
        if (start < 0 || length < 0) return false
        val end = start + length
        if (end < start) return false
        val availableEnd = if (isDownloadingCompleted && size > 0) {
            size
        } else {
            downloadOffset + downloadedPrefixSize
        }
        return localPath != null && start >= downloadOffset && end <= availableEnd
    }
}

class TelegramFileTimeoutException(message: String) : Exception(message)

class TelegramFileUnavailableException(message: String) : Exception(message)
