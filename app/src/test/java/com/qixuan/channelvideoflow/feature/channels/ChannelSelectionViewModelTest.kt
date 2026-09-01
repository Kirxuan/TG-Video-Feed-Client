package com.qixuan.channelvideoflow.feature.channels

import com.qixuan.channelvideoflow.model.channel.TelegramChannel
import com.qixuan.channelvideoflow.model.channel.TelegramChatFailure
import com.qixuan.channelvideoflow.model.channel.TelegramChatSyncState
import com.qixuan.channelvideoflow.model.video.ChannelVideoScanProgress
import com.qixuan.channelvideoflow.model.video.VideoScanStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runners.model.Statement

@OptIn(ExperimentalCoroutinesApi::class)
class ChannelSelectionViewModelTest {
    @get:Rule
    val mainDispatcherRule = ChannelMainDispatcherRule()

    @Test
    fun searchIsLocalAndSavingTwoChannelsDelegatesExactIds() = runTest {
        val repository = FakeTelegramChatRepository(
            initialChannels = listOf(
                channel(1, "Alpha 频道", "alpha"),
                channel(2, "Beta 频道", "beta"),
                channel(3, "Gamma 频道", null),
            ),
            initialSyncState = TelegramChatSyncState.Ready,
        )
        val messageRepository = FakeTelegramMessageRepository()
        val viewModel = ChannelSelectionViewModel(repository, messageRepository)
        runCurrent()

        viewModel.onSearchQueryChanged("BETA")
        assertEquals(listOf(2L), viewModel.uiState.value.channels.map { it.chatId })

        viewModel.onSearchQueryChanged("")
        viewModel.toggleChannel(1)
        viewModel.toggleChannel(2)
        assertEquals(2, viewModel.uiState.value.selectedCount)
        assertTrue(viewModel.uiState.value.canSave)

        viewModel.saveSelection()
        runCurrent()

        assertEquals(listOf(setOf(1L, 2L)), repository.savedSelections)
        assertEquals(ChannelSaveStatus.Saved(2), viewModel.uiState.value.saveStatus)
        assertFalse(viewModel.uiState.value.canSave)
        assertEquals(1, repository.refreshCalls)
        assertEquals(2, messageRepository.refreshSelectionCalls)
    }

    @Test
    fun persistedSelectionIsRestoredAndAccessLossRemovesUnavailableDraftIds() = runTest {
        val repository = FakeTelegramChatRepository(
            initialChannels = listOf(
                channel(1, "频道一", isSelected = true),
                channel(2, "频道二", isSelected = true),
            ),
            initialSyncState = TelegramChatSyncState.Ready,
        )
        val viewModel = ChannelSelectionViewModel(repository, FakeTelegramMessageRepository())
        runCurrent()

        assertEquals(2, viewModel.uiState.value.selectedCount)
        assertFalse(viewModel.uiState.value.canSave)

        viewModel.toggleChannel(1)
        repository.emitChannels(listOf(channel(1, "频道一", isSelected = true)))
        runCurrent()

        assertEquals(0, viewModel.uiState.value.selectedCount)
        assertTrue(viewModel.uiState.value.canSave)
        assertEquals(listOf(1L), viewModel.uiState.value.channels.map { it.chatId })
    }

    @Test
    fun loadingEmptyErrorAndCachedContentStatesRemainDistinct() = runTest {
        val repository = FakeTelegramChatRepository()
        val viewModel = ChannelSelectionViewModel(repository, FakeTelegramMessageRepository())
        runCurrent()
        assertEquals(ChannelListPhase.LOADING, viewModel.uiState.value.phase)

        repository.emitSyncState(TelegramChatSyncState.Ready)
        runCurrent()
        assertEquals(ChannelListPhase.EMPTY, viewModel.uiState.value.phase)

        repository.emitSyncState(
            TelegramChatSyncState.Failed(TelegramChatFailure.NetworkUnavailable),
        )
        runCurrent()
        assertEquals(ChannelListPhase.ERROR, viewModel.uiState.value.phase)
        assertEquals(TelegramChatFailure.NetworkUnavailable, viewModel.uiState.value.failure)

        repository.emitChannels(listOf(channel(9, "缓存频道")))
        runCurrent()
        assertEquals(ChannelListPhase.CONTENT, viewModel.uiState.value.phase)
        assertEquals(TelegramChatFailure.NetworkUnavailable, viewModel.uiState.value.failure)
    }

