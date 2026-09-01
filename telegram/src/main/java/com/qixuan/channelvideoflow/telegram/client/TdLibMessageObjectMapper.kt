package com.qixuan.channelvideoflow.telegram.client

import org.drinkless.tdlib.TdApi

internal object TdLibMessageObjectMapper {
    fun mapMessage(message: TdApi.Message): TelegramClientMessage = TelegramClientMessage(
        chatId = message.chatId,
        messageId = message.id,
        publishTime = message.date.toLong(),
        editTime = message.editDate.toLong().takeIf { it > 0 },
        canBeSaved = message.canBeSaved,
        video = mapVideoContent(message.content),
    )

    fun mapVideoContent(content: TdApi.MessageContent): TelegramClientVideoContent? {
        val messageVideo = content as? TdApi.MessageVideo ?: return null
        if (messageVideo.isSecret) return null
        val video = messageVideo.video ?: return null
        val file = video.video ?: return null
        val caption = messageVideo.caption
        return TelegramClientVideoContent(
            fileId = file.id,
            remoteUniqueId = file.remote?.uniqueId.orEmpty(),
            caption = caption?.text.orEmpty(),
            hashtagEntityRanges = caption?.entities
                .orEmpty()
                .asSequence()
                .filter { entity -> entity?.type is TdApi.TextEntityTypeHashtag }
                .mapNotNull { entity ->
                    entity?.let { TelegramClientUtf16Range(it.offset, it.length) }
                }
                .toList(),
            durationSeconds = video.duration,
            width = video.width,
            height = video.height,
            fileSize = file.size.takeIf { it > 0 },
            supportsStreaming = video.supportsStreaming,
            alternativeVariants = messageVideo.alternativeVideos
                .orEmpty()
                .mapNotNull { alternative ->
                    val alternativeFile = alternative?.video ?: return@mapNotNull null
                    TelegramClientVideoVariant(
                        alternativeId = alternative.id,
                        fileId = alternativeFile.id,
                        remoteUniqueId = alternativeFile.remote?.uniqueId.orEmpty(),
                        fileSize = alternativeFile.size.takeIf { it > 0 },
                        width = alternative.width,
                        height = alternative.height,
                        codec = alternative.codec.orEmpty(),
                        hlsManifestFile = alternative.hlsFile
                            ?.takeIf { hlsFile -> hlsFile.id > 0 }
                            ?.toClientMediaFile(),
                    )
                }
                .filter { alternative ->
                    alternative.fileId != file.id || alternative.hlsManifestFile != null
                }
                .distinctBy { alternative ->
                    Triple(
                        alternative.alternativeId,
                        alternative.fileId,
                        alternative.hlsManifestFile?.fileId,
                    )
                },
        )
    }

    private fun TdApi.File.toClientMediaFile(): TelegramClientMediaFile = TelegramClientMediaFile(
        fileId = id,
        remoteUniqueId = remote?.uniqueId.orEmpty(),
        fileSize = size.takeIf { it > 0 },
    )
}
