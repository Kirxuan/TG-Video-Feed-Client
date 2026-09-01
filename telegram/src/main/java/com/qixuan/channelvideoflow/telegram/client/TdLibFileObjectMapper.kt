package com.qixuan.channelvideoflow.telegram.client

import org.drinkless.tdlib.TdApi

internal object TdLibFileObjectMapper {
    fun map(file: TdApi.File): TelegramClientFileSnapshot {
        val local = file.local
        return TelegramClientFileSnapshot(
            fileId = file.id,
            size = file.size,
            expectedSize = file.expectedSize,
            localPath = local?.path?.takeIf(String::isNotEmpty),
            canBeDownloaded = local?.canBeDownloaded == true,
            isDownloadingActive = local?.isDownloadingActive == true,
            isDownloadingCompleted = local?.isDownloadingCompleted == true,
            downloadOffset = local?.downloadOffset ?: 0L,
            downloadedPrefixSize = local?.downloadedPrefixSize ?: 0L,
            downloadedSize = local?.downloadedSize ?: 0L,
        )
    }
}
