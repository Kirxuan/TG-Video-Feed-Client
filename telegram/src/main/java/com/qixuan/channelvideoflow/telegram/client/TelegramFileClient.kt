package com.qixuan.channelvideoflow.telegram.client

import kotlinx.coroutines.flow.Flow

internal interface TelegramFileClient {
    val fileEvents: Flow<TelegramFileClientEvent>

    suspend fun downloadFile(
        fileId: Int,
        priority: Int,
        offset: Long,
        limit: Long,
    ): TelegramClientResult<TelegramClientFileSnapshot>

    suspend fun cancelDownloadFile(fileId: Int): TelegramClientResult<Unit>

    suspend fun getFile(fileId: Int): TelegramClientResult<TelegramClientFileSnapshot>

    suspend fun getFileDownloadedPrefixSize(
        fileId: Int,
        offset: Long,
    ): TelegramClientResult<Long>

    suspend fun deleteFile(fileId: Int): TelegramClientResult<Unit>

    suspend fun getStorageStatistics(): TelegramClientResult<TelegramClientStorageStatistics>

    suspend fun optimizeVideoStorage(maxBytes: Long): TelegramClientResult<TelegramClientStorageStatistics>
}

internal sealed interface TelegramFileClientEvent {
    data class FileUpdated(val file: TelegramClientFileSnapshot) : TelegramFileClientEvent
    data object Ready : TelegramFileClientEvent
    data object AccountLoggingOut : TelegramFileClientEvent
}
