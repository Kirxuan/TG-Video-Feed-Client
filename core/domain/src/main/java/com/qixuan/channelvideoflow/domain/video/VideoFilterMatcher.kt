package com.qixuan.channelvideoflow.domain.video

import com.qixuan.channelvideoflow.model.video.IndexedVideo
import com.qixuan.channelvideoflow.model.video.TagFilterMode
import com.qixuan.channelvideoflow.model.video.VideoFilter

object VideoFilterMatcher {
    fun matches(video: IndexedVideo, filter: VideoFilter): Boolean {
        if (video.key.chatId !in filter.channelIds) return false
        if (filter.normalizedTags.isEmpty()) return true

        val videoTags = video.tags.mapTo(hashSetOf()) { it.normalizedName }
        return when (filter.tagMode) {
            TagFilterMode.OR -> filter.normalizedTags.any(videoTags::contains)
            TagFilterMode.AND -> filter.normalizedTags.all(videoTags::contains)
        }
    }

    fun filter(videos: List<IndexedVideo>, filter: VideoFilter): List<IndexedVideo> =
        videos.filter { video -> matches(video, filter) }
}
