package com.qixuan.channelvideoflow.model.video

data class VideoKey(
    val chatId: Long,
    val messageId: Long,
)

data class VideoTag(
    val normalizedName: String,
    val displayName: String,
)

/** An app-owned description of a Telegram file. TDLib types never cross this boundary. */
data class TelegramMediaFileReference(
    val fileId: Int,
    val remoteUniqueId: String,
    val fileSize: Long?,
)

enum class VideoDeliveryCapability {
    PROGRESSIVE,
    HLS,
}

/**
 * One server-side representation and both of its official delivery resources.
 *
 * [fileId] remains the progressive MP4 fallback. [hlsManifestFile] is ephemeral metadata obtained
 * from a fresh TDLib message and is deliberately not persisted in Room.
 */
data class VideoPlaybackVariant(
    val fileId: Int,
    val remoteUniqueId: String,
    val fileSize: Long?,
    val width: Int,
    val height: Int,
    val codec: String,
    val alternativeId: Long = 0,
    val hlsManifestFile: TelegramMediaFileReference? = null,
) {
    val capabilities: Set<VideoDeliveryCapability>
        get() = buildSet {
            add(VideoDeliveryCapability.PROGRESSIVE)
            if (hlsManifestFile != null) add(VideoDeliveryCapability.HLS)
        }
}

enum class HlsCapabilityStatus {
    AVAILABLE,
    NO_ALTERNATIVE_VIDEO,
    NO_HLS_MANIFEST,
    INVALID_DESCRIPTOR,
}

/**
 * A fixed-field observability record. It cannot carry captions, URLs, credentials, or media bytes.
 */
data class PlaybackCapabilityObservation(
    val videoKey: VideoKey,
    val directVariantCount: Int,
    val hlsVariantCount: Int,
    val status: HlsCapabilityStatus,
) {
    fun toRedactedLogLine(): String = buildString {
        append("playback_capability chatId=")
        append(videoKey.chatId)
        append(" messageId=")
        append(videoKey.messageId)
        append(" directVariants=")
        append(directVariantCount.coerceAtLeast(0))
        append(" hlsVariants=")
        append(hlsVariantCount.coerceAtLeast(0))
        append(" status=")
        append(status.name)
    }
}

data class IndexedVideo(
    val key: VideoKey,
    val fileId: Int,
    val remoteUniqueId: String,
    val caption: String,
    val supportsStreaming: Boolean,
    val fileSize: Long?,
    val durationSeconds: Int,
    val width: Int,
    val height: Int,
    val publishTime: Long,
    val editTime: Long?,
    val canBeSaved: Boolean,
    val tags: List<VideoTag>,
    val alternativeVariants: List<VideoPlaybackVariant> = emptyList(),
    val selectedAlternative: VideoPlaybackVariant? = null,
) {
    val playbackFileId: Int
        get() = selectedAlternative?.fileId ?: fileId

    val playbackFileSize: Long?
        get() = selectedAlternative?.fileSize ?: fileSize

    val playbackWidth: Int
        get() = selectedAlternative?.width ?: width

    val playbackHeight: Int
        get() = selectedAlternative?.height ?: height

    val hlsCapableVariants: List<VideoPlaybackVariant>
        get() = alternativeVariants.filter { variant -> variant.hlsManifestFile != null }
}

enum class VideoQualityPreference {
    AUTO,
    DATA_SAVER,
    HD_720,
    ORIGINAL,
}

data class TagSummary(
    val normalizedName: String,
    val displayName: String,
    val videoCount: Int,
)

enum class TagFilterMode {
    OR,
    AND,
}

data class VideoFilter(
    val channelIds: Set<Long>,
    val normalizedTags: Set<String> = emptySet(),
    val tagMode: TagFilterMode = TagFilterMode.OR,
)

/** The session-local ordering applied after the Room filter has produced feed metadata. */
enum class VideoFeedOrder {
    LATEST,
    RANDOM,
}

/** Every newly-created playback-page session starts in random order. */
val DEFAULT_VIDEO_FEED_ORDER: VideoFeedOrder = VideoFeedOrder.RANDOM

/**
 * Sanitized result of requesting the official Telegram link for a message.
 *
 * The URL is strictly for user-initiated navigation; it is never a media source.
 */
sealed interface OriginalMessageLinkResult {
    data class Available(
        val httpsUrl: String,
    ) : OriginalMessageLinkResult

    data object Unavailable : OriginalMessageLinkResult
    data object NetworkUnavailable : OriginalMessageLinkResult
    data object Unknown : OriginalMessageLinkResult
}

enum class VideoScanStatus {
    NOT_STARTED,
    SCANNING,
    PAUSED,
    COMPLETED,
    ERROR,
}

sealed interface TelegramMessageFailure {
    data object NetworkUnavailable : TelegramMessageFailure

    data class FloodWait(
        val retryAtEpochMillis: Long,
    ) : TelegramMessageFailure

    data class RequestRejected(
        val code: Int,
    ) : TelegramMessageFailure

    data object AccessLost : TelegramMessageFailure
    data object Timeout : TelegramMessageFailure
    data object Database : TelegramMessageFailure
    data object PaginationStalled : TelegramMessageFailure
    data object Unknown : TelegramMessageFailure
}

data class ChannelVideoScanProgress(
    val chatId: Long,
    val channelTitle: String,
    val status: VideoScanStatus,
    val processedVideoCandidateCount: Long,
    val videoSearchPageCount: Int,
    val indexedVideoCount: Int,
    val approximateVideoCount: Int?,
    val duplicateVideoEncounterCount: Long,
    val exceptionCount: Int,
    val nextVideoSearchCursor: Long,
    val latestSyncedMessageId: Long?,
    val isPausedByUser: Boolean,
    val failure: TelegramMessageFailure? = null,
)
