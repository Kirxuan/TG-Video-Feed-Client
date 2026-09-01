package com.qixuan.channelvideoflow.domain.message

import com.qixuan.channelvideoflow.model.video.ChannelVideoScanProgress
import com.qixuan.channelvideoflow.model.video.IndexedVideo
import com.qixuan.channelvideoflow.model.video.OriginalMessageLinkResult
import com.qixuan.channelvideoflow.model.video.TagSummary
import com.qixuan.channelvideoflow.model.video.VideoFilter
import com.qixuan.channelvideoflow.model.video.VideoKey
import kotlinx.coroutines.flow.Flow

interface TelegramMessageRepository {
    val scanProgress: Flow<List<ChannelVideoScanProgress>>

    fun observeVideos(filter: VideoFilter): Flow<List<IndexedVideo>>

    fun observeTags(channelIds: Set<Long>): Flow<List<TagSummary>>

    /**
     * Re-resolves the current Telegram message into an app-owned result that
     * distinguishes a fresh reference, a terminal message change, and a
     * transient request failure.
     */
    suspend fun refreshVideo(videoKey: VideoKey): VideoReferenceResolution

    suspend fun getOriginalMessageLink(videoKey: VideoKey): OriginalMessageLinkResult

    suspend fun setForeground(isForeground: Boolean)

    suspend fun refreshSelection()

    suspend fun pauseScanning()

    suspend fun resumeScanning()
}

/** App-owned result of resolving a Telegram message into its current video reference. */
sealed interface VideoReferenceResolution {
    data class Resolved(val video: IndexedVideo) : VideoReferenceResolution

    /** The message no longer exists and its Room row has been marked deleted. */
    data object MessageMissing : VideoReferenceResolution

    /** The message exists but is no longer an ordinary messageVideo. */
    data object UnsupportedMessage : VideoReferenceResolution

    /** A transient/request failure for which the indexed reference remains a safe fallback. */
    data class Unavailable(
        val failure: VideoReferenceFailure,
    ) : VideoReferenceResolution
}

sealed interface VideoReferenceFailure {
    data object Network : VideoReferenceFailure
    data class FloodWait(val retryAfterSeconds: Int) : VideoReferenceFailure
    data object Timeout : VideoReferenceFailure
    data object SessionUnavailable : VideoReferenceFailure
    data object AccessLost : VideoReferenceFailure
    data class RequestRejected(val code: Int) : VideoReferenceFailure
    data object Unknown : VideoReferenceFailure
}
