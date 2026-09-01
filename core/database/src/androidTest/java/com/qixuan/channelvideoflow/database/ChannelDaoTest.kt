package com.qixuan.channelvideoflow.database

import android.content.Context
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.qixuan.channelvideoflow.model.channel.ChannelAccessState
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ChannelDaoTest {
    private lateinit var context: Context
    private lateinit var database: ChannelVideoFlowDatabase
    private lateinit var dao: ChannelDao

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, ChannelVideoFlowDatabase::class.java)
            .build()
        dao = database.channelDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun metadataUpdatePreservesSelectionAndUnavailableChannelIsHidden() = runBlocking {
        dao.reconcileAvailableChannels(listOf(channel(1, "旧标题"), channel(2, "频道 B")))
        dao.replaceSelection(setOf(1, 2))

        dao.upsertAvailableChannel(channel(1, "新标题", username = "renamed"))
        dao.markChannelUnavailable(2)

        assertEquals(
            listOf(channel(1, "新标题", username = "renamed", isSelected = true)),
            dao.observeAvailableChannels().first(),
        )
        assertEquals(listOf(1L), dao.getSelectedChannelIds())
    }

    @Test
    fun successfulReconciliationMarksMissingChannelsUnavailableAndClearsTheirSelection() =
        runBlocking {
            dao.reconcileAvailableChannels(listOf(channel(1, "频道 A"), channel(2, "频道 B")))
            dao.replaceSelection(setOf(1, 2))

            dao.reconcileAvailableChannels(listOf(channel(1, "频道 A")))

            assertEquals(listOf(1L), dao.getSelectedChannelIds())
            assertEquals(
                ChannelAccessState.UNAVAILABLE,
                dao.getAll().single { it.chatId == 2L }.accessState,
            )
        }

    @Test
    fun manuallyPinnedChannelsComeBeforeSavedSelectionsAndStayPinnedAfterUnselecting() =
        runBlocking {
            dao.reconcileAvailableChannels(
                listOf(
                    channel(1, "Bravo"),
                    channel(2, "Charlie"),
                    channel(3, "Alpha"),
                ),
            )
            dao.replaceSelection(setOf(2))
            dao.setChannelPinned(chatId = 3, isPinned = true)

            assertEquals(
                listOf(3L, 2L, 1L),
                dao.observeAvailableChannels().first().map(ChannelEntity::chatId),
            )

            dao.replaceSelection(emptySet())

            assertEquals(
                listOf(3L, 1L, 2L),
                dao.observeAvailableChannels().first().map(ChannelEntity::chatId),
            )
        }

    @Test
    fun twoSelectionsSurviveDatabaseCloseAndReopen() = runBlocking {
        database.close()
        val databaseName = "channel-selection-${UUID.randomUUID()}.db"
        context.deleteDatabase(databaseName)

        try {
            val firstDatabase = openPersistentDatabase(databaseName)
            try {
                firstDatabase.channelDao().reconcileAvailableChannels(
                    listOf(channel(1, "频道 A"), channel(2, "频道 B"), channel(3, "频道 C")),
                )
                firstDatabase.channelDao().replaceSelection(setOf(1, 2))
                firstDatabase.channelDao().setChannelPinned(chatId = 3, isPinned = true)
            } finally {
                firstDatabase.close()
            }

            val reopenedDatabase = openPersistentDatabase(databaseName)
            try {
                assertEquals(
                    setOf(1L, 2L),
                    reopenedDatabase.channelDao().getSelectedChannelIds().toSet(),
                )
                assertEquals(
                    true,
                    reopenedDatabase.channelDao().getAll().single { it.chatId == 3L }.isPinned,
                )
            } finally {
                reopenedDatabase.close()
            }
        } finally {
            context.deleteDatabase(databaseName)
            database = Room.inMemoryDatabaseBuilder(context, ChannelVideoFlowDatabase::class.java)
                .build()
            dao = database.channelDao()
        }
    }

    private fun openPersistentDatabase(name: String): ChannelVideoFlowDatabase =
        Room.databaseBuilder(context, ChannelVideoFlowDatabase::class.java, name).build()

    private fun channel(
        id: Long,
        title: String,
        username: String? = null,
        isSelected: Boolean = false,
    ): ChannelEntity = ChannelEntity(
        chatId = id,
        title = title,
        username = username,
        isSelected = isSelected,
        accessState = ChannelAccessState.AVAILABLE,
    )
}
