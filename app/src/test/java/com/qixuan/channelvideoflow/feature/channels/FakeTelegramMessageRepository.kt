package com.qixuan.channelvideoflow.feature.channels

import com.qixuan.channelvideoflow.domain.message.TelegramMessageRepository
import com.qixuan.channelvideoflow.domain.message.VideoReferenceFailure
import com.qixuan.channelvideoflow.domain.message.VideoReferenceResolution
import com.qixuan.channelvideoflow.model.video.ChannelVideoScanProgress
import com.qixuan.channelvideoflow.model.video.IndexedVideo
import com.qixuan.channelvideoflow.model.video.OriginalMessageLinkResult
import com.qixuan.channelvideoflow.model.video.TagSummary
import com.qixuan.channelvideoflow.model.video.VideoFilter
import com.qixuan.channelvideoflow.model.video.VideoKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

internal class FakeTelegramMessageRepository(
    initialProgress: List<ChannelVideoScanProgress> = emptyList(),
) : TelegramMessageRepository {
    private val mutableProgress = MutableStateFlow(initialProgress)
    override val scanProgress: Flow<List<ChannelVideoScanProgress>> = mutableProgress

    val foregroundChanges = mutableListOf<Boolean>()
    var refreshSelectionCalls = 0
    var pauseCalls = 0
    var resumeCalls = 0

    override fun observeVideos(filter: VideoFilter): Flow<List<IndexedVideo>> = flowOf(emptyList())

    override fun observeTags(channelIds: Set<Long>): Flow<List<TagSummary>> = flowOf(emptyList())

    override suspend fun refreshVideo(videoKey: VideoKey): VideoReferenceResolution =
        VideoReferenceResolution.Unavailable(VideoReferenceFailure.Unknown)

    override suspend fun getOriginalMessageLink(videoKey: VideoKey): OriginalMessageLinkResult =
        OriginalMessageLinkResult.Unavailable

    override suspend fun setForeground(isForeground: Boolean) {
        foregroundChanges += isForeground
    }

    override suspend fun refreshSelection() {
        refreshSelectionCalls += 1
    }

    override suspend fun pauseScanning() {
        pauseCalls += 1
    }

    override suspend fun resumeScanning() {
        resumeCalls += 1
    }

    fun emitProgress(progress: List<ChannelVideoScanProgress>) {
        mutableProgress.value = progress
    }
}
