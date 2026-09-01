package com.qixuan.channelvideoflow.telegram.chat

import com.qixuan.channelvideoflow.database.ChannelDao
import com.qixuan.channelvideoflow.database.ChannelEntity
import com.qixuan.channelvideoflow.database.toModel
import com.qixuan.channelvideoflow.domain.channel.TelegramChatRepository
import com.qixuan.channelvideoflow.model.channel.TelegramChannel
import com.qixuan.channelvideoflow.model.channel.TelegramChatFailure
import com.qixuan.channelvideoflow.model.channel.TelegramChatSyncState
import com.qixuan.channelvideoflow.telegram.client.TelegramChatClient
import com.qixuan.channelvideoflow.telegram.client.TelegramChatClientEvent
import com.qixuan.channelvideoflow.telegram.client.TelegramClientChat
import com.qixuan.channelvideoflow.telegram.client.TelegramClientChatList
import com.qixuan.channelvideoflow.telegram.client.TelegramClientChatType
import com.qixuan.channelvideoflow.telegram.client.TelegramClientFailure
import com.qixuan.channelvideoflow.telegram.client.TelegramClientResult
import com.qixuan.channelvideoflow.telegram.client.TelegramClientSupergroup
import com.qixuan.channelvideoflow.telegram.client.TelegramLoadChatsResult
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

