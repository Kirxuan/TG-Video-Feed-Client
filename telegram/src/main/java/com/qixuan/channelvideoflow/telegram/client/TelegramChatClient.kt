package com.qixuan.channelvideoflow.telegram.client

import kotlinx.coroutines.flow.Flow

internal interface TelegramChatClient {
    val chatEvents: Flow<TelegramChatClientEvent>

    suspend fun loadChats(
        chatList: TelegramClientChatList,
        limit: Int,
    ): TelegramLoadChatsResult

    suspend fun getChats(
        chatList: TelegramClientChatList,
        limit: Int,
    ): TelegramClientResult<TelegramClientChats>

    suspend fun getChat(chatId: Long): TelegramClientResult<TelegramClientChat>

    suspend fun getSupergroup(
        supergroupId: Long,
    ): TelegramClientResult<TelegramClientSupergroup>
}
