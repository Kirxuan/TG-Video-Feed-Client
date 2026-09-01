package com.qixuan.channelvideoflow.telegram.chat

import com.qixuan.channelvideoflow.database.ChannelDao
import com.qixuan.channelvideoflow.database.ChannelEntity
import com.qixuan.channelvideoflow.model.channel.ChannelAccessState
import com.qixuan.channelvideoflow.model.channel.TelegramChatFailure
import com.qixuan.channelvideoflow.model.channel.TelegramChatSyncState
import com.qixuan.channelvideoflow.telegram.client.TelegramChatClient
import com.qixuan.channelvideoflow.telegram.client.TelegramChatClientEvent
import com.qixuan.channelvideoflow.telegram.client.TelegramClientChat
import com.qixuan.channelvideoflow.telegram.client.TelegramClientChatList
import com.qixuan.channelvideoflow.telegram.client.TelegramClientChatType
import com.qixuan.channelvideoflow.telegram.client.TelegramClientChats
import com.qixuan.channelvideoflow.telegram.client.TelegramClientFailure
import com.qixuan.channelvideoflow.telegram.client.TelegramClientMemberStatus
import com.qixuan.channelvideoflow.telegram.client.TelegramClientResult
import com.qixuan.channelvideoflow.telegram.client.TelegramClientSupergroup
import com.qixuan.channelvideoflow.telegram.client.TelegramLoadChatsResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TdLibTelegramChatRepositoryTest {
    @Test
    fun refreshPaginatesBothListsAndPersistsOnlyAccessibleChannels() = runTest {
        val client = FakeTelegramChatClient().apply {
            loadResults[TelegramClientChatList.MAIN] = ArrayDeque(
                listOf(
                    TelegramLoadChatsResult.Loaded,
                    TelegramLoadChatsResult.Loaded,
                    TelegramLoadChatsResult.EndReached,
                ),
            )
            chatsByList[TelegramClientChatList.MAIN] = listOf(1, 2, 3, 4)
            chatsByList[TelegramClientChatList.ARCHIVE] = listOf(5)
            chats[1] = chat(1, TelegramClientChatType.Private)
            chats[2] = chat(2, TelegramClientChatType.BasicGroup)
            chats[3] = channelChat(3, 30)
            chats[4] = channelChat(4, 40)
            chats[5] = chat(5, TelegramClientChatType.Supergroup(50, isChannel = false))
            supergroups[30] = supergroup(30, TelegramClientMemberStatus.Member)
            supergroups[40] = supergroup(40, TelegramClientMemberStatus.Left)
            supergroups[50] = supergroup(
                50,
                TelegramClientMemberStatus.Member,
                isChannel = false,
            )
        }
        val dao = FakeChannelDao()
        val repository = TdLibTelegramChatRepository(
            client,
            dao,
            backgroundScope,
        )

        repository.refresh()

        assertEquals(TelegramChatSyncState.Ready, repository.syncState.value)
        assertEquals(listOf(3L), repository.channels.first().map { it.chatId })
        assertEquals(3, client.loadCalls.count { it == TelegramClientChatList.MAIN })
        assertEquals(1, client.loadCalls.count { it == TelegramClientChatList.ARCHIVE })
    }

    @Test
    fun failedRefreshKeepsCachedChannelsAndSurfacesSanitizedFailure() = runTest {
        val client = FakeTelegramChatClient().apply {
            loadResults[TelegramClientChatList.MAIN] = ArrayDeque(
                listOf(
                    TelegramLoadChatsResult.Failed(
                        TelegramClientFailure.NetworkUnavailable,
                    ),
                ),
            )
        }
        val dao = FakeChannelDao(
            listOf(
                ChannelEntity(
                    chatId = 9,
                    title = "缓存频道",
                    username = null,
                    accessState = ChannelAccessState.AVAILABLE,
                ),
            ),
        )
        val repository = TdLibTelegramChatRepository(client, dao, backgroundScope)

        repository.refresh()

        assertEquals(
            TelegramChatSyncState.Failed(TelegramChatFailure.NetworkUnavailable),
            repository.syncState.value,
        )
        assertEquals(listOf(9L), repository.channels.first().map { it.chatId })
    }

    @Test
    fun titleAccessAndLogoutUpdatesModifyThePersistedSourceOfTruth() = runTest {
        val client = FakeTelegramChatClient()
        val dao = FakeChannelDao()
        val repository = TdLibTelegramChatRepository(
            client,
            dao,
            backgroundScope,
        )
        runCurrent()
        val chat = channelChat(7, 70)
        client.supergroups[70] = supergroup(70, TelegramClientMemberStatus.Member)

        client.emit(TelegramChatClientEvent.ChatChanged(chat))
        runCurrent()
        dao.replaceSelection(setOf(7))
        client.emit(TelegramChatClientEvent.ChatTitleChanged(7, "更新后标题"))
        runCurrent()

        assertEquals("更新后标题", repository.channels.first().single().title)
        assertTrue(repository.channels.first().single().isSelected)

        client.emit(
            TelegramChatClientEvent.SupergroupChanged(
                supergroup(70, TelegramClientMemberStatus.Left),
            ),
        )
        runCurrent()
        assertTrue(repository.channels.first().isEmpty())
        assertTrue(dao.getSelectedChannelIds().isEmpty())

        client.emit(TelegramChatClientEvent.ChatChanged(chat))
        runCurrent()
        client.emit(TelegramChatClientEvent.AccountLoggingOut)
        runCurrent()
        assertTrue(dao.getAll().isEmpty())
    }

    @Test
    fun manualPinUpdatesTheLocalChannelMetadataWithoutATdLibRequest() = runTest {
        val client = FakeTelegramChatClient()
        val dao = FakeChannelDao(
            listOf(
                ChannelEntity(
                    chatId = 7,
                    title = "频道七",
                    username = null,
                    accessState = ChannelAccessState.AVAILABLE,
                ),
            ),
        )
        val repository = TdLibTelegramChatRepository(client, dao, backgroundScope)

        repository.setChannelPinned(chatId = 7, isPinned = true)

        assertTrue(repository.channels.first().single().isPinned)
        assertTrue(client.loadCalls.isEmpty())
    }

    private class FakeTelegramChatClient : TelegramChatClient {
        private val events = MutableSharedFlow<TelegramChatClientEvent>(extraBufferCapacity = 16)
        override val chatEvents: Flow<TelegramChatClientEvent> = events
        val loadResults = mutableMapOf<TelegramClientChatList, ArrayDeque<TelegramLoadChatsResult>>()
        val chatsByList = mutableMapOf<TelegramClientChatList, List<Long>>()
        val chats = mutableMapOf<Long, TelegramClientChat>()
        val supergroups = mutableMapOf<Long, TelegramClientSupergroup>()
        val loadCalls = mutableListOf<TelegramClientChatList>()

        override suspend fun loadChats(
            chatList: TelegramClientChatList,
            limit: Int,
        ): TelegramLoadChatsResult {
            loadCalls += chatList
            return loadResults[chatList]?.removeFirstOrNull()
                ?: TelegramLoadChatsResult.EndReached
        }

        override suspend fun getChats(
            chatList: TelegramClientChatList,
            limit: Int,
        ): TelegramClientResult<TelegramClientChats> {
            val ids = chatsByList[chatList].orEmpty()
            return TelegramClientResult.Success(TelegramClientChats(ids.size, ids))
        }

        override suspend fun getChat(chatId: Long): TelegramClientResult<TelegramClientChat> =
            chats[chatId]
                ?.let(TelegramClientResult<TelegramClientChat>::Success)
                ?: TelegramClientResult.Failure(TelegramClientFailure.NotFound)

        override suspend fun getSupergroup(
            supergroupId: Long,
        ): TelegramClientResult<TelegramClientSupergroup> = supergroups[supergroupId]
            ?.let(TelegramClientResult<TelegramClientSupergroup>::Success)
            ?: TelegramClientResult.Failure(TelegramClientFailure.NotFound)

        fun emit(event: TelegramChatClientEvent) {
            assertTrue(events.tryEmit(event))
        }
    }

    private class FakeChannelDao(
        initial: List<ChannelEntity> = emptyList(),
    ) : ChannelDao() {
        private val entities = MutableStateFlow(initial)

        override fun observeChannelsByAccessState(
            accessState: ChannelAccessState,
        ): Flow<List<ChannelEntity>> = entities.map { channels ->
            channels.filter { it.accessState == accessState }
        }

        override suspend fun getAll(): List<ChannelEntity> = entities.value

        override suspend fun getSelectedChannelIds(
            accessState: ChannelAccessState,
        ): List<Long> = entities.value
            .filter { it.accessState == accessState && it.isSelected }
            .map(ChannelEntity::chatId)

        override suspend fun upsert(entity: ChannelEntity) {
            entities.value = entities.value.filterNot { it.chatId == entity.chatId } + entity
        }

        override suspend fun markUnavailable(
            chatId: Long,
            accessState: ChannelAccessState,
        ) {
            entities.value = entities.value.map { entity ->
                if (entity.chatId == chatId) {
                    entity.copy(accessState = accessState, isSelected = false)
                } else {
                    entity
                }
            }
        }

        override suspend fun updateTitle(chatId: Long, title: String) {
            entities.value = entities.value.map { entity ->
                if (entity.chatId == chatId) entity.copy(title = title) else entity
            }
        }

        override suspend fun setChannelPinned(chatId: Long, isPinned: Boolean) {
            entities.value = entities.value.map { entity ->
                if (entity.chatId == chatId) entity.copy(isPinned = isPinned) else entity
            }
        }

        override suspend fun clearSelection() {
            entities.value = entities.value.map { it.copy(isSelected = false) }
        }

        override suspend fun selectAvailableChannels(
            chatIds: List<Long>,
            accessState: ChannelAccessState,
        ) {
            entities.value = entities.value.map { entity ->
                if (entity.chatId in chatIds && entity.accessState == accessState) {
                    entity.copy(isSelected = true)
                } else {
                    entity
                }
            }
        }

        override suspend fun clearAll() {
            entities.value = emptyList()
        }
    }

    private companion object {
        fun chat(id: Long, type: TelegramClientChatType) = TelegramClientChat(
            chatId = id,
            title = "聊天 $id",
            type = type,
        )

        fun channelChat(id: Long, supergroupId: Long) = chat(
            id,
            TelegramClientChatType.Supergroup(supergroupId, isChannel = true),
        )

        fun supergroup(
            id: Long,
            status: TelegramClientMemberStatus,
            isChannel: Boolean = true,
        ) = TelegramClientSupergroup(
            supergroupId = id,
            isChannel = isChannel,
            username = "channel_$id",
            memberStatus = status,
        )
    }
}