internal class TdLibTelegramChatRepository(
    private val client: TelegramChatClient,
    private val channelDao: ChannelDao,
    scope: CoroutineScope,
) : TelegramChatRepository {
    private val mutableSyncState =
        MutableStateFlow<TelegramChatSyncState>(TelegramChatSyncState.Loading)
    override val syncState: StateFlow<TelegramChatSyncState> = mutableSyncState.asStateFlow()

    override val channels: Flow<List<TelegramChannel>> = channelDao
        .observeAvailableChannels()
        .map { entities -> entities.map(ChannelEntity::toModel) }
        .catch {
            mutableSyncState.value =
                TelegramChatSyncState.Failed(TelegramChatFailure.Database)
            emit(emptyList())
        }

    private val refreshMutex = Mutex()
    private val accountGeneration = AtomicLong(0)
    private val chatsById = ConcurrentHashMap<Long, TelegramClientChat>()
    private val supergroupsById = ConcurrentHashMap<Long, TelegramClientSupergroup>()

    init {
        scope.launch {
            client.chatEvents.collect { event ->
                try {
                    handleEvent(event)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Throwable) {
                    mutableSyncState.value =
                        TelegramChatSyncState.Failed(TelegramChatFailure.Database)
                }
            }
        }
    }

    override suspend fun refresh() {
        refreshMutex.withLock {
            val generation = accountGeneration.get()
            mutableSyncState.value = TelegramChatSyncState.Loading
            try {
                val availableChannels = loadAllChannels(generation)
                ensureCurrentGeneration(generation)
                channelDao.reconcileAvailableChannels(availableChannels)
                mutableSyncState.value = TelegramChatSyncState.Ready
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: RefreshFailure) {
                mutableSyncState.value = TelegramChatSyncState.Failed(failure.failure)
            } catch (_: Throwable) {
                mutableSyncState.value =
                    TelegramChatSyncState.Failed(TelegramChatFailure.Database)
            }
        }
    }

    override suspend fun saveSelectedChannelIds(chatIds: Set<Long>) {
        try {
            channelDao.replaceSelection(chatIds)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            mutableSyncState.value =
                TelegramChatSyncState.Failed(TelegramChatFailure.Database)
            throw throwable
        }
    }

    override suspend fun setChannelPinned(chatId: Long, isPinned: Boolean) {
        try {
            channelDao.setChannelPinned(chatId, isPinned)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            mutableSyncState.value =
                TelegramChatSyncState.Failed(TelegramChatFailure.Database)
            throw throwable
        }
    }

    private suspend fun loadAllChannels(generation: Long): List<ChannelEntity> {
        val chatIds = linkedSetOf<Long>()
        TelegramClientChatList.entries.forEach { list ->
            loadEntireChatList(list, generation)
            val chats = request(generation) { client.getChats(list, MAX_CHAT_COUNT) }
            chatIds += chats.chatIds
        }

        return buildList {
            chatIds.forEach { chatId ->
                val chat = when (val result = requestAllowingNotFound(generation) {
                    client.getChat(chatId)
                }) {
                    null -> return@forEach
                    else -> result
                }
                chatsById[chat.chatId] = chat
                val type = chat.type as? TelegramClientChatType.Supergroup ?: return@forEach
                if (!type.isChannel) return@forEach

                val supergroup = when (val result = requestAllowingNotFound(generation) {
                    client.getSupergroup(type.supergroupId)
                }) {
                    null -> return@forEach
                    else -> result
                }
                supergroupsById[supergroup.supergroupId] = supergroup
                TdLibChannelMapper.map(chat, supergroup)?.let(::add)
            }
        }
    }

    private suspend fun loadEntireChatList(
        chatList: TelegramClientChatList,
        generation: Long,
    ) {
        repeat(MAX_LOAD_PAGES) {
            ensureCurrentGeneration(generation)
            when (val result = withRequestTimeout { client.loadChats(chatList, PAGE_SIZE) }) {
                TelegramLoadChatsResult.Loaded -> Unit
                TelegramLoadChatsResult.EndReached -> return
                is TelegramLoadChatsResult.Failed -> throw RefreshFailure(result.failure.toDomain())
            }
        }
        throw RefreshFailure(TelegramChatFailure.Unknown)
    }

    private suspend fun handleEvent(event: TelegramChatClientEvent) {
        when (event) {
            is TelegramChatClientEvent.ChatChanged -> {
                chatsById[event.chat.chatId] = event.chat
                refreshSingleChat(event.chat)
            }
            is TelegramChatClientEvent.ChatTitleChanged -> {
                chatsById.computeIfPresent(event.chatId) { _, chat ->
                    chat.copy(title = event.title)
                }
                channelDao.updateTitle(event.chatId, event.title)
            }
            is TelegramChatClientEvent.SupergroupChanged -> {
                supergroupsById[event.supergroup.supergroupId] = event.supergroup
                chatsById.values
                    .filter { chat ->
                        (chat.type as? TelegramClientChatType.Supergroup)?.supergroupId ==
                            event.supergroup.supergroupId
                    }
                    .forEach { chat -> persistMappedChannel(chat, event.supergroup) }
            }
            TelegramChatClientEvent.AccountLoggingOut -> {
                accountGeneration.incrementAndGet()
                chatsById.clear()
                supergroupsById.clear()
                channelDao.clearAll()
                mutableSyncState.value = TelegramChatSyncState.Loading
            }
        }
    }

    private suspend fun refreshSingleChat(chat: TelegramClientChat) {
        val type = chat.type as? TelegramClientChatType.Supergroup
        if (type == null || !type.isChannel) {
            channelDao.markChannelUnavailable(chat.chatId)
            return
        }

        val supergroup = supergroupsById[type.supergroupId]
            ?: when (val result = client.getSupergroup(type.supergroupId)) {
                is TelegramClientResult.Success -> result.value
                is TelegramClientResult.Failure -> {
                    if (result.failure == TelegramClientFailure.NotFound) {
                        channelDao.markChannelUnavailable(chat.chatId)
                    }
                    return
                }
            }
        supergroupsById[supergroup.supergroupId] = supergroup
        persistMappedChannel(chat, supergroup)
    }

    private suspend fun persistMappedChannel(
        chat: TelegramClientChat,
        supergroup: TelegramClientSupergroup,
    ) {
        val channel = TdLibChannelMapper.map(chat, supergroup)
        if (channel == null) {
            channelDao.markChannelUnavailable(chat.chatId)
        } else {
            channelDao.upsertAvailableChannel(channel)
        }
    }

    private suspend fun <T> request(
        generation: Long,
        block: suspend () -> TelegramClientResult<T>,
    ): T = requestAllowingNotFound(generation, block)
        ?: throw RefreshFailure(TelegramChatFailure.Unknown)

    private suspend fun <T> requestAllowingNotFound(
        generation: Long,
        block: suspend () -> TelegramClientResult<T>,
    ): T? {
        ensureCurrentGeneration(generation)
        return when (val result = withRequestTimeout(block)) {
            is TelegramClientResult.Success -> result.value
            is TelegramClientResult.Failure -> {
                if (result.failure == TelegramClientFailure.NotFound) {
                    null
                } else {
                    throw RefreshFailure(result.failure.toDomain())
                }
            }
        }
    }

    private suspend fun <T> withRequestTimeout(block: suspend () -> T): T = try {
        withTimeout(REQUEST_TIMEOUT_MILLIS) { block() }
    } catch (_: TimeoutCancellationException) {
        throw RefreshFailure(TelegramChatFailure.Timeout)
    }

    private fun ensureCurrentGeneration(expected: Long) {
        if (accountGeneration.get() != expected) throw CancellationException("Account changed")
    }

    private fun TelegramClientFailure.toDomain(): TelegramChatFailure = when (this) {
        TelegramClientFailure.SessionUnavailable -> TelegramChatFailure.Unknown
        TelegramClientFailure.NetworkUnavailable -> TelegramChatFailure.NetworkUnavailable
        is TelegramClientFailure.FloodWait -> TelegramChatFailure.FloodWait(retryAfterSeconds)
        TelegramClientFailure.AccessLost -> TelegramChatFailure.Unknown
        TelegramClientFailure.Timeout -> TelegramChatFailure.Timeout
        TelegramClientFailure.NotFound -> TelegramChatFailure.Unknown
        is TelegramClientFailure.RequestRejected -> TelegramChatFailure.RequestRejected(code)
        TelegramClientFailure.Unknown -> TelegramChatFailure.Unknown
    }

    private class RefreshFailure(val failure: TelegramChatFailure) : RuntimeException()

    private companion object {
        const val PAGE_SIZE = 100
        const val MAX_LOAD_PAGES = 1_000
        const val MAX_CHAT_COUNT = 100_000
        const val REQUEST_TIMEOUT_MILLIS = 15_000L
    }
}
