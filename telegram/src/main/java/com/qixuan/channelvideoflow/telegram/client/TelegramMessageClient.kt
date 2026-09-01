package com.qixuan.channelvideoflow.telegram.client

import kotlinx.coroutines.flow.Flow

internal interface TelegramMessageClient {
    val messageEvents: Flow<TelegramMessageClientEvent>

    suspend fun searchChatVideos(
        chatId: Long,
        fromMessageId: Long,
        limit: Int,
    ): TelegramClientResult<TelegramClientVideoSearchPage>

    suspend fun getMessage(
        chatId: Long,
        messageId: Long,
    ): TelegramClientResult<TelegramClientMessage>

    suspend fun getMessageProperties(
        chatId: Long,
        messageId: Long,
    ): TelegramClientResult<TelegramClientMessageProperties>

    suspend fun getMessageLink(
        chatId: Long,
        messageId: Long,
    ): TelegramClientResult<TelegramClientMessageLink>
}
