package com.qixuan.channelvideoflow.domain.channel

import com.qixuan.channelvideoflow.model.channel.TelegramChannel
import com.qixuan.channelvideoflow.model.channel.TelegramChatSyncState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface TelegramChatRepository {
    val channels: Flow<List<TelegramChannel>>
    val syncState: StateFlow<TelegramChatSyncState>

    suspend fun refresh()

    suspend fun saveSelectedChannelIds(chatIds: Set<Long>)

    suspend fun setChannelPinned(chatId: Long, isPinned: Boolean)
}
