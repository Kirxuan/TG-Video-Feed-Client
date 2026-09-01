package com.qixuan.channelvideoflow.feature.channels

import com.qixuan.channelvideoflow.domain.channel.TelegramChatRepository
import com.qixuan.channelvideoflow.model.channel.TelegramChannel
import com.qixuan.channelvideoflow.model.channel.TelegramChatSyncState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

internal class FakeTelegramChatRepository(
    initialChannels: List<TelegramChannel> = emptyList(),
    initialSyncState: TelegramChatSyncState = TelegramChatSyncState.Loading,
) : TelegramChatRepository {
    private val mutableChannels = MutableStateFlow(initialChannels)
    private val mutableSyncState = MutableStateFlow(initialSyncState)
    override val channels: Flow<List<TelegramChannel>> = mutableChannels
    override val syncState: StateFlow<TelegramChatSyncState> = mutableSyncState

    var refreshCalls = 0
        private set
    val savedSelections = mutableListOf<Set<Long>>()
    val pinnedUpdates = mutableListOf<Pair<Long, Boolean>>()
    var saveFailure: Throwable? = null
    var pinFailure: Throwable? = null

    override suspend fun refresh() {
        refreshCalls += 1
    }

    override suspend fun saveSelectedChannelIds(chatIds: Set<Long>) {
        saveFailure?.let { throw it }
        savedSelections += chatIds
        mutableChannels.value = mutableChannels.value.map { channel ->
            channel.copy(isSelected = channel.chatId in chatIds)
        }
    }

    override suspend fun setChannelPinned(chatId: Long, isPinned: Boolean) {
        pinFailure?.let { throw it }
        pinnedUpdates += chatId to isPinned
        mutableChannels.value = mutableChannels.value.map { channel ->
            if (channel.chatId == chatId) channel.copy(isPinned = isPinned) else channel
        }
    }

    fun emitChannels(channels: List<TelegramChannel>) {
        mutableChannels.value = channels
    }

    fun emitSyncState(state: TelegramChatSyncState) {
        mutableSyncState.value = state
    }
}
