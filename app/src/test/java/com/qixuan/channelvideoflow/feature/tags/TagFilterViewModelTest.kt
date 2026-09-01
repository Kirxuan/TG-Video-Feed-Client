package com.qixuan.channelvideoflow.feature.tags

import com.qixuan.channelvideoflow.domain.channel.TelegramChatRepository
import com.qixuan.channelvideoflow.domain.message.TelegramMessageRepository
import com.qixuan.channelvideoflow.domain.message.VideoReferenceFailure
import com.qixuan.channelvideoflow.domain.message.VideoReferenceResolution
import com.qixuan.channelvideoflow.model.channel.TelegramChannel
import com.qixuan.channelvideoflow.model.channel.TelegramChatSyncState
import com.qixuan.channelvideoflow.model.video.ChannelVideoScanProgress
import com.qixuan.channelvideoflow.model.video.IndexedVideo
import com.qixuan.channelvideoflow.model.video.OriginalMessageLinkResult
import com.qixuan.channelvideoflow.model.video.TagFilterMode
import com.qixuan.channelvideoflow.model.video.TagSummary
import com.qixuan.channelvideoflow.model.video.VideoFilter
import com.qixuan.channelvideoflow.model.video.VideoKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TagFilterViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun selectedChannelsDriveTagsAndUnavailableSelectionsAreRemoved() = runTest(dispatcher) {
        val chats = FakeChats(
            listOf(
                TelegramChannel(1L, "一", null, isSelected = true),
                TelegramChannel(2L, "二", null, isSelected = false),
            ),
        )
        val messages = FakeMessages(
            listOf(
                TagSummary("news", "#新闻", 8),
                TagSummary("music", "#音乐", 3),
            ),
        )
        val viewModel = TagFilterViewModel(chats, messages)
        val collection = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        runCurrent()

        assertEquals(setOf(1L), viewModel.uiState.value.channelIds)
        assertEquals(listOf("news", "music"), viewModel.uiState.value.tags.map { it.summary.normalizedName })
        viewModel.toggleTag("music")
        viewModel.setMode(TagFilterMode.AND)
        runCurrent()
        assertEquals(
            VideoFilter(setOf(1L), setOf("music"), TagFilterMode.AND),
            viewModel.currentFilter(),
        )

        messages.tags.value = listOf(TagSummary("news", "#新闻", 9))
        runCurrent()
        assertEquals(emptySet<String>(), viewModel.currentFilter().normalizedTags)
        collection.cancel()
    }

    @Test
    fun normalizerTrimsOptionalHashAndUsesNfkcWithLocaleRoot() {
        assertEquals("kotlin", normalizeTagSearchQuery("  #ＫｏＴＬＩＮ  "))
        assertEquals("新闻", normalizeTagSearchQuery(" #新闻"))
        assertEquals("mixed内容", normalizeTagSearchQuery("Mixed内容"))
        assertEquals("", normalizeTagSearchQuery("  #  "))
    }

    @Test
    fun searchMatchesChineseEnglishCaseLeadingHashAndNormalizedName() = runTest(dispatcher) {
        val chats = FakeChats(listOf(TelegramChannel(1L, "一", null, isSelected = true)))
        val messages = FakeMessages(
            listOf(
                TagSummary("新闻", "#新闻", 8),
                TagSummary("kotlin", "#Kotlin", 5),
                TagSummary("mmd", "#ＭＭＤ", 3),
                TagSummary("mixed内容", "#Mixed内容", 2),
            ),
        )
        val viewModel = TagFilterViewModel(chats, messages)
        val collection = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        runCurrent()

        viewModel.onSearchQueryChanged("  #新闻 ")
        runCurrent()
        assertEquals(listOf("新闻"), viewModel.uiState.value.tags.map { it.summary.normalizedName })

        viewModel.onSearchQueryChanged("KOTLIN")
        runCurrent()
        assertEquals(listOf("kotlin"), viewModel.uiState.value.tags.map { it.summary.normalizedName })

        viewModel.onSearchQueryChanged("#mmd")
        runCurrent()
        assertEquals(listOf("mmd"), viewModel.uiState.value.tags.map { it.summary.normalizedName })

        viewModel.onSearchQueryChanged("mixed内")
        runCurrent()
        assertEquals(listOf("mixed内容"), viewModel.uiState.value.tags.map { it.summary.normalizedName })
        assertEquals(4, viewModel.uiState.value.totalTagCount)
        collection.cancel()
    }

    @Test
    fun hiddenSelectionSurvivesSearchAndClearOperationsStayIndependent() = runTest(dispatcher) {
        val chats = FakeChats(listOf(TelegramChannel(1L, "一", null, isSelected = true)))
        val messages = FakeMessages(
            listOf(
                TagSummary("news", "#新闻", 8),
                TagSummary("music", "#音乐", 3),
            ),
        )
        val viewModel = TagFilterViewModel(chats, messages)
        val collection = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        runCurrent()

        viewModel.toggleTag("music")
        viewModel.setMode(TagFilterMode.AND)
        viewModel.onSearchQueryChanged("新闻")
        runCurrent()

        assertEquals(listOf("news"), viewModel.uiState.value.tags.map { it.summary.normalizedName })
        assertEquals(setOf("music"), viewModel.uiState.value.selectedNames)
        assertEquals(
            VideoFilter(setOf(1L), setOf("music"), TagFilterMode.AND),
            viewModel.currentFilter(),
        )

        viewModel.clearSearch()
        runCurrent()
        assertEquals("", viewModel.uiState.value.searchQuery)
        assertEquals(setOf("music"), viewModel.uiState.value.selectedNames)
        assertEquals(2, viewModel.uiState.value.tags.size)

        viewModel.onSearchQueryChanged("新闻")
        viewModel.clearSelection()
        runCurrent()
        assertEquals("新闻", viewModel.uiState.value.searchQuery)
        assertTrue(viewModel.uiState.value.selectedNames.isEmpty())
        assertTrue(viewModel.currentFilter().normalizedTags.isEmpty())
        collection.cancel()
    }

    private class FakeChats(initial: List<TelegramChannel>) : TelegramChatRepository {
        override val channels = MutableStateFlow(initial)
        override val syncState: StateFlow<TelegramChatSyncState> =
            MutableStateFlow(TelegramChatSyncState.Ready)
        override suspend fun refresh() = Unit
        override suspend fun saveSelectedChannelIds(chatIds: Set<Long>) = Unit
        override suspend fun setChannelPinned(chatId: Long, isPinned: Boolean) = Unit
    }

    private class FakeMessages(initial: List<TagSummary>) : TelegramMessageRepository {
        val tags = MutableStateFlow(initial)
        override val scanProgress: Flow<List<ChannelVideoScanProgress>> = emptyFlow()
        override fun observeVideos(filter: VideoFilter): Flow<List<IndexedVideo>> = flowOf(emptyList())
        override fun observeTags(channelIds: Set<Long>): Flow<List<TagSummary>> = tags
        override suspend fun refreshVideo(videoKey: VideoKey): VideoReferenceResolution =
            VideoReferenceResolution.Unavailable(VideoReferenceFailure.Unknown)
        override suspend fun getOriginalMessageLink(videoKey: VideoKey): OriginalMessageLinkResult =
            OriginalMessageLinkResult.Unavailable
        override suspend fun setForeground(isForeground: Boolean) = Unit
        override suspend fun refreshSelection() = Unit
        override suspend fun pauseScanning() = Unit
        override suspend fun resumeScanning() = Unit
    }
}