    @Test
    fun saveFailureIsSanitizedAndKeepsTheDraftRetryable() = runTest {
        val repository = FakeTelegramChatRepository(
            initialChannels = listOf(channel(1, "频道一")),
            initialSyncState = TelegramChatSyncState.Ready,
        ).apply {
            saveFailure = IllegalStateException("synthetic database detail")
        }
        val viewModel = ChannelSelectionViewModel(repository, FakeTelegramMessageRepository())
        runCurrent()
        viewModel.toggleChannel(1)

        viewModel.saveSelection()
        runCurrent()

        assertEquals(ChannelSaveStatus.Failed, viewModel.uiState.value.saveStatus)
        assertTrue(viewModel.uiState.value.canSave)
    }

    @Test
    fun manualPinDoesNotChangeSelectionAndPersistsThroughTheRepository() = runTest {
        val repository = FakeTelegramChatRepository(
            initialChannels = listOf(channel(1, "频道一")),
            initialSyncState = TelegramChatSyncState.Ready,
        )
        val viewModel = ChannelSelectionViewModel(repository, FakeTelegramMessageRepository())
        runCurrent()

        viewModel.toggleChannelPinned(1)
        runCurrent()

        assertEquals(listOf(1L to true), repository.pinnedUpdates)
        assertTrue(viewModel.uiState.value.channels.single().isPinned)
        assertFalse(viewModel.uiState.value.channels.single().isSelected)
        assertEquals(0, viewModel.uiState.value.selectedCount)

        viewModel.toggleChannelPinned(1)
        runCurrent()

        assertEquals(listOf(1L to true, 1L to false), repository.pinnedUpdates)
        assertFalse(viewModel.uiState.value.channels.single().isPinned)
    }

    @Test
    fun floodWaitBlocksRefreshUntilTheServerDelayExpires() = runTest {
        val repository = FakeTelegramChatRepository(
            initialSyncState = TelegramChatSyncState.Ready,
        )
        val viewModel = ChannelSelectionViewModel(repository, FakeTelegramMessageRepository())
        runCurrent()
        assertEquals(1, repository.refreshCalls)

        repository.emitSyncState(
            TelegramChatSyncState.Failed(TelegramChatFailure.FloodWait(2)),
        )
        runCurrent()
        viewModel.refresh()
        runCurrent()
        assertEquals(1, repository.refreshCalls)
        assertEquals(2, viewModel.uiState.value.retrySecondsRemaining)

        advanceTimeBy(2_000)
        runCurrent()
        viewModel.refresh()
        runCurrent()

        assertEquals(2, repository.refreshCalls)
        assertEquals(0, viewModel.uiState.value.retrySecondsRemaining)
    }

    @Test
    fun scanProgressAndPauseResumeAreExposedWithoutTdLibTypes() = runTest {
        val chatRepository = FakeTelegramChatRepository(
            initialChannels = listOf(channel(1, "频道一", isSelected = true)),
            initialSyncState = TelegramChatSyncState.Ready,
        )
        val messageRepository = FakeTelegramMessageRepository(
            listOf(
                ChannelVideoScanProgress(
                    chatId = 1,
                    channelTitle = "频道一",
                    status = VideoScanStatus.SCANNING,
                    processedVideoCandidateCount = 200,
                    videoSearchPageCount = 2,
                    indexedVideoCount = 12,
                    approximateVideoCount = 13,
                    duplicateVideoEncounterCount = 1,
                    exceptionCount = 0,
                    nextVideoSearchCursor = 80,
                    latestSyncedMessageId = 100,
                    isPausedByUser = false,
                ),
            ),
        )
        val viewModel = ChannelSelectionViewModel(chatRepository, messageRepository)
        runCurrent()

        assertEquals(200L, viewModel.uiState.value.scanSummary.processedVideoCandidateCount)
        assertEquals(12, viewModel.uiState.value.scanSummary.indexedVideoCount)
        assertEquals(VideoScanStatus.SCANNING, viewModel.uiState.value.channels.single().scanStatus)

        viewModel.onForegroundChanged(true)
        viewModel.pauseScanning()
        viewModel.resumeScanning()
        runCurrent()

        assertEquals(listOf(true), messageRepository.foregroundChanges)
        assertEquals(1, messageRepository.pauseCalls)
        assertEquals(1, messageRepository.resumeCalls)
    }

    private fun channel(
        id: Long,
        title: String,
        username: String? = null,
        isSelected: Boolean = false,
    ) = TelegramChannel(id, title, username, isSelected)
}

@OptIn(ExperimentalCoroutinesApi::class)
class ChannelMainDispatcherRule(
    private val dispatcher: TestDispatcher = StandardTestDispatcher(),
) : TestWatcher(), TestRule {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }

    override fun apply(base: Statement, description: Description): Statement =
        super.apply(base, description)
}
