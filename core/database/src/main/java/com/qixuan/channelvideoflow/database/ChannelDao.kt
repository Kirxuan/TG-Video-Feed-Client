package com.qixuan.channelvideoflow.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.qixuan.channelvideoflow.model.channel.ChannelAccessState
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ChannelDao {
    fun observeAvailableChannels(): Flow<List<ChannelEntity>> =
        observeChannelsByAccessState(ChannelAccessState.AVAILABLE)

    @Query(
        """
        SELECT * FROM channels
        WHERE access_state = :accessState
        ORDER BY is_pinned DESC, is_selected DESC, title COLLATE NOCASE ASC, chat_id ASC
        """,
    )
    protected abstract fun observeChannelsByAccessState(
        accessState: ChannelAccessState,
    ): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels")
    abstract suspend fun getAll(): List<ChannelEntity>

    @Query("SELECT chat_id FROM channels WHERE is_selected = 1 AND access_state = :accessState")
    abstract suspend fun getSelectedChannelIds(
        accessState: ChannelAccessState = ChannelAccessState.AVAILABLE,
    ): List<Long>

    @Upsert
    protected abstract suspend fun upsert(entity: ChannelEntity)

    @Query(
        """
        UPDATE channels
        SET access_state = :accessState, is_selected = 0
        WHERE chat_id = :chatId
        """,
    )
    protected abstract suspend fun markUnavailable(
        chatId: Long,
        accessState: ChannelAccessState = ChannelAccessState.UNAVAILABLE,
    )

    @Query("UPDATE channels SET title = :title WHERE chat_id = :chatId")
    abstract suspend fun updateTitle(chatId: Long, title: String)

    @Query("UPDATE channels SET is_pinned = :isPinned WHERE chat_id = :chatId")
    abstract suspend fun setChannelPinned(chatId: Long, isPinned: Boolean)

    @Query("UPDATE channels SET is_selected = 0")
    protected abstract suspend fun clearSelection()

    @Query(
        """
        UPDATE channels
        SET is_selected = 1
        WHERE chat_id IN (:chatIds) AND access_state = :accessState
        """,
    )
    protected abstract suspend fun selectAvailableChannels(
        chatIds: List<Long>,
        accessState: ChannelAccessState = ChannelAccessState.AVAILABLE,
    )

    @Query("DELETE FROM channels")
    abstract suspend fun clearAll()

    @Transaction
    open suspend fun upsertAvailableChannel(incoming: ChannelEntity) {
        val current = getAll().firstOrNull { it.chatId == incoming.chatId }
        upsert(
            if (current == null) {
                incoming.copy(accessState = ChannelAccessState.AVAILABLE)
            } else {
                current.copy(
                    title = incoming.title,
                    username = incoming.username,
                    accessState = ChannelAccessState.AVAILABLE,
                )
            },
        )
    }

    @Transaction
    open suspend fun reconcileAvailableChannels(incoming: List<ChannelEntity>) {
        val currentById = getAll().associateBy(ChannelEntity::chatId)
        val incomingIds = incoming.mapTo(mutableSetOf(), ChannelEntity::chatId)

        incoming.forEach { channel ->
            val current = currentById[channel.chatId]
            upsert(
                if (current == null) {
                    channel.copy(accessState = ChannelAccessState.AVAILABLE)
                } else {
                    current.copy(
                        title = channel.title,
                        username = channel.username,
                        accessState = ChannelAccessState.AVAILABLE,
                    )
                },
            )
        }

        currentById.values
            .asSequence()
            .filter { it.accessState == ChannelAccessState.AVAILABLE && it.chatId !in incomingIds }
            .forEach { markUnavailable(it.chatId) }
    }

    @Transaction
    open suspend fun markChannelUnavailable(chatId: Long) {
        markUnavailable(chatId)
    }

    @Transaction
    open suspend fun replaceSelection(chatIds: Set<Long>) {
        clearSelection()
        if (chatIds.isNotEmpty()) {
            selectAvailableChannels(chatIds.toList())
        }
    }
}
