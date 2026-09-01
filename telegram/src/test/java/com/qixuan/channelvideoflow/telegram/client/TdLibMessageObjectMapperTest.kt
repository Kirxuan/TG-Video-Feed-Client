package com.qixuan.channelvideoflow.telegram.client

import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TdLibMessageObjectMapperTest {
    @Test
    fun mapsOnlyOrdinaryMessageVideoMetadata() {
        val message = videoMessage(
            chatId = 7,
            messageId = 70,
            caption = "😀 #学习 #Kotlin",
            ranges = arrayOf(
                TdApi.TextEntity(3, 3, TdApi.TextEntityTypeHashtag()),
                TdApi.TextEntity(7, 7, TdApi.TextEntityTypeHashtag()),
            ),
        )

        val mapped = TdLibMessageObjectMapper.mapMessage(message)

        assertEquals(7, mapped.chatId)
        assertEquals(70, mapped.messageId)
        assertEquals(1_700_000_000L, mapped.publishTime)
        assertEquals(1_700_000_100L, mapped.editTime)
        assertEquals(true, mapped.canBeSaved)
        assertEquals(501, mapped.video?.fileId)
        assertEquals("unique-501", mapped.video?.remoteUniqueId)
        assertEquals(4096L, mapped.video?.fileSize)
        assertEquals(true, mapped.video?.supportsStreaming)
        assertEquals(
            listOf(
                TelegramClientVideoVariant(
                    alternativeId = 1,
                    fileId = 601,
                    remoteUniqueId = "unique-601",
                    fileSize = 2048,
                    width = 640,
                    height = 360,
                    codec = "h264",
                    hlsManifestFile = TelegramClientMediaFile(
                        fileId = 600,
                        remoteUniqueId = "unique-600",
                        fileSize = 512,
                    ),
                ),
            ),
            mapped.video?.alternativeVariants,
        )
        assertEquals(
            listOf(TelegramClientUtf16Range(3, 3), TelegramClientUtf16Range(7, 7)),
            mapped.video?.hashtagEntityRanges,
        )
    }

    @Test
    fun animationVideoNoteDocumentPaidMediaAndSecretVideoAreExcluded() {
        val unsupported = listOf<TdApi.MessageContent>(
            TdApi.MessageAnimation(),
            TdApi.MessageVideoNote(),
            TdApi.MessageDocument(),
            TdApi.MessagePaidMedia(),
            TdApi.MessageStory(),
            TdApi.MessageVideoChatStarted(),
        )

        unsupported.forEach { content ->
            assertNull(TdLibMessageObjectMapper.mapVideoContent(content))
        }

        val secret = videoMessage(1, 1, "", emptyArray()).content as TdApi.MessageVideo
        secret.isSecret = true
        assertNull(TdLibMessageObjectMapper.mapVideoContent(secret))
    }

    private fun videoMessage(
        chatId: Long,
        messageId: Long,
        caption: String,
        ranges: Array<TdApi.TextEntity>,
    ): TdApi.Message = TdApi.Message().apply {
        id = messageId
        this.chatId = chatId
        date = 1_700_000_000
        editDate = 1_700_000_100
        canBeSaved = true
        content = TdApi.MessageVideo().apply {
            video = TdApi.Video().apply {
                duration = 45
                width = 1080
                height = 1920
                supportsStreaming = true
                video = TdApi.File().apply {
                    id = 501
                    size = 4096
                    remote = TdApi.RemoteFile().apply { uniqueId = "unique-501" }
                }
            }
            this.caption = TdApi.FormattedText(caption, ranges)
            alternativeVideos = arrayOf(
                TdApi.AlternativeVideo().apply {
                    id = 1
                    width = 640
                    height = 360
                    codec = "h264"
                    hlsFile = TdApi.File().apply {
                        id = 600
                        size = 512
                        remote = TdApi.RemoteFile().apply { uniqueId = "unique-600" }
                    }
                    video = TdApi.File().apply {
                        id = 601
                        size = 2048
                        remote = TdApi.RemoteFile().apply { uniqueId = "unique-601" }
                    }
                },
            )
            isSecret = false
        }
    }
}
