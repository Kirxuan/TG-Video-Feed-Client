package com.qixuan.channelvideoflow.domain.video

import com.qixuan.channelvideoflow.model.video.IndexedVideo
import com.qixuan.channelvideoflow.model.video.TagFilterMode
import com.qixuan.channelvideoflow.model.video.VideoFilter
import com.qixuan.channelvideoflow.model.video.VideoKey
import com.qixuan.channelvideoflow.model.video.VideoTag
import org.junit.Assert.assertEquals
import org.junit.Test

class VideoFilterMatcherTest {
    private val videos = listOf(
        video(1, 11, "学习"),
        video(1, 12, "娱乐"),
        video(2, 21, "学习", "单片机"),
        video(2, 22, "其他"),
        video(3, 31, "学习"),
    )

    @Test
    fun tagOrWithinChannelOr() {
        assertMatches(setOf(1, 2), setOf("学习", "单片机"), TagFilterMode.OR, 11, 21)
    }

    @Test
    fun tagAndWithinChannelOr() {
        assertMatches(setOf(1, 2), setOf("学习", "单片机"), TagFilterMode.AND, 21)
    }

    @Test
    fun emptyTagsApplyNoTagRestriction() {
        assertMatches(setOf(1, 2), emptySet(), TagFilterMode.OR, 11, 12, 21, 22)
    }

    @Test
    fun aSingleChannelRestrictsBeforeTagMatching() {
        assertMatches(setOf(2), setOf("学习"), TagFilterMode.OR, 21)
    }

    @Test
    fun emptyChannelsNeverMeansAllChannels() {
        assertMatches(emptySet(), setOf("学习"), TagFilterMode.OR)
    }

    @Test
    fun normalizedEnglishVariantsShareOneFilterKey() {
        val english = listOf(video(1, 41, "kotlin"), video(2, 42, "kotlin"))
        val result = VideoFilterMatcher.filter(
            english,
            VideoFilter(setOf(1, 2), setOf("kotlin"), TagFilterMode.OR),
        )
        assertEquals(listOf(41L, 42L), result.map { it.key.messageId })
    }

    private fun assertMatches(
        channels: Set<Int>,
        tags: Set<String>,
        mode: TagFilterMode,
        vararg messageIds: Long,
    ) {
        val result = VideoFilterMatcher.filter(
            videos,
            VideoFilter(channels.map(Int::toLong).toSet(), tags, mode),
        )
        assertEquals(messageIds.toList(), result.map { it.key.messageId })
    }

    private fun video(chatId: Long, messageId: Long, vararg tags: String) = IndexedVideo(
        key = VideoKey(chatId, messageId),
        fileId = messageId.toInt(),
        remoteUniqueId = "remote-$chatId-$messageId",
        caption = "",
        supportsStreaming = true,
        fileSize = 1,
        durationSeconds = 1,
        width = 1,
        height = 1,
        publishTime = messageId,
        editTime = null,
        canBeSaved = true,
        tags = tags.map { VideoTag(it, "#$it") },
    )
}
