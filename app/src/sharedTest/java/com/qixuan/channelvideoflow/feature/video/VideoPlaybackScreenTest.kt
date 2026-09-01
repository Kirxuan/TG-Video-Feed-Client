package com.qixuan.channelvideoflow.feature.video

import android.view.ViewConfiguration
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.percentOffset
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.media3.ui.PlayerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qixuan.channelvideoflow.model.video.IndexedVideo
import com.qixuan.channelvideoflow.model.video.VideoFeedOrder
import com.qixuan.channelvideoflow.model.video.VideoKey
import com.qixuan.channelvideoflow.model.video.VideoTag
import com.qixuan.channelvideoflow.player.VideoPlaybackController
import com.qixuan.channelvideoflow.player.VideoPlaybackFailure
import com.qixuan.channelvideoflow.player.VideoPlaybackState
import com.qixuan.channelvideoflow.player.VideoPlaybackSpeeds
import com.qixuan.channelvideoflow.player.VideoPlayerSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VideoPlaybackScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun playbackFailureUsesImmersiveChineseStateAndFakePlayerReceivesRetry() {
        val video = video(supportsStreaming = true)
        val fakePlayer = FakeVideoPlaybackController()
        composeRule.setContent {
            VideoPlaybackScreen(
                uiState = VideoPlaybackUiState(
                    phase = VideoFeedPhase.CONTENT,
                    items = listOf(FeedVideoItem(video, "测试频道")),
                    player = VideoPlayerSnapshot(
                        VideoPlaybackState.Failed(video, VideoPlaybackFailure.DECODER_UNSUPPORTED),
                    ),
                ),
                onBack = {},
                onLogout = {},
                onRetry = fakePlayer::retry,
                onTogglePause = fakePlayer::pause,
                onSeek = fakePlayer::seekTo,
                onToggleMute = { fakePlayer.setMuted(true) },
                onOriginalMessage = {},
                onOrderChanged = {},
                onPageUnstable = fakePlayer::pauseForPageTransition,
                onPageSettled = { _, _ -> },
                onAttachPlayer = fakePlayer::attach,
            )
        }

        composeRule.onNodeWithText("无法播放").assertIsDisplayed()
        composeRule.onNodeWithText("设备不支持该视频编码").assertIsDisplayed()
        composeRule.onNodeWithTag(VideoFeedTestTags.Retry).performClick()

        assertEquals(1, fakePlayer.retryCalls)
    }

    @Test
    fun networkFailureMatchesImmersiveRetryState() {
        val video = video(supportsStreaming = true)
        composeRule.setContent {
            VideoPlaybackScreen(
                uiState = VideoPlaybackUiState(
                    phase = VideoFeedPhase.CONTENT,
                    items = listOf(FeedVideoItem(video, "测试频道")),
                    player = VideoPlayerSnapshot(
                        VideoPlaybackState.Failed(video, VideoPlaybackFailure.NETWORK),
                    ),
                ),
                onBack = {},
                onLogout = {},
                onRetry = {},
                onTogglePause = {},
                onSeek = {},
                onToggleMute = {},
                onOriginalMessage = {},
                onOrderChanged = {},
                onPageUnstable = {},
                onPageSettled = { _, _ -> },
                onAttachPlayer = {},
            )
        }

        composeRule.onNodeWithText("网络错误").assertIsDisplayed()
        composeRule.onNodeWithText("请检查网络连接后重试").assertIsDisplayed()
        composeRule.onNodeWithText("重试").assertIsDisplayed()
    }

    @Test
    fun unsupportedStreamShowsExactMessageWithoutAttachingPlayer() {
        val video = video(supportsStreaming = false)
        val fakePlayer = FakeVideoPlaybackController()
        composeRule.setContent {
            VideoPlaybackScreen(
                uiState = VideoPlaybackUiState(
                    phase = VideoFeedPhase.CONTENT,
                    items = listOf(FeedVideoItem(video, "测试频道")),
                    player = VideoPlayerSnapshot(VideoPlaybackState.Unsupported(video)),
                ),
                onBack = {},
                onLogout = {},
                onRetry = fakePlayer::retry,
                onTogglePause = fakePlayer::pause,
                onSeek = fakePlayer::seekTo,
                onToggleMute = { fakePlayer.setMuted(true) },
                onOriginalMessage = {},
                onOrderChanged = {},
                onPageUnstable = fakePlayer::pauseForPageTransition,
                onPageSettled = { _, _ -> },
                onAttachPlayer = fakePlayer::attach,
            )
        }

        composeRule.onNodeWithText("该视频暂不支持流式播放。").assertIsDisplayed()
        assertEquals(0, fakePlayer.attachCalls)
    }

    @Test
    fun emptyFeedUsesImmersiveStateAndReturnsToChannelSelection() {
        var backCalls = 0
        composeRule.setContent {
            VideoPlaybackScreen(
                uiState = VideoPlaybackUiState(phase = VideoFeedPhase.EMPTY),
                onBack = { backCalls += 1 },
                onLogout = {},
                onRetry = {},
                onTogglePause = {},
                onSeek = {},
                onToggleMute = {},
                onOriginalMessage = {},
                onOrderChanged = {},
                onPageUnstable = {},
                onPageSettled = { _, _ -> },
                onAttachPlayer = {},
            )
        }

        composeRule.onNodeWithText("暂无可播放视频").assertIsDisplayed()
        composeRule.onNodeWithText("请调整频道选择，或等待视频索引完成").assertIsDisplayed()
        composeRule.onNodeWithTag(VideoFeedTestTags.EmptyAction).performClick()

        assertEquals(1, backCalls)
    }

    @Test
    fun removedCurrentMessageFailureOverridesTheEmptyFeedState() {
        val removed = video(supportsStreaming = true)
        var backCalls = 0
        composeRule.setContent {
            VideoPlaybackScreen(
                uiState = VideoPlaybackUiState(
                    phase = VideoFeedPhase.EMPTY,
                    player = VideoPlayerSnapshot(
                        VideoPlaybackState.Failed(
                            removed,
                            VideoPlaybackFailure.MESSAGE_UNAVAILABLE,
                        ),
                    ),
                ),
                onBack = { backCalls += 1 },
                onLogout = {},
                onRetry = {},
                onTogglePause = {},
                onSeek = {},
                onToggleMute = {},
                onOriginalMessage = {},
                onOrderChanged = {},
                onPageUnstable = {},
                onPageSettled = { _, _ -> },
                onAttachPlayer = {},
            )
        }

        composeRule.onNodeWithText("视频已不可播放").assertIsDisplayed()
        composeRule.onNodeWithText("消息已删除或不再是普通视频").assertIsDisplayed()
        composeRule.onAllNodesWithText("暂无可播放视频").assertCountEquals(0)
        composeRule.onNodeWithTag(VideoFeedTestTags.EmptyAction).performClick()

        assertEquals(1, backCalls)
    }

    @Test
    fun systemBackReturnsToChannelSelection() {
        lateinit var backDispatcher: OnBackPressedDispatcher
        var backCalls = 0
        composeRule.setContent {
            backDispatcher = requireNotNull(LocalOnBackPressedDispatcherOwner.current)
                .onBackPressedDispatcher
            VideoPlaybackScreen(
                uiState = VideoPlaybackUiState(phase = VideoFeedPhase.LOADING),
                onBack = { backCalls += 1 },
                onLogout = {},
                onRetry = {},
                onTogglePause = {},
                onSeek = {},
                onToggleMute = {},
                onOriginalMessage = {},
                onOrderChanged = {},
                onPageUnstable = {},
                onPageSettled = { _, _ -> },
                onAttachPlayer = {},
            )
        }

        composeRule.runOnIdle { backDispatcher.onBackPressed() }

        assertEquals(1, backCalls)
    }

    @Test
    fun initialPlaybackStateShowsRandomSelectedWithoutLatestInterimState() {
        composeRule.setContent {
            VideoPlaybackScreen(
                uiState = VideoPlaybackUiState(),
                onBack = {},
                onLogout = {},
                onRetry = {},
                onTogglePause = {},
                onSeek = {},
                onToggleMute = {},
                onOriginalMessage = {},
                onOrderChanged = {},
                onPageUnstable = {},
                onPageSettled = { _, _ -> },
                onAttachPlayer = {},
            )
        }

        composeRule.onNodeWithTag(VideoFeedTestTags.RandomOrder).assertIsSelected()
        composeRule.onNodeWithTag(VideoFeedTestTags.LatestOrder).assertIsNotSelected()
    }

    @Test
    fun contentUsesCompactTabsTapPauseProgressAndRightSideActions() {
        val video = video(supportsStreaming = true)
        val fakePlayer = FakeVideoPlaybackController()
        var selectedOrder: VideoFeedOrder? = null
        composeRule.setContent {
            VideoPlaybackScreen(
                uiState = VideoPlaybackUiState(
                    phase = VideoFeedPhase.CONTENT,
                    items = listOf(FeedVideoItem(video, "测试频道")),
                    order = VideoFeedOrder.LATEST,
                    player = VideoPlayerSnapshot(
                        VideoPlaybackState.Ready(
                            video = video,
                            firstReadyWaitMillis = null,
                            observedLocalBytes = null,
                        ),
                        positionMillis = 10_000L,
                        durationMillis = 60_000L,
                        bufferedPositionMillis = 30_000L,
                        isSeekable = true,
                        hasRenderedFirstFrame = true,
                    ),
                ),
                onBack = {},
                onLogout = {},
                onRetry = fakePlayer::retry,
                onTogglePause = fakePlayer::pause,
                onSeek = fakePlayer::seekTo,
                onToggleMute = { fakePlayer.setMuted(true) },
                onOriginalMessage = {},
                onOrderChanged = { selectedOrder = it },
                onPageUnstable = fakePlayer::pauseForPageTransition,
                onPageSettled = { _, _ -> },
                onAttachPlayer = fakePlayer::attach,
            )
        }

        composeRule.onNodeWithText("测试频道").assertIsDisplayed()
        composeRule.onNodeWithText("视频说明").assertIsDisplayed()
        composeRule.onNodeWithTag(VideoFeedTestTags.TapSurface).performTouchInput { click() }
        assertEquals(1, fakePlayer.pauseCalls)
        composeRule.onAllNodesWithTag(VideoFeedTestTags.PausedOverlay).assertCountEquals(0)
        composeRule.onNodeWithTag(VideoFeedTestTags.Progress).assertIsDisplayed()
        composeRule.onNodeWithTag(VideoFeedTestTags.Mute).assertIsDisplayed()
        composeRule.onNodeWithTag(VideoFeedTestTags.OriginalLink).assertIsDisplayed()
        composeRule.onNodeWithTag(VideoFeedTestTags.Progress).performTouchInput {
            down(percentOffset(0.05f, 0.5f))
            moveTo(percentOffset(0.90f, 0.5f), delayMillis = 100)
            up()
        }
        assertTrue(fakePlayer.seekCalls.single() > 45_000L)
        composeRule.onNodeWithTag(VideoFeedTestTags.RandomOrder).performClick()

        assertEquals(VideoFeedOrder.RANDOM, selectedOrder)
    }

    @Test
    fun muteAndOriginalMessageEachKeepOneSemanticNodeAndInvokeOneCallback() {
        val video = video(supportsStreaming = true)
        var muteCalls = 0
        var originalMessageCalls = 0
        composeRule.setContent {
            VideoPlaybackScreen(
                uiState = VideoPlaybackUiState(
                    phase = VideoFeedPhase.CONTENT,
                    items = listOf(FeedVideoItem(video, "测试频道")),
                    order = VideoFeedOrder.LATEST,
                    player = VideoPlayerSnapshot(
                        playbackState = VideoPlaybackState.Ready(
                            video = video,
                            firstReadyWaitMillis = null,
                            observedLocalBytes = null,
                        ),
                        hasRenderedFirstFrame = true,
                    ),
                ),
                onBack = {},
                onLogout = {},
                onRetry = {},
                onTogglePause = {},
                onSeek = {},
                onToggleMute = { muteCalls += 1 },
                onOriginalMessage = { originalMessageCalls += 1 },
                onOrderChanged = {},
                onPageUnstable = {},
                onPageSettled = { _, _ -> },
                onAttachPlayer = {},
            )
        }

        composeRule.onAllNodesWithTag(VideoFeedTestTags.Mute).assertCountEquals(1)
        composeRule.onAllNodesWithTag(VideoFeedTestTags.OriginalLink).assertCountEquals(1)
        composeRule.onNodeWithTag(VideoFeedTestTags.Mute).performClick()
        composeRule.onNodeWithTag(VideoFeedTestTags.OriginalLink).performClick()

        composeRule.onAllNodesWithTag(VideoFeedTestTags.Mute).assertCountEquals(1)
        composeRule.onAllNodesWithTag(VideoFeedTestTags.OriginalLink).assertCountEquals(1)
        assertEquals(1, muteCalls)
        assertEquals(1, originalMessageCalls)
    }

    @Test
    fun rapidMuteClicksInvokeExactlyOncePerGesture() {
        val video = video(supportsStreaming = true)
        var muteCalls = 0
        composeRule.setContent {
            VideoPlaybackScreen(
                uiState = VideoPlaybackUiState(
                    phase = VideoFeedPhase.CONTENT,
                    items = listOf(FeedVideoItem(video, "测试频道")),
                    order = VideoFeedOrder.LATEST,
                    player = VideoPlayerSnapshot(
                        playbackState = VideoPlaybackState.Ready(
                            video = video,
                            firstReadyWaitMillis = null,
                            observedLocalBytes = null,
                        ),
                        hasRenderedFirstFrame = true,
                    ),
                ),
                onBack = {},
                onLogout = {},
                onRetry = {},
                onTogglePause = {},
                onSeek = {},
                onToggleMute = { muteCalls += 1 },
                onOriginalMessage = {},
                onOrderChanged = {},
                onPageUnstable = {},
                onPageSettled = { _, _ -> },
                onAttachPlayer = {},
            )
        }

        repeat(3) {
            composeRule.onNodeWithTag(VideoFeedTestTags.Mute).performClick()
        }

        assertEquals(3, muteCalls)
        composeRule.onAllNodesWithTag(VideoFeedTestTags.Mute).assertCountEquals(1)
    }

    @Test
    fun disabledOriginalMessageKeepsOneSemanticNodeAndDoesNotInvokeCallback() {
        val video = video(supportsStreaming = true)
        var originalMessageCalls = 0
        composeRule.setContent {
            VideoPlaybackScreen(
                uiState = VideoPlaybackUiState(
                    phase = VideoFeedPhase.CONTENT,
                    items = listOf(FeedVideoItem(video, "测试频道")),
                    order = VideoFeedOrder.LATEST,
                    player = VideoPlayerSnapshot(
                        playbackState = VideoPlaybackState.Ready(
                            video = video,
                            firstReadyWaitMillis = null,
                            observedLocalBytes = null,
                        ),
                        hasRenderedFirstFrame = true,
                    ),
                    originalMessageLink = OriginalMessageLinkUiState.Loading,
                ),
                onBack = {},
                onLogout = {},
                onRetry = {},
                onTogglePause = {},
                onSeek = {},
                onToggleMute = {},
                onOriginalMessage = { originalMessageCalls += 1 },
                onOrderChanged = {},
                onPageUnstable = {},
                onPageSettled = { _, _ -> },
                onAttachPlayer = {},
            )
        }

        composeRule.onAllNodesWithTag(VideoFeedTestTags.OriginalLink).assertCountEquals(1)
        composeRule.onNodeWithTag(VideoFeedTestTags.OriginalLink).performTouchInput {
            down(center)
            up()
        }

        assertEquals(0, originalMessageCalls)
    }

    @Test
    fun landscapeVideoOffersFullscreenAndFullscreenKeepsOnlyPlaybackControls() {
        val video = video(
            supportsStreaming = true,
            width = 1_920,
            height = 1_080,
        )
        var fullscreenCallback = false
        composeRule.setContent {
            var isFullscreen by remember { mutableStateOf(false) }
            VideoPlaybackScreen(
                uiState = VideoPlaybackUiState(
                    phase = VideoFeedPhase.CONTENT,
                    items = listOf(FeedVideoItem(video, "横屏频道")),
                    player = VideoPlayerSnapshot(
                        playbackState = VideoPlaybackState.Ready(
                            video = video,
                            firstReadyWaitMillis = null,
                            observedLocalBytes = null,
                        ),
                        durationMillis = 60_000L,
                        isSeekable = true,
                        hasRenderedFirstFrame = true,
                    ),
                ),
                onBack = {},
                onLogout = {},
                onRetry = {},
                onTogglePause = {},
                onSeek = {},
                onToggleMute = {},
                onOriginalMessage = {},
                onOrderChanged = {},
                onPageUnstable = {},
                onPageSettled = { _, _ -> },
                onAttachPlayer = {},
                isFullscreen = isFullscreen,
                onFullscreenChanged = {
                    isFullscreen = it
                    fullscreenCallback = it
                },
            )
        }

        composeRule.onNodeWithTag(VideoFeedTestTags.Fullscreen).assertIsDisplayed().performClick()
        assertTrue(fullscreenCallback)
        composeRule.onNodeWithTag(VideoFeedTestTags.ExitFullscreen).assertIsDisplayed()
        composeRule.onAllNodesWithTag(VideoFeedTestTags.Metadata).assertCountEquals(0)
        composeRule.onAllNodesWithTag(VideoFeedTestTags.Fullscreen).assertCountEquals(0)
        composeRule.onNodeWithTag(VideoFeedTestTags.ExitFullscreen).performClick()
        assertTrue(!fullscreenCallback)
        composeRule.onNodeWithTag(VideoFeedTestTags.Metadata).assertIsDisplayed()
    }

    @Test
    fun portraitVideoDoesNotOfferFullscreen() {
        val video = video(
            supportsStreaming = true,
            width = 1_080,
            height = 1_920,
        )
        composeRule.setContent {
            VideoPlaybackScreen(
                uiState = VideoPlaybackUiState(
                    phase = VideoFeedPhase.CONTENT,
                    items = listOf(FeedVideoItem(video, "竖屏频道")),
                    player = VideoPlayerSnapshot(
                        playbackState = VideoPlaybackState.Ready(
                            video = video,
                            firstReadyWaitMillis = null,
                            observedLocalBytes = null,
                        ),
                        hasRenderedFirstFrame = true,
                    ),
                ),
                onBack = {},
                onLogout = {},
                onRetry = {},
                onTogglePause = {},
                onSeek = {},
                onToggleMute = {},
                onOriginalMessage = {},
                onOrderChanged = {},
                onPageUnstable = {},
                onPageSettled = { _, _ -> },
                onAttachPlayer = {},
            )
        }

        composeRule.onAllNodesWithTag(VideoFeedTestTags.Fullscreen).assertCountEquals(0)
    }

    @Test
    fun pausedVideoShowsFaintCenterResumeControl() {
        val video = video(supportsStreaming = true)
        var resumeCalls = 0
        composeRule.setContent {
            VideoPlaybackScreen(
                uiState = VideoPlaybackUiState(
                    phase = VideoFeedPhase.CONTENT,
                    items = listOf(FeedVideoItem(video, "测试频道")),
                    player = VideoPlayerSnapshot(
                        playbackState = VideoPlaybackState.Ready(
                            video = video,
                            firstReadyWaitMillis = null,
                            observedLocalBytes = null,
                        ),
                        isPaused = true,
                        positionMillis = 18_000L,
                        durationMillis = 56_000L,
                        isSeekable = true,
                        hasRenderedFirstFrame = true,
                    ),
                ),
                onBack = {},
                onLogout = {},
                onRetry = {},
                onTogglePause = { resumeCalls += 1 },
                onSeek = {},
                onToggleMute = {},
                onOriginalMessage = {},
                onOrderChanged = {},
                onPageUnstable = {},
                onPageSettled = { _, _ -> },
                onAttachPlayer = {},
            )
        }

        composeRule.onNodeWithTag(VideoFeedTestTags.PausedOverlay).assertIsDisplayed().performClick()
        assertEquals(1, resumeCalls)
    }

    @Test
    fun realFirstFrameShowsOneNonClickableSwipeHint() {
        val video = video(supportsStreaming = true)
        composeRule.setContent {
            VideoPlaybackScreen(
                uiState = VideoPlaybackUiState(
                    phase = VideoFeedPhase.CONTENT,
                    items = listOf(FeedVideoItem(video, "测试频道")),
                    player = VideoPlayerSnapshot(
                        playbackState = VideoPlaybackState.Ready(
                            video = video,
                            firstReadyWaitMillis = null,
                            observedLocalBytes = null,
                        ),
                        hasRenderedFirstFrame = true,
                    ),
                    showSwipeHint = true,
                ),
                onBack = {},
                onLogout = {},
                onRetry = {},
                onTogglePause = {},
                onSeek = {},
                onToggleMute = {},
                onOriginalMessage = {},
                onOrderChanged = {},
                onPageUnstable = {},
                onPageSettled = { _, _ -> },
                onAttachPlayer = {},
            )
        }

        composeRule.onAllNodesWithTag(VideoFeedTestTags.SwipeHint).assertCountEquals(1)
        composeRule.onNodeWithTag(VideoFeedTestTags.SwipeHint).assertHasNoClickAction()
        composeRule.onNodeWithText("上滑浏览下一条").assertIsDisplayed()
    }

    @Test
    fun nonPlayableAndNotYetRenderedStatesNeverShowSwipeHint() {
        val playable = video(supportsStreaming = true)
        val unsupported = video(supportsStreaming = false)
        var uiState by mutableStateOf(VideoPlaybackUiState(showSwipeHint = true))
        composeRule.setContent {
            VideoPlaybackScreen(
                uiState = uiState,
                onBack = {},
                onLogout = {},
                onRetry = {},
                onTogglePause = {},
                onSeek = {},
                onToggleMute = {},
                onOriginalMessage = {},
                onOrderChanged = {},
                onPageUnstable = {},
                onPageSettled = { _, _ -> },
                onAttachPlayer = {},
            )
        }

        composeRule.onAllNodesWithTag(VideoFeedTestTags.SwipeHint).assertCountEquals(0)

        composeRule.runOnIdle {
            uiState = VideoPlaybackUiState(
                phase = VideoFeedPhase.CONTENT,
                items = listOf(FeedVideoItem(playable, "测试频道")),
                player = VideoPlayerSnapshot(
                    playbackState = VideoPlaybackState.Failed(
                        playable,
                        VideoPlaybackFailure.NETWORK,
                    ),
                ),
                showSwipeHint = true,
            )
        }
        composeRule.onAllNodesWithTag(VideoFeedTestTags.SwipeHint).assertCountEquals(0)

        composeRule.runOnIdle {
            uiState = VideoPlaybackUiState(
                phase = VideoFeedPhase.CONTENT,
                items = listOf(FeedVideoItem(unsupported, "测试频道")),
                showSwipeHint = true,
            )
        }
        composeRule.onAllNodesWithTag(VideoFeedTestTags.SwipeHint).assertCountEquals(0)

        composeRule.runOnIdle {
            uiState = VideoPlaybackUiState(
                phase = VideoFeedPhase.CONTENT,
                items = listOf(FeedVideoItem(playable, "测试频道")),
                player = VideoPlayerSnapshot(
                    playbackState = VideoPlaybackState.Ready(
                        video = playable,
                        firstReadyWaitMillis = null,
                        observedLocalBytes = null,
                    ),
                    hasRenderedFirstFrame = false,
                ),
                showSwipeHint = true,
            )
        }
        composeRule.onAllNodesWithTag(VideoFeedTestTags.SwipeHint).assertCountEquals(0)
    }

    @Test
    fun swipeHintDoesNotBlockTapPagerPointerOrProgressDrag() {
        val current = video(supportsStreaming = true)
        var showSwipeHint by mutableStateOf(true)
        var togglePauseCalls = 0
        var pointerDownCalls = 0
        val seekCalls = mutableListOf<Long>()
        composeRule.setContent {
            VideoPlaybackScreen(
                uiState = VideoPlaybackUiState(
                    phase = VideoFeedPhase.CONTENT,
                    items = listOf(FeedVideoItem(current, "测试频道")),
                    player = VideoPlayerSnapshot(
                        playbackState = VideoPlaybackState.Ready(
                            video = current,
                            firstReadyWaitMillis = null,
                            observedLocalBytes = null,
                        ),
                        positionMillis = 10_000L,
                        durationMillis = 60_000L,
                        isSeekable = true,
                        hasRenderedFirstFrame = true,
                    ),
                    showSwipeHint = showSwipeHint,
                ),
                onBack = {},
                onLogout = {},
                onRetry = {},
                onTogglePause = { togglePauseCalls += 1 },
                onSeek = seekCalls::add,
                onToggleMute = {},
                onOriginalMessage = {},
                onOrderChanged = {},
                onPageUnstable = {},
                onPageSettled = { _, _ -> },
                onPagerPointerDown = {
                    pointerDownCalls += 1
                    showSwipeHint = false
                },
                onAttachPlayer = {},
            )
        }

        composeRule.onNodeWithTag(VideoFeedTestTags.TapSurface).performTouchInput { click() }
        assertEquals(1, togglePauseCalls)

        composeRule.runOnIdle { showSwipeHint = true }
        composeRule.onNodeWithTag(VideoFeedTestTags.Pager).performTouchInput { swipeUp() }
        composeRule.onAllNodesWithTag(VideoFeedTestTags.SwipeHint).assertCountEquals(0)
        assertTrue(pointerDownCalls >= 1)

        composeRule.runOnIdle { showSwipeHint = true }
        composeRule.onNodeWithTag(VideoFeedTestTags.Progress).performTouchInput {
            swipe(
                start = Offset(20f, center.y),
                end = Offset(width - 20f, center.y),
                durationMillis = 300L,
            )
        }
        assertTrue(seekCalls.isNotEmpty())
    }

    @Test
    fun fullscreenNeverShowsSwipeHint() {
        val video = video(supportsStreaming = true, width = 1920, height = 1080)
        composeRule.setContent {
            VideoPlaybackScreen(
                uiState = VideoPlaybackUiState(
                    phase = VideoFeedPhase.CONTENT,
                    items = listOf(FeedVideoItem(video, "测试频道")),
                    player = VideoPlayerSnapshot(
                        playbackState = VideoPlaybackState.Ready(
                            video = video,
                            firstReadyWaitMillis = null,
                            observedLocalBytes = null,
                        ),
                        hasRenderedFirstFrame = true,
                    ),
                    showSwipeHint = true,
                ),
                onBack = {},
                onLogout = {},
                onRetry = {},
                onTogglePause = {},
                onSeek = {},
                onToggleMute = {},
                onOriginalMessage = {},
                onOrderChanged = {},
                onPageUnstable = {},
                onPageSettled = { _, _ -> },
                onAttachPlayer = {},
                isFullscreen = true,
            )
        }

        composeRule.onAllNodesWithTag(VideoFeedTestTags.SwipeHint).assertCountEquals(0)
    }

    @Test
    fun exitingPausedOverlayKeepsAnimationWithoutExposingResumeSemantics() {
        val video = video(supportsStreaming = true)
        var player by mutableStateOf(
            VideoPlayerSnapshot(
                playbackState = VideoPlaybackState.Ready(
                    video = video,
                    firstReadyWaitMillis = null,
                    observedLocalBytes = null,
                ),
                isPaused = false,
                hasRenderedFirstFrame = true,
            ),
        )
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            VideoPlaybackScreen(
                uiState = VideoPlaybackUiState(
                    phase = VideoFeedPhase.CONTENT,
                    items = listOf(FeedVideoItem(video, "测试频道")),
                    order = VideoFeedOrder.LATEST,
                    player = player,
                ),
                onBack = {},
                onLogout = {},
                onRetry = {},
                onTogglePause = {},
                onSeek = {},
                onToggleMute = {},
                onOriginalMessage = {},
                onOrderChanged = {},
                onPageUnstable = {},
                onPageSettled = { _, _ -> },
                onAttachPlayer = {},
            )
        }

        composeRule.onAllNodesWithTag(VideoFeedTestTags.PausedOverlay).assertCountEquals(0)
        composeRule.runOnIdle { player = player.copy(isPaused = true) }
        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag(VideoFeedTestTags.PausedOverlay).assertCountEquals(1)
        composeRule.onAllNodesWithContentDescription("继续播放").assertCountEquals(1)
        composeRule.onNodeWithContentDescription("继续播放").assertHasClickAction()
        composeRule.onNodeWithTag(VideoFeedTestTags.PausedOverlay).assertHasClickAction()

        composeRule.runOnIdle { player = player.copy(isPaused = false) }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(150L)
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag(VideoFeedTestTags.PausedOverlay).assertCountEquals(1)
        composeRule.onNodeWithTag(VideoFeedTestTags.PausedOverlay).assertHasNoClickAction()
        composeRule.onAllNodesWithContentDescription("继续播放").assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription("暂停视频").assertCountEquals(1)
        composeRule.onNodeWithContentDescription("暂停视频").assertHasClickAction()
        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule.waitForIdle()

        composeRule.onAllNodesWithTag(VideoFeedTestTags.PausedOverlay).assertCountEquals(0)
    }

    @Test
    fun rapidPauseStateChangesNeverLeaveDuplicateOrStaleOverlayNodes() {
        val video = video(supportsStreaming = true)
        var player by mutableStateOf(
            VideoPlayerSnapshot(
                playbackState = VideoPlaybackState.Ready(
                    video = video,
                    firstReadyWaitMillis = null,
                    observedLocalBytes = null,
                ),
                isPaused = false,
                hasRenderedFirstFrame = true,
            ),
        )
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            VideoPlaybackScreen(
                uiState = VideoPlaybackUiState(
                    phase = VideoFeedPhase.CONTENT,
                    items = listOf(FeedVideoItem(video, "测试频道")),
                    order = VideoFeedOrder.LATEST,
                    player = player,
                ),
                onBack = {},
                onLogout = {},
                onRetry = {},
                onTogglePause = {},
                onSeek = {},
                onToggleMute = {},
                onOriginalMessage = {},
                onOrderChanged = {},
                onPageUnstable = {},
                onPageSettled = { _, _ -> },
                onAttachPlayer = {},
            )
        }

        composeRule.runOnIdle { player = player.copy(isPaused = true) }
        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag(VideoFeedTestTags.PausedOverlay).assertCountEquals(1)

        repeat(3) { toggle ->
            composeRule.runOnIdle {
                player = player.copy(isPaused = toggle % 2 != 0)
            }
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.waitForIdle()
            composeRule.onAllNodesWithTag(VideoFeedTestTags.PausedOverlay)
                .assertCountEquals(1)
        }

        composeRule.runOnIdle { player = player.copy(isPaused = false) }
        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule.waitForIdle()

        composeRule.onAllNodesWithTag(VideoFeedTestTags.PausedOverlay).assertCountEquals(0)
    }

    @Test
    fun shortTapPausesExactlyOnceWithoutRequestingTemporarySpeed() {
        val current = video(supportsStreaming = true)
        var pauseCalls = 0
        val speedChanges = mutableListOf<Boolean>()
        composeRule.setContent {
            VideoPlaybackScreen(
                uiState = playingUiState(current),
                onBack = {},
                onLogout = {},
                onRetry = {},
                onTogglePause = { pauseCalls += 1 },
                onSeek = {},
                onToggleMute = {},
                onOriginalMessage = {},
                onOrderChanged = {},
                onPageUnstable = {},
                onPageSettled = { _, _ -> },
                onTemporaryPlaybackSpeedChanged = speedChanges::add,
                onAttachPlayer = {},
            )
        }

        composeRule.onNodeWithTag(VideoFeedTestTags.TapSurface).performClick()

        assertEquals(1, pauseCalls)
        assertEquals(emptyList<Boolean>(), speedChanges)
    }

    @Test
    fun systemLongPressHoldsTemporarySpeedUntilReleaseWithoutPauseOrReattach() {
        val current = video(supportsStreaming = true)
        var player by mutableStateOf(playingSnapshot(current))
        var pauseCalls = 0
        var attachCalls = 0
        val speedChanges = mutableListOf<Boolean>()
        composeRule.setContent {
            VideoPlaybackScreen(
                uiState = VideoPlaybackUiState(
                    phase = VideoFeedPhase.CONTENT,
                    items = listOf(FeedVideoItem(current, "测试频道")),
                    player = player,
                ),
                onBack = {},
                onLogout = {},
                onRetry = {},
                onTogglePause = { pauseCalls += 1 },
                onSeek = {},
                onToggleMute = {},
                onOriginalMessage = {},
                onOrderChanged = {},
                onPageUnstable = {},
                onPageSettled = { _, _ -> },
                onTemporaryPlaybackSpeedChanged = { active ->
                    speedChanges += active
                    player = player.copy(
                        playbackSpeed = if (active) {
                            VideoPlaybackSpeeds.TEMPORARY_FAST_FORWARD
                        } else {
                            VideoPlaybackSpeeds.NORMAL
                        },
                    )
                },
                onAttachPlayer = { attachCalls += 1 },
            )
        }

        composeRule.onNodeWithTag(VideoFeedTestTags.TapSurface).performTouchInput {
            down(center)
        }
        composeRule.mainClock.advanceTimeBy(ViewConfiguration.getLongPressTimeout().toLong() + 1L)
        composeRule.waitForIdle()

        assertEquals(listOf(true), speedChanges)
        assertEquals(0, pauseCalls)
        assertEquals(1, attachCalls)
        composeRule.onNodeWithTag(VideoFeedTestTags.TemporarySpeed).assertIsDisplayed()

        composeRule.onNodeWithTag(VideoFeedTestTags.TapSurface).performTouchInput { up() }
        composeRule.waitForIdle()

        assertEquals(listOf(true, false), speedChanges)
        assertEquals(0, pauseCalls)
        assertEquals(1, attachCalls)
    }

    @Test
    fun pointerCancelAfterLongPressRestoresNormalSpeed() {
        val current = video(supportsStreaming = true)
        val speedChanges = mutableListOf<Boolean>()
        composeRule.setContent {
            VideoPlaybackScreen(
                uiState = playingUiState(current),
                onBack = {},
                onLogout = {},
                onRetry = {},
                onTogglePause = {},
                onSeek = {},
                onToggleMute = {},
                onOriginalMessage = {},
                onOrderChanged = {},
                onPageUnstable = {},
                onPageSettled = { _, _ -> },
                onTemporaryPlaybackSpeedChanged = speedChanges::add,
                onAttachPlayer = {},
            )
        }

        composeRule.onNodeWithTag(VideoFeedTestTags.TapSurface).performTouchInput {
            down(center)
        }
        composeRule.mainClock.advanceTimeBy(ViewConfiguration.getLongPressTimeout().toLong() + 1L)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(VideoFeedTestTags.TapSurface).performTouchInput { cancel() }
        composeRule.waitForIdle()

        assertEquals(listOf(true, false), speedChanges)
    }

    @Test
    fun leavingCompositionDuringLongPressRestoresNormalSpeed() {
        val current = video(supportsStreaming = true)
        var showScreen by mutableStateOf(true)
        val speedChanges = mutableListOf<Boolean>()
        composeRule.setContent {
            if (showScreen) {
                VideoPlaybackScreen(
                    uiState = playingUiState(current),
                    onBack = {},
                    onLogout = {},
                    onRetry = {},
                    onTogglePause = {},
                    onSeek = {},
                    onToggleMute = {},
                    onOriginalMessage = {},
                    onOrderChanged = {},
                    onPageUnstable = {},
                    onPageSettled = { _, _ -> },
                    onTemporaryPlaybackSpeedChanged = speedChanges::add,
                    onAttachPlayer = {},
                )
            }
        }

        composeRule.onNodeWithTag(VideoFeedTestTags.TapSurface).performTouchInput {
            down(center)
        }
        composeRule.mainClock.advanceTimeBy(ViewConfiguration.getLongPressTimeout().toLong() + 1L)
        composeRule.waitForIdle()
        assertEquals(listOf(true), speedChanges)

        composeRule.runOnIdle { showScreen = false }
        composeRule.waitForIdle()

        assertEquals(listOf(true, false), speedChanges)
    }

    @Test
    fun verticalMoveBeyondSystemSlopLetsPagerSwipeWithoutTemporarySpeed() {
        val first = video(supportsStreaming = true)
        val second = first.copy(key = VideoKey(2L, 2L), fileId = 2)
        val speedChanges = mutableListOf<Boolean>()
        composeRule.setContent {
            VideoPlaybackScreen(
                uiState = playingUiState(first).copy(
                    items = listOf(
                        FeedVideoItem(first, "频道一"),
                        FeedVideoItem(second, "频道二"),
                    ),
                ),
                onBack = {},
                onLogout = {},
                onRetry = {},
                onTogglePause = {},
                onSeek = {},
                onToggleMute = {},
                onOriginalMessage = {},
                onOrderChanged = {},
                onPageUnstable = {},
                onPageSettled = { _, _ -> },
                onTemporaryPlaybackSpeedChanged = speedChanges::add,
                onAttachPlayer = {},
            )
        }

        composeRule.onNodeWithTag(VideoFeedTestTags.TapSurface).performTouchInput {
            down(center)
            moveTo(
                center + Offset(0f, -height * 0.4f),
                delayMillis = ViewConfiguration.getLongPressTimeout().toLong() / 4L,
            )
            advanceEventTime(ViewConfiguration.getLongPressTimeout().toLong())
            up()
        }
        composeRule.waitForIdle()

        assertEquals(emptyList<Boolean>(), speedChanges)
    }

    @Test
    fun actionButtonsAndProgressGesturesNeverActivateTemporarySpeed() {
        val current = video(supportsStreaming = true, width = 1920, height = 1080)
        val speedChanges = mutableListOf<Boolean>()
        composeRule.setContent {
            VideoPlaybackScreen(
                uiState = playingUiState(current),
                onBack = {},
                onLogout = {},
                onRetry = {},
                onTogglePause = {},
                onSeek = {},
                onToggleMute = {},
                onOriginalMessage = {},
                onOrderChanged = {},
                onPageUnstable = {},
                onPageSettled = { _, _ -> },
                onTemporaryPlaybackSpeedChanged = speedChanges::add,
                onAttachPlayer = {},
            )
        }

        composeRule.onNodeWithTag(VideoFeedTestTags.Mute).performClick()
        composeRule.onNodeWithTag(VideoFeedTestTags.OriginalLink).performClick()
        composeRule.onNodeWithTag(VideoFeedTestTags.Fullscreen).performClick()
        composeRule.onNodeWithTag(VideoFeedTestTags.Progress).performTouchInput {
            swipe(
                start = Offset(20f, center.y),
                end = Offset(width - 20f, center.y),
                durationMillis = 300L,
            )
        }

        assertEquals(emptyList<Boolean>(), speedChanges)
    }

    @Test
    fun expandUsesRealVisualOverflowAndNeverLeaksAcrossVideoKeys() {
        val short = video(
            supportsStreaming = true,
            caption = "",
            tags = listOf(VideoTag("short", "#短标签")),
        )
        val longCaption = short.copy(
            key = VideoKey(1L, 2L),
            caption = overflowCaption("受限宽度文案"),
        )
        val anotherShort = short.copy(key = VideoKey(1L, 3L))
        val longTags = short.copy(
            key = VideoKey(1L, 4L),
            caption = "短文案",
            tags = List(8) { index ->
                VideoTag("tag$index", "#标签${index}A\n#标签${index}B")
            },
        )
        var state by mutableStateOf(playingUiState(short))
        composeRule.setContent {
            Box(Modifier.size(width = 320.dp, height = 760.dp)) {
                VideoPlaybackScreen(
                    uiState = state,
                    onBack = {},
                    onLogout = {},
                    onRetry = {},
                    onTogglePause = {},
                    onSeek = {},
                    onToggleMute = {},
                    onOriginalMessage = {},
                    onOrderChanged = {},
                    onPageUnstable = {},
                    onPageSettled = { _, _ -> },
                    onAttachPlayer = {},
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag(VideoFeedTestTags.DetailsExpand).assertCountEquals(0)

        composeRule.runOnIdle { state = playingUiState(longCaption) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(VideoFeedTestTags.DetailsExpand).assertIsDisplayed()

        composeRule.runOnIdle { state = playingUiState(anotherShort) }
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag(VideoFeedTestTags.DetailsExpand).assertCountEquals(0)

        composeRule.runOnIdle { state = playingUiState(longTags) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(VideoFeedTestTags.DetailsExpand).assertIsDisplayed()
    }

    @Test
    fun sameVideoKeyCaptionOverflowClearsWhenCaptionAndTagsBecomeEmpty() {
        val longCaption = video(
            supportsStreaming = true,
            caption = overflowCaption("同一消息的长文案"),
            tags = emptyList(),
        )
        var state by mutableStateOf(playingUiState(longCaption))
        composeRule.setContent {
            Box(Modifier.size(width = 320.dp, height = 760.dp)) {
                VideoPlaybackScreen(
                    uiState = state,
                    onBack = {},
                    onLogout = {},
                    onRetry = {},
                    onTogglePause = {},
                    onSeek = {},
                    onToggleMute = {},
                    onOriginalMessage = {},
                    onOrderChanged = {},
                    onPageUnstable = {},
                    onPageSettled = { _, _ -> },
                    onAttachPlayer = {},
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(VideoFeedTestTags.DetailsExpand).assertIsDisplayed()

        composeRule.runOnIdle {
            state = playingUiState(longCaption.copy(caption = "", tags = emptyList()))
        }
        composeRule.waitForIdle()

        composeRule.onAllNodesWithTag(VideoFeedTestTags.DetailsExpand).assertCountEquals(0)
    }

    @Test
    fun sameVideoKeyTagsOverflowClearsWhenCaptionAndTagsBecomeEmpty() {
        val longTags = video(
            supportsStreaming = true,
            caption = "",
            tags = List(8) { index ->
                VideoTag("tag$index", "#同一消息标签${index}A\n#同一消息标签${index}B")
            },
        )
        var state by mutableStateOf(playingUiState(longTags))
        composeRule.setContent {
            Box(Modifier.size(width = 320.dp, height = 760.dp)) {
                VideoPlaybackScreen(
                    uiState = state,
                    onBack = {},
                    onLogout = {},
                    onRetry = {},
                    onTogglePause = {},
                    onSeek = {},
                    onToggleMute = {},
                    onOriginalMessage = {},
                    onOrderChanged = {},
                    onPageUnstable = {},
                    onPageSettled = { _, _ -> },
                    onAttachPlayer = {},
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(VideoFeedTestTags.DetailsExpand).assertIsDisplayed()

        composeRule.runOnIdle {
            state = playingUiState(longTags.copy(caption = "", tags = emptyList()))
        }
        composeRule.waitForIdle()

        composeRule.onAllNodesWithTag(VideoFeedTestTags.DetailsExpand).assertCountEquals(0)
    }

    @Test
    fun sameVideoKeyOverflowFollowsCurrentContentAfterRelayout() {
        val long = video(
            supportsStreaming = true,
            caption = overflowCaption("重布局前的长内容"),
            tags = emptyList(),
        )
        val short = long.copy(caption = "重布局后的短内容")
        val editedLong = long.copy(caption = overflowCaption("再次编辑后的长内容"))
        var state by mutableStateOf(playingUiState(long))
        composeRule.setContent {
            Box(Modifier.size(width = 320.dp, height = 760.dp)) {
                VideoPlaybackScreen(
                    uiState = state,
                    onBack = {},
                    onLogout = {},
                    onRetry = {},
                    onTogglePause = {},
                    onSeek = {},
                    onToggleMute = {},
                    onOriginalMessage = {},
                    onOrderChanged = {},
                    onPageUnstable = {},
                    onPageSettled = { _, _ -> },
                    onAttachPlayer = {},
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(VideoFeedTestTags.DetailsExpand).assertIsDisplayed()

        composeRule.runOnIdle { state = playingUiState(short) }
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag(VideoFeedTestTags.DetailsExpand).assertCountEquals(0)

        composeRule.runOnIdle { state = playingUiState(editedLong) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(VideoFeedTestTags.DetailsExpand).assertIsDisplayed()
    }

    @Test
    fun editedOverflowStateRemainsIsolatedAcrossVideoKeys() {
        val firstLong = video(
            supportsStreaming = true,
            caption = overflowCaption("第一条消息的长内容"),
            tags = emptyList(),
        )
        val secondShort = firstLong.copy(
            key = VideoKey(2L, 2L),
            fileId = 2,
            caption = "第二条消息的短内容",
        )
        val secondLong = secondShort.copy(caption = overflowCaption("第二条消息编辑后的长内容"))
        val firstShort = firstLong.copy(caption = "第一条消息编辑后的短内容")
        var state by mutableStateOf(playingUiState(firstLong))
        composeRule.setContent {
            Box(Modifier.size(width = 320.dp, height = 760.dp)) {
                VideoPlaybackScreen(
                    uiState = state,
                    onBack = {},
                    onLogout = {},
                    onRetry = {},
                    onTogglePause = {},
                    onSeek = {},
                    onToggleMute = {},
                    onOriginalMessage = {},
                    onOrderChanged = {},
                    onPageUnstable = {},
                    onPageSettled = { _, _ -> },
                    onAttachPlayer = {},
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(VideoFeedTestTags.DetailsExpand).assertIsDisplayed()

        composeRule.runOnIdle { state = playingUiState(secondShort) }
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag(VideoFeedTestTags.DetailsExpand).assertCountEquals(0)

        composeRule.runOnIdle { state = playingUiState(secondLong) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(VideoFeedTestTags.DetailsExpand).assertIsDisplayed()

        composeRule.runOnIdle { state = playingUiState(firstShort) }
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag(VideoFeedTestTags.DetailsExpand).assertCountEquals(0)
    }

    @Test
    fun openDetailsShowsUpdatedContentForTheSameVideoKey() {
        val oldCaption = overflowCaption("更新前文案")
        val oldTags = listOf(VideoTag("old", "#旧标签"))
        val current = video(
            supportsStreaming = true,
            caption = oldCaption,
            tags = oldTags,
        )
        val newCaption = "更新后的完整文案\n保留换行与 emoji 🎬"
        val newTags = listOf(
            VideoTag("new", "#新标签"),
            VideoTag("unicode", "#中文✨"),
        )
        var state by mutableStateOf(playingUiState(current))
        composeRule.setContent {
            VideoPlaybackScreen(
                uiState = state,
                onBack = {},
                onLogout = {},
                onRetry = {},
                onTogglePause = {},
                onSeek = {},
                onToggleMute = {},
                onOriginalMessage = {},
                onOrderChanged = {},
                onPageUnstable = {},
                onPageSettled = { _, _ -> },
                onAttachPlayer = {},
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(VideoFeedTestTags.DetailsExpand).performClick()
        composeRule.onNodeWithTag(VideoFeedTestTags.DetailsCaption).assertTextEquals(oldCaption)

        composeRule.runOnIdle {
            state = playingUiState(current.copy(caption = newCaption, tags = newTags))
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(VideoFeedTestTags.DetailsSheet).assertIsDisplayed()
        composeRule.onNodeWithTag(VideoFeedTestTags.DetailsCaption).assertTextEquals(newCaption)
        composeRule.onNodeWithTag(VideoFeedTestTags.DetailsTags)
            .assertTextEquals("#新标签  #中文✨")
        composeRule.onAllNodesWithText(oldCaption).assertCountEquals(0)
        composeRule.onAllNodesWithText("#旧标签").assertCountEquals(0)
    }

    @Test
    fun captionAndExpandOpenCompleteDetailsWithoutChangingPlayingOrPausedState() {
        val caption = "第一行保留换行\n第二行包含 Unicode 与 emoji：你好，世界 🌏\n" +
            List(8) { line -> "完整长文案第 $line 行。" }.joinToString("\n")
        val tags = listOf(
            VideoTag("first", "#第一个标签"),
            VideoTag("emoji", "#emoji✨"),
            VideoTag("unicode", "#中文标签"),
        )
        val current = video(
            supportsStreaming = true,
            caption = caption,
            tags = tags,
            publishTime = 1_700_000_000L,
        )
        var state by mutableStateOf(playingUiState(current))
        var togglePauseCalls = 0
        val speedChanges = mutableListOf<Boolean>()
        val seekCalls = mutableListOf<Long>()
        var attachCalls = 0
        composeRule.setContent {
            VideoPlaybackScreen(
                uiState = state,
                onBack = {},
                onLogout = {},
                onRetry = {},
                onTogglePause = { togglePauseCalls += 1 },
                onTemporaryPlaybackSpeedChanged = speedChanges::add,
                onSeek = seekCalls::add,
                onToggleMute = {},
                onOriginalMessage = {},
                onOrderChanged = {},
                onPageUnstable = {},
                onPageSettled = { _, _ -> },
                onAttachPlayer = { attachCalls += 1 },
            )
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(caption).performClick()
        composeRule.onNodeWithTag(VideoFeedTestTags.DetailsSheet).assertIsDisplayed()
        composeRule.onNodeWithText("视频详情").assertIsDisplayed()
        composeRule.onNodeWithText("测试频道").assertIsDisplayed()
        composeRule.onNodeWithTag(VideoFeedTestTags.DetailsCaption).assertTextEquals(caption)
        composeRule.onNodeWithTag(VideoFeedTestTags.DetailsTags).assertTextEquals(
            "#第一个标签  #emoji✨  #中文标签",
        )
        composeRule.onNodeWithTag(VideoFeedTestTags.DetailsPublishTime)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag(VideoFeedTestTags.DetailsClose).performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag(VideoFeedTestTags.DetailsSheet).assertCountEquals(0)

        composeRule.runOnIdle {
            state = state.copy(player = state.player.copy(isPlaying = false, isPaused = true))
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(VideoFeedTestTags.DetailsExpand).performClick()
        composeRule.onNodeWithTag(VideoFeedTestTags.DetailsSheet).assertIsDisplayed()
        composeRule.onNodeWithTag(VideoFeedTestTags.DetailsClose).performClick()
        composeRule.waitForIdle()

        assertEquals(0, togglePauseCalls)
        assertEquals(emptyList<Boolean>(), speedChanges)
        assertEquals(emptyList<Long>(), seekCalls)
        assertEquals(1, attachCalls)
        assertTrue(state.player.isPaused)
        assertEquals(false, state.player.isPlaying)
    }

    @Test
    fun firstSystemBackClosesDetailsAndSecondBackLeavesPlaybackPage() {
        val current = video(
            supportsStreaming = true,
            caption = overflowCaption("需要展开的长文案"),
        )
        lateinit var backDispatcher: OnBackPressedDispatcher
        var backCalls = 0
        composeRule.setContent {
            backDispatcher = requireNotNull(LocalOnBackPressedDispatcherOwner.current)
                .onBackPressedDispatcher
            VideoPlaybackScreen(
                uiState = playingUiState(current),
                onBack = { backCalls += 1 },
                onLogout = {},
                onRetry = {},
                onTogglePause = {},
                onSeek = {},
                onToggleMute = {},
                onOriginalMessage = {},
                onOrderChanged = {},
                onPageUnstable = {},
                onPageSettled = { _, _ -> },
                onAttachPlayer = {},
            )
        }
        composeRule.onNodeWithTag(VideoFeedTestTags.DetailsExpand).performClick()

        composeRule.runOnIdle { backDispatcher.onBackPressed() }
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag(VideoFeedTestTags.DetailsSheet).assertCountEquals(0)
        assertEquals(0, backCalls)

        composeRule.runOnIdle { backDispatcher.onBackPressed() }
        assertEquals(1, backCalls)
    }

    @Test
    fun detailsCloseWhenVideoChangesFeedInvalidatesFullscreenStartsOrCompositionLeaves() {
        val first = video(
            supportsStreaming = true,
            caption = overflowCaption("第一条长文案"),
        )
        val second = first.copy(
            key = VideoKey(2L, 2L),
            fileId = 2,
            caption = overflowCaption("第二条长文案"),
        )
        var state by mutableStateOf(playingUiState(first))
        var fullscreen by mutableStateOf(false)
        var showScreen by mutableStateOf(true)
        composeRule.setContent {
            if (showScreen) {
                VideoPlaybackScreen(
                    uiState = state,
                    onBack = {},
                    onLogout = {},
                    onRetry = {},
                    onTogglePause = {},
                    onSeek = {},
                    onToggleMute = {},
                    onOriginalMessage = {},
                    onOrderChanged = {},
                    onPageUnstable = {},
                    onPageSettled = { _, _ -> },
                    onAttachPlayer = {},
                    isFullscreen = fullscreen,
                    onFullscreenChanged = { fullscreen = it },
                )
            }
        }

        composeRule.onNodeWithTag(VideoFeedTestTags.DetailsExpand).performClick()
        composeRule.runOnIdle { state = playingUiState(second) }
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag(VideoFeedTestTags.DetailsSheet).assertCountEquals(0)

        composeRule.onNodeWithTag(VideoFeedTestTags.DetailsExpand).performClick()
        composeRule.runOnIdle { state = state.copy(queueGeneration = 1L) }
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag(VideoFeedTestTags.DetailsSheet).assertCountEquals(0)

        composeRule.onNodeWithTag(VideoFeedTestTags.DetailsExpand).performClick()
        composeRule.runOnIdle { state = state.copy(phase = VideoFeedPhase.EMPTY) }
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag(VideoFeedTestTags.DetailsSheet).assertCountEquals(0)

        composeRule.runOnIdle { state = playingUiState(second) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(VideoFeedTestTags.DetailsExpand).performClick()
        composeRule.runOnIdle { fullscreen = true }
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag(VideoFeedTestTags.DetailsSheet).assertCountEquals(0)

        composeRule.runOnIdle { fullscreen = false }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(VideoFeedTestTags.DetailsExpand).performClick()
        composeRule.runOnIdle { showScreen = false }
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag(VideoFeedTestTags.DetailsSheet).assertCountEquals(0)
    }

    @Test
    fun modalGesturesCannotReachPlaybackPagerProgressOrActionsAndCloseRestoresThem() {
        val current = video(
            supportsStreaming = true,
            width = 1_920,
            height = 1_080,
            caption = overflowCaption("用于手势隔离验证的长文案"),
        )
        var pauseCalls = 0
        val speedChanges = mutableListOf<Boolean>()
        val seekCalls = mutableListOf<Long>()
        var muteCalls = 0
        var originalMessageCalls = 0
        var fullscreenCalls = 0
        var pageUnstableCalls = 0
        var pageSettledCalls = 0
        var pointerDownCalls = 0
        var pointerReleasedCalls = 0
        composeRule.setContent {
            VideoPlaybackScreen(
                uiState = playingUiState(current),
                onBack = {},
                onLogout = {},
                onRetry = {},
                onTogglePause = { pauseCalls += 1 },
                onTemporaryPlaybackSpeedChanged = speedChanges::add,
                onSeek = seekCalls::add,
                onToggleMute = { muteCalls += 1 },
                onOriginalMessage = { originalMessageCalls += 1 },
                onOrderChanged = {},
                onPageUnstable = { pageUnstableCalls += 1 },
                onPageSettled = { _, _ -> pageSettledCalls += 1 },
                onPagerPointerDown = { pointerDownCalls += 1 },
                onPagerPointerReleased = { pointerReleasedCalls += 1 },
                onAttachPlayer = {},
                onFullscreenChanged = { fullscreenCalls += 1 },
            )
        }
        composeRule.onNodeWithTag(VideoFeedTestTags.DetailsExpand).performClick()
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            pauseCalls = 0
            speedChanges.clear()
            seekCalls.clear()
            muteCalls = 0
            originalMessageCalls = 0
            fullscreenCalls = 0
            pageUnstableCalls = 0
            pageSettledCalls = 0
            pointerDownCalls = 0
            pointerReleasedCalls = 0
        }

        composeRule.onAllNodesWithTag(VideoFeedTestTags.TapSurface).assertCountEquals(0)
        composeRule.onAllNodesWithTag(VideoFeedTestTags.Mute).assertCountEquals(0)
        composeRule.onAllNodesWithTag(VideoFeedTestTags.OriginalLink).assertCountEquals(0)
        composeRule.onAllNodesWithTag(VideoFeedTestTags.Progress).assertCountEquals(0)
        composeRule.onAllNodesWithTag(VideoFeedTestTags.Pager).assertCountEquals(0)
        composeRule.onNodeWithTag(VideoFeedTestTags.DetailsContent).performTouchInput {
            click(center)
            swipeUp(durationMillis = 500L)
        }
        composeRule.waitForIdle()

        assertEquals(0, pauseCalls)
        assertEquals(emptyList<Boolean>(), speedChanges)
        assertEquals(emptyList<Long>(), seekCalls)
        assertEquals(0, muteCalls)
        assertEquals(0, originalMessageCalls)
        assertEquals(0, fullscreenCalls)
        assertEquals(0, pageUnstableCalls)
        assertEquals(0, pageSettledCalls)
        assertEquals(0, pointerDownCalls)
        assertEquals(0, pointerReleasedCalls)

        composeRule.onNodeWithTag(VideoFeedTestTags.DetailsClose).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(VideoFeedTestTags.TapSurface).assertIsDisplayed()
        composeRule.onNodeWithTag(VideoFeedTestTags.Mute).assertIsDisplayed().performClick()
        composeRule.onNodeWithTag(VideoFeedTestTags.TapSurface).performClick()
        assertEquals(1, muteCalls)
        assertEquals(1, pauseCalls)
    }

    @Test
    fun longDetailsScrollToTheLastFieldOnASmallScreen() {
        val current = video(
            supportsStreaming = true,
            caption = List(80) { line -> "第 $line 行长文案，保留换行与 emoji 🎬" }
                .joinToString("\n") + "\n末尾标记",
            tags = List(12) { index -> VideoTag("tag$index", "#标签$index") },
            publishTime = 1_700_000_000L,
        )
        composeRule.setContent {
            Box(Modifier.size(width = 320.dp, height = 420.dp)) {
                VideoPlaybackScreen(
                    uiState = playingUiState(current),
                    onBack = {},
                    onLogout = {},
                    onRetry = {},
                    onTogglePause = {},
                    onSeek = {},
                    onToggleMute = {},
                    onOriginalMessage = {},
                    onOrderChanged = {},
                    onPageUnstable = {},
                    onPageSettled = { _, _ -> },
                    onAttachPlayer = {},
                )
            }
        }

        composeRule.onNodeWithTag(VideoFeedTestTags.DetailsExpand).performClick()
        composeRule.onNodeWithTag(VideoFeedTestTags.DetailsCaption)
            .assertTextEquals(current.caption)
        composeRule.onNodeWithTag(VideoFeedTestTags.DetailsPublishTime)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun detailsHideMissingFieldsWithoutNullOrEmptyContainers() {
        val current = video(
            supportsStreaming = true,
            caption = "",
            tags = List(8) { index ->
                VideoTag("tag$index", "#很长标签${index}A\n#很长标签${index}B")
            },
            publishTime = 0L,
        )
        composeRule.setContent {
            Box(Modifier.size(width = 260.dp, height = 760.dp)) {
                VideoPlaybackScreen(
                    uiState = playingUiState(current).copy(
                        items = listOf(FeedVideoItem(current, "")),
                    ),
                    onBack = {},
                    onLogout = {},
                    onRetry = {},
                    onTogglePause = {},
                    onSeek = {},
                    onToggleMute = {},
                    onOriginalMessage = {},
                    onOrderChanged = {},
                    onPageUnstable = {},
                    onPageSettled = { _, _ -> },
                    onAttachPlayer = {},
                )
            }
        }

        composeRule.onNodeWithTag(VideoFeedTestTags.DetailsExpand).performClick()
        composeRule.onNodeWithText("标签").assertIsDisplayed()
        composeRule.onAllNodesWithText("频道").assertCountEquals(0)
        composeRule.onAllNodesWithText("文案").assertCountEquals(0)
        composeRule.onAllNodesWithText("发布时间").assertCountEquals(0)
        composeRule.onAllNodesWithText("null").assertCountEquals(0)
    }

    @Test
    fun temporarySpeedPromptIsNonClickableAndExitAnimationClearsStaleSemantics() {
        val current = video(supportsStreaming = true)
        var player by mutableStateOf(
            playingSnapshot(current).copy(
                playbackSpeed = VideoPlaybackSpeeds.TEMPORARY_FAST_FORWARD,
            ),
        )
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            VideoPlaybackScreen(
                uiState = VideoPlaybackUiState(
                    phase = VideoFeedPhase.CONTENT,
                    items = listOf(FeedVideoItem(current, "测试频道")),
                    player = player,
                ),
                onBack = {},
                onLogout = {},
                onRetry = {},
                onTogglePause = {},
                onSeek = {},
                onToggleMute = {},
                onOriginalMessage = {},
                onOrderChanged = {},
                onPageUnstable = {},
                onPageSettled = { _, _ -> },
                onTemporaryPlaybackSpeedChanged = {},
                onAttachPlayer = {},
            )
        }
        composeRule.mainClock.advanceTimeBy(500L)

        composeRule.onNodeWithTag(VideoFeedTestTags.TemporarySpeed)
            .assertIsDisplayed()
            .assertHasNoClickAction()
        composeRule.onNodeWithContentDescription("2× 快进中").assertIsDisplayed()

        composeRule.runOnIdle {
            player = player.copy(playbackSpeed = VideoPlaybackSpeeds.NORMAL)
        }
        composeRule.mainClock.advanceTimeByFrame()

        composeRule.onAllNodesWithContentDescription("2× 快进中").assertCountEquals(0)
        composeRule.onAllNodesWithText("2× 快进中").assertCountEquals(0)

        composeRule.mainClock.advanceTimeBy(500L)
        composeRule.onAllNodesWithTag(VideoFeedTestTags.TemporarySpeed).assertCountEquals(0)
    }

    @Test
    fun positionTickerOnlyRecomposesProgressAndNeverReattachesPlayerView() {
        val current = video(supportsStreaming = true)
        val progress = MutableStateFlow(
            VideoPlaybackProgressUiState(
                key = current.key,
                positionMillis = 1_000L,
                durationMillis = 60_000L,
                bufferedPositionMillis = 10_000L,
                isSeekable = true,
            ),
        )
        val fakePlayer = FakeVideoPlaybackController()
        var pagerCompositions = 0
        composeRule.setContent {
            VideoPlaybackScreen(
                uiState = VideoPlaybackUiState(
                    phase = VideoFeedPhase.CONTENT,
                    items = listOf(FeedVideoItem(current, "测试频道")),
                    player = VideoPlayerSnapshot(
                        playbackState = VideoPlaybackState.Ready(
                            current,
                            firstReadyWaitMillis = null,
                            observedLocalBytes = null,
                        ),
                        hasRenderedFirstFrame = true,
                    ),
                    showSwipeHint = true,
                ),
                playbackProgress = progress,
                onBack = {},
                onLogout = {},
                onRetry = {},
                onTogglePause = {},
                onSeek = {},
                onToggleMute = {},
                onOriginalMessage = {},
                onOrderChanged = {},
                onPageUnstable = {},
                onPageSettled = { _, _ -> },
                onAttachPlayer = fakePlayer::attach,
                onPagerComposed = { pagerCompositions += 1 },
            )
        }
        composeRule.waitForIdle()
        val initialPagerCompositions = pagerCompositions

        repeat(12) { tick ->
            composeRule.runOnIdle {
                progress.value = progress.value.copy(positionMillis = (tick + 2) * 1_000L)
            }
            composeRule.waitForIdle()
        }

        assertEquals(initialPagerCompositions, pagerCompositions)
        assertEquals(1, fakePlayer.attachCalls)
        composeRule.onNodeWithTag(VideoFeedTestTags.Progress).assertIsDisplayed()
        composeRule.onAllNodesWithTag(VideoFeedTestTags.SwipeHint).assertCountEquals(1)
    }

    @Test
    fun slowDragBelowSnapMidpointNeverPublishesThePredictedNextPageAsCommittedTarget() {
        val first = video(supportsStreaming = true)
        val second = first.copy(key = VideoKey(chatId = 1L, messageId = 2L))
        val targetedPages = mutableListOf<Pair<Int, Int>>()
        composeRule.setContent {
            VideoPlaybackScreen(
                uiState = VideoPlaybackUiState(
                    phase = VideoFeedPhase.CONTENT,
                    items = listOf(
                        FeedVideoItem(first, "第一页"),
                        FeedVideoItem(second, "第二页"),
                    ),
                ),
                onBack = {},
                onLogout = {},
                onRetry = {},
                onTogglePause = {},
                onSeek = {},
                onToggleMute = {},
                onOriginalMessage = {},
                onOrderChanged = {},
                onPageUnstable = {},
                onPageTargeted = { pagerPage, logicalPage ->
                    targetedPages += pagerPage to logicalPage
                },
                onPageSettled = { _, _ -> },
                onAttachPlayer = {},
            )
        }
        composeRule.waitForIdle()
        targetedPages.clear()

        composeRule.onNodeWithTag(VideoFeedTestTags.Pager).performTouchInput {
            swipe(
                start = center,
                end = center + Offset(0f, -center.y * 0.24f),
                durationMillis = 1_200L,
            )
        }
        composeRule.waitForIdle()

        assertTrue(targetedPages.none { (_, logicalPage) -> logicalPage == 1 })
    }

    @Test
    fun predictedTargetRequiresCurrentPageToCrossTheSnapMidpoint() {
        assertEquals(
            10,
            committedPagerTargetPage(currentPage = 10, predictedTargetPage = 11),
        )
        assertEquals(
            11,
            committedPagerTargetPage(currentPage = 11, predictedTargetPage = 11),
        )
        assertEquals(
            10,
            committedPagerTargetPage(currentPage = 10, predictedTargetPage = 9),
        )
        assertEquals(
            9,
            committedPagerTargetPage(currentPage = 9, predictedTargetPage = 9),
        )
    }

    @Test
    fun randomPagerBoundaryMapsToPregeneratedUpcomingItemWithUniqueVirtualKey() {
        val currentFirst = FeedVideoItem(
            video(supportsStreaming = true).copy(key = VideoKey(1, 1)),
            "当前轮",
        )
        val currentLast = FeedVideoItem(
            video(supportsStreaming = true).copy(key = VideoKey(1, 2)),
            "当前轮",
        )
        val upcomingFirst = FeedVideoItem(
            video(supportsStreaming = true).copy(key = VideoKey(1, 3)),
            "下一轮",
        )
        val state = VideoPlaybackUiState(
            phase = VideoFeedPhase.CONTENT,
            items = listOf(currentFirst, currentLast),
            upcomingItems = listOf(upcomingFirst, currentFirst, currentLast),
            order = VideoFeedOrder.RANDOM,
            randomRoundStartPagerPage = 100,
        )

        val boundary = requireNotNull(resolvePagerItem(state, pagerPage = 102))

        assertEquals(upcomingFirst.video.key, boundary.item.video.key)
        assertEquals(0, boundary.logicalPage)
        assertTrue(
            pagerItemKey(100, VideoFeedOrder.RANDOM, currentFirst.video.key) !=
                pagerItemKey(103, VideoFeedOrder.RANDOM, currentFirst.video.key),
        )
    }

    @Test
    fun rapidSwipeToUnsupportedAndBackKeepsOneStablePlayerViewThenReturns() {
        val streaming = video(supportsStreaming = true)
        val unsupported = video(supportsStreaming = false).copy(
            key = VideoKey(chatId = 1L, messageId = 2L),
        )
        val fakePlayer = FakeVideoPlaybackController()
        var backCalls = 0
        composeRule.setContent {
            VideoPlaybackScreen(
                uiState = VideoPlaybackUiState(
                    phase = VideoFeedPhase.CONTENT,
                    items = listOf(
                        FeedVideoItem(streaming, "流式频道"),
                        FeedVideoItem(unsupported, "非流式频道"),
                    ),
                    player = VideoPlayerSnapshot(
                        playbackState = VideoPlaybackState.Ready(
                            streaming,
                            firstReadyWaitMillis = null,
                            observedLocalBytes = null,
                        ),
                        hasRenderedFirstFrame = true,
                    ),
                ),
                onBack = { backCalls += 1 },
                onLogout = {},
                onRetry = {},
                onTogglePause = {},
                onSeek = {},
                onToggleMute = {},
                onOriginalMessage = {},
                onOrderChanged = {},
                onPageUnstable = {},
                onPageSettled = { _, _ -> },
                onAttachPlayer = fakePlayer::attach,
                onDetachPlayer = fakePlayer::detach,
            )
        }

        composeRule.onNodeWithTag(VideoFeedTestTags.Pager).performTouchInput {
            swipeUp(durationMillis = 80)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("该视频暂不支持流式播放。").assertIsDisplayed()
        composeRule.onNodeWithTag(VideoFeedTestTags.Pager).performTouchInput {
            swipeDown(durationMillis = 80)
        }
        composeRule.waitForIdle()

        assertEquals(1, fakePlayer.attachCalls)
        assertEquals(0, fakePlayer.detachCalls)
        composeRule.onNodeWithContentDescription("返回频道").performClick()
        assertEquals(1, backCalls)
    }

    @Test
    fun readyWithoutMatchingFirstFrameKeepsOpaqueKeyAlignedPlaceholder() {
        val current = video(supportsStreaming = true)
        val stale = current.copy(key = VideoKey(chatId = 9L, messageId = 9L))
        var state by mutableStateOf(
            VideoPlaybackUiState(
                phase = VideoFeedPhase.CONTENT,
                items = listOf(FeedVideoItem(current, "当前频道")),
                player = VideoPlayerSnapshot(
                    playbackState = VideoPlaybackState.Ready(
                        current,
                        firstReadyWaitMillis = null,
                        observedLocalBytes = null,
                    ),
                    hasRenderedFirstFrame = false,
                ),
            ),
        )
        composeRule.setContent {
            VideoPlaybackScreen(
                uiState = state,
                onBack = {},
                onLogout = {},
                onRetry = {},
                onTogglePause = {},
                onSeek = {},
                onToggleMute = {},
                onOriginalMessage = {},
                onOrderChanged = {},
                onPageUnstable = {},
                onPageSettled = { _, _ -> },
                onAttachPlayer = {},
            )
        }

        composeRule.onNodeWithContentDescription("正在准备视频").assertIsDisplayed()
        composeRule.onAllNodesWithTag(VideoFeedTestTags.Metadata).assertCountEquals(0)
        composeRule.runOnIdle {
            state = state.copy(
                player = VideoPlayerSnapshot(
                    playbackState = VideoPlaybackState.Ready(
                        stale,
                        firstReadyWaitMillis = null,
                        observedLocalBytes = null,
                    ),
                    hasRenderedFirstFrame = true,
                ),
            )
        }
        composeRule.onNodeWithContentDescription("正在准备视频").assertIsDisplayed()
        composeRule.runOnIdle {
            state = state.copy(
                player = VideoPlayerSnapshot(
                    playbackState = VideoPlaybackState.Ready(
                        current,
                        firstReadyWaitMillis = null,
                        observedLocalBytes = null,
                    ),
                    hasRenderedFirstFrame = true,
                ),
            )
        }
        composeRule.onNodeWithTag(VideoFeedTestTags.Metadata).assertIsDisplayed()
    }

    @Test
    fun loadingCurrentItemShowsFullyOpaquePosterAndOnePreparationAnnouncement() {
        val current = video(supportsStreaming = true, width = 1_080, height = 1_920)
        setPlaybackScreen(uiState = { loadingUiState(current) })

        composeRule.onNodeWithTag(VideoFeedTestTags.LoadingPoster)
            .assertIsDisplayed()
            .assertHasNoClickAction()
        composeRule.onAllNodesWithContentDescription("正在准备视频").assertCountEquals(1)
        assertEquals(1f, loadingPosterAlpha())
        assertEquals(current.key.toPosterIdentity(), loadingPosterIdentity())
    }

    @Test
    fun readyWithoutRenderedFirstFrameKeepsOpaquePoster() {
        val current = video(supportsStreaming = true)
        setPlaybackScreen(uiState = {
            loadingUiState(current).copy(
                player = readySnapshot(current, hasRenderedFirstFrame = false),
            )
        })

        composeRule.onNodeWithTag(VideoFeedTestTags.LoadingPoster).assertIsDisplayed()
        assertEquals(1f, loadingPosterAlpha())
        composeRule.onAllNodesWithTag(VideoFeedTestTags.Metadata).assertCountEquals(0)
    }

    @Test
    fun staleReadyFirstFrameCannotRemoveCurrentPoster() {
        val current = video(supportsStreaming = true)
        val stale = current.copy(key = VideoKey(chatId = 99L, messageId = 77L))
        setPlaybackScreen(uiState = {
            loadingUiState(current).copy(
                player = readySnapshot(stale, hasRenderedFirstFrame = true),
            )
        })

        composeRule.onNodeWithTag(VideoFeedTestTags.LoadingPoster).assertIsDisplayed()
        assertEquals(1f, loadingPosterAlpha())
        assertEquals(current.key.toPosterIdentity(), loadingPosterIdentity())
        composeRule.onAllNodesWithTag(VideoFeedTestTags.Metadata).assertCountEquals(0)
    }

    @Test
    fun matchingFirstFrameUsesTheSpecifiedPosterFadeDuration() {
        val current = video(supportsStreaming = true)
        var state by mutableStateOf(loadingUiState(current))
        composeRule.mainClock.autoAdvance = false
        setPlaybackScreen(uiState = { state })

        composeRule.runOnIdle {
            state = state.copy(player = readySnapshot(current, hasRenderedFirstFrame = true))
        }
        composeRule.mainClock.advanceTimeBy(VIDEO_POSTER_FADE_OUT_MILLIS - 32L)
        composeRule.waitForIdle()

        assertEquals(190, VIDEO_POSTER_FADE_OUT_MILLIS)
        composeRule.onNodeWithTag(VideoFeedTestTags.LoadingPoster).assertIsDisplayed()
        assertTrue(loadingPosterAlpha() in 0f..0.999f)

        composeRule.mainClock.advanceTimeBy(64L)
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag(VideoFeedTestTags.LoadingPoster).assertCountEquals(0)
        composeRule.mainClock.autoAdvance = true
    }

    @Test
    fun switchingVideoKeyDuringFadeRestoresANewFullyOpaquePosterImmediately() {
        val first = video(supportsStreaming = true)
        val second = first.copy(
            key = VideoKey(chatId = 2L, messageId = 77L),
            fileId = 2,
        )
        var state by mutableStateOf(loadingUiState(first))
        composeRule.mainClock.autoAdvance = false
        setPlaybackScreen(uiState = { state })

        composeRule.runOnIdle {
            state = state.copy(player = readySnapshot(first, hasRenderedFirstFrame = true))
        }
        composeRule.mainClock.advanceTimeBy(VIDEO_POSTER_FADE_OUT_MILLIS / 2L)
        composeRule.waitForIdle()
        assertTrue(loadingPosterAlpha() < 1f)

        composeRule.runOnIdle {
            state = loadingUiState(second)
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()

        assertEquals(1f, loadingPosterAlpha())
        assertEquals(second.key.toPosterIdentity(), loadingPosterIdentity())
        composeRule.onAllNodesWithTag(VideoFeedTestTags.Metadata).assertCountEquals(0)
        composeRule.mainClock.autoAdvance = true
    }

    @Test
    fun sameVideoKeyKeepsItsPosterPaletteAcrossRecomposition() {
        val current = video(supportsStreaming = true)
        var state by mutableStateOf(loadingUiState(current))
        setPlaybackScreen(uiState = { state })
        val initialPalette = loadingPosterPalette()

        composeRule.runOnIdle {
            state = state.copy(
                items = listOf(
                    FeedVideoItem(
                        current.copy(caption = "同一身份更新后的说明"),
                        "更新后的频道标题",
                    ),
                ),
                player = readySnapshot(current, hasRenderedFirstFrame = false),
            )
        }
        composeRule.waitForIdle()

        assertEquals(initialPalette, loadingPosterPalette())
        assertEquals(videoPosterPaletteIndex(current.key), loadingPosterPalette())
        assertEquals(1f, loadingPosterAlpha())
    }

    @Test
    fun chatIdParticipatesInPosterIdentityWhenMessageIdsMatch() {
        val firstKey = VideoKey(chatId = 1L, messageId = 77L)
        val secondKey = VideoKey(chatId = 2L, messageId = 77L)

        assertNotEquals(firstKey.toPosterIdentity(), secondKey.toPosterIdentity())
        assertNotEquals(
            videoPosterPaletteIndex(firstKey),
            videoPosterPaletteIndex(secondKey),
        )
    }

    @Test
    fun failureAndUnsupportedReplaceLoadingPosterWithoutSuccessFade() {
        val streaming = video(supportsStreaming = true)
        val unsupported = streaming.copy(
            key = VideoKey(chatId = 2L, messageId = 2L),
            supportsStreaming = false,
        )
        var state by mutableStateOf(loadingUiState(streaming))
        composeRule.mainClock.autoAdvance = false
        setPlaybackScreen(uiState = { state })
        composeRule.onNodeWithTag(VideoFeedTestTags.LoadingPoster).assertIsDisplayed()

        composeRule.runOnIdle {
            state = state.copy(
                player = VideoPlayerSnapshot(
                    playbackState = VideoPlaybackState.Failed(
                        streaming,
                        VideoPlaybackFailure.NETWORK,
                    ),
                ),
            )
        }
        composeRule.mainClock.advanceTimeByFrame()
        // VerticalPager applies the page subcomposition update on the following frame.
        // This remains an immediate terminal-state replacement (32 ms), not the
        // 190 ms success-only poster fade.
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag(VideoFeedTestTags.LoadingPoster).assertCountEquals(0)
        composeRule.onNodeWithText("网络错误").assertIsDisplayed()

        composeRule.mainClock.autoAdvance = true
        composeRule.runOnIdle {
            state = VideoPlaybackUiState(
                phase = VideoFeedPhase.CONTENT,
                items = listOf(FeedVideoItem(unsupported, "不支持频道")),
                order = VideoFeedOrder.LATEST,
                player = VideoPlayerSnapshot(VideoPlaybackState.Unsupported(unsupported)),
            )
        }
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag(VideoFeedTestTags.LoadingPoster).assertCountEquals(0)
        composeRule.onNodeWithText("该视频暂不支持流式播放。").assertIsDisplayed()
    }

    @Test
    fun loadingPosterClickOnlyReportsPointerLifecycleAndKeepsTheCurrentPage() {
        val first = video(supportsStreaming = true)
        val second = first.copy(key = VideoKey(chatId = 1L, messageId = 2L), fileId = 2)
        var pauseCalls = 0
        val speedChanges = mutableListOf<Boolean>()
        val seekCalls = mutableListOf<Long>()
        val targetedPages = mutableListOf<Pair<Int, Int>>()
        val settledPages = mutableListOf<Pair<Int, Int>>()
        var pointerDownCalls = 0
        var pointerReleasedCalls = 0
        setPlaybackScreen(
            uiState = {
                loadingUiState(first).copy(
                    items = listOf(
                        FeedVideoItem(first, "第一页"),
                        FeedVideoItem(second, "第二页"),
                    ),
                )
            },
            onTogglePause = { pauseCalls += 1 },
            onTemporaryPlaybackSpeedChanged = speedChanges::add,
            onSeek = seekCalls::add,
            onPageTargeted = { pagerPage, logicalPage ->
                targetedPages += pagerPage to logicalPage
            },
            onPageSettled = { pagerPage, logicalPage ->
                settledPages += pagerPage to logicalPage
            },
            onPagerPointerDown = { pointerDownCalls += 1 },
            onPagerPointerReleased = { pointerReleasedCalls += 1 },
        )
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            pauseCalls = 0
            speedChanges.clear()
            seekCalls.clear()
            targetedPages.clear()
            settledPages.clear()
            pointerDownCalls = 0
            pointerReleasedCalls = 0
        }

        composeRule.onNodeWithTag(VideoFeedTestTags.LoadingPoster).performTouchInput { click() }
        composeRule.waitForIdle()

        assertEquals(0, pauseCalls)
        assertEquals(emptyList<Boolean>(), speedChanges)
        assertEquals(emptyList<Long>(), seekCalls)
        assertEquals(emptyList<Pair<Int, Int>>(), targetedPages)
        assertEquals(emptyList<Pair<Int, Int>>(), settledPages)
        assertTrue(pointerDownCalls > 0)
        assertEquals(pointerDownCalls, pointerReleasedCalls)
        assertEquals(first.key.toPosterIdentity(), loadingPosterIdentity())
        assertEquals(1f, loadingPosterAlpha())
    }

    @Test
    fun loadingPosterLongPressCannotReachPauseTemporarySpeedOrSeek() {
        val current = video(supportsStreaming = true)
        var pauseCalls = 0
        val speedChanges = mutableListOf<Boolean>()
        val seekCalls = mutableListOf<Long>()
        var pointerDownCalls = 0
        var pointerReleasedCalls = 0
        setPlaybackScreen(
            uiState = { loadingUiState(current) },
            onTogglePause = { pauseCalls += 1 },
            onTemporaryPlaybackSpeedChanged = speedChanges::add,
            onSeek = seekCalls::add,
            onPagerPointerDown = { pointerDownCalls += 1 },
            onPagerPointerReleased = { pointerReleasedCalls += 1 },
        )

        composeRule.onNodeWithTag(VideoFeedTestTags.LoadingPoster).performTouchInput {
            down(center)
            advanceEventTime(ViewConfiguration.getLongPressTimeout().toLong() + 1L)
            up()
        }
        composeRule.waitForIdle()

        assertEquals(0, pauseCalls)
        assertEquals(emptyList<Boolean>(), speedChanges)
        assertEquals(emptyList<Long>(), seekCalls)
        assertTrue(pointerDownCalls > 0)
        assertEquals(pointerDownCalls, pointerReleasedCalls)
        assertEquals(current.key.toPosterIdentity(), loadingPosterIdentity())
        assertEquals(1f, loadingPosterAlpha())
    }

    @Test
    fun loadingSwipeUpUsesPagerLifecycleAndShowsTheSecondOpaquePoster() {
        val first = video(supportsStreaming = true)
        val second = first.copy(
            key = VideoKey(chatId = 2L, messageId = first.key.messageId),
            fileId = 2,
        )
        var unstableCalls = 0
        val targetedPages = mutableListOf<Pair<Int, Int>>()
        val settledPages = mutableListOf<Pair<Int, Int>>()
        var pointerDownCalls = 0
        var pointerReleasedCalls = 0
        val fakePlayer = FakeVideoPlaybackController()
        setPlaybackScreen(
            uiState = {
                loadingUiState(first).copy(
                    items = listOf(
                        FeedVideoItem(first, "第一页"),
                        FeedVideoItem(second, "第二页"),
                    ),
                )
            },
            onPageUnstable = { unstableCalls += 1 },
            onPageTargeted = { pagerPage, logicalPage ->
                targetedPages += pagerPage to logicalPage
            },
            onPageSettled = { pagerPage, logicalPage ->
                settledPages += pagerPage to logicalPage
            },
            onPagerPointerDown = { pointerDownCalls += 1 },
            onPagerPointerReleased = { pointerReleasedCalls += 1 },
            onAttachPlayer = fakePlayer::attach,
            onDetachPlayer = fakePlayer::detach,
        )
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            unstableCalls = 0
            targetedPages.clear()
            settledPages.clear()
            pointerDownCalls = 0
            pointerReleasedCalls = 0
        }

        composeRule.onNodeWithTag(VideoFeedTestTags.LoadingPoster).performTouchInput {
            swipeUp(durationMillis = 400L)
        }
        composeRule.waitForIdle()

        assertTrue(unstableCalls > 0)
        assertTrue(targetedPages.any { it == 1 to 1 })
        assertEquals(1 to 1, settledPages.last())
        assertTrue(pointerDownCalls > 0)
        assertEquals(pointerDownCalls, pointerReleasedCalls)
        assertEquals(second.key.toPosterIdentity(), loadingPosterIdentity())
        assertEquals(1f, loadingPosterAlpha())
        assertEquals(1, fakePlayer.attachCalls)
        assertEquals(0, fakePlayer.detachCalls)
    }

    @Test
    fun loadingSwipeDownReturnsToThePreviousOpaquePoster() {
        val first = video(supportsStreaming = true)
        val second = first.copy(key = VideoKey(chatId = 2L, messageId = 2L), fileId = 2)
        val settledPages = mutableListOf<Pair<Int, Int>>()
        var pointerDownCalls = 0
        var pointerReleasedCalls = 0
        setPlaybackScreen(
            uiState = {
                loadingUiState(first).copy(
                    items = listOf(
                        FeedVideoItem(first, "第一页"),
                        FeedVideoItem(second, "第二页"),
                    ),
                )
            },
            onPageSettled = { pagerPage, logicalPage ->
                settledPages += pagerPage to logicalPage
            },
            onPagerPointerDown = { pointerDownCalls += 1 },
            onPagerPointerReleased = { pointerReleasedCalls += 1 },
        )

        composeRule.onNodeWithTag(VideoFeedTestTags.LoadingPoster).performTouchInput {
            swipeUp(durationMillis = 160L)
        }
        composeRule.waitForIdle()
        assertEquals(second.key.toPosterIdentity(), loadingPosterIdentity())
        composeRule.runOnIdle {
            settledPages.clear()
            pointerDownCalls = 0
            pointerReleasedCalls = 0
        }

        composeRule.onNodeWithTag(VideoFeedTestTags.LoadingPoster).performTouchInput {
            swipeDown(durationMillis = 160L)
        }
        composeRule.waitForIdle()

        assertEquals(0 to 0, settledPages.last())
        assertTrue(pointerDownCalls > 0)
        assertEquals(pointerDownCalls, pointerReleasedCalls)
        assertEquals(first.key.toPosterIdentity(), loadingPosterIdentity())
        assertEquals(1f, loadingPosterAlpha())
    }

    @Test
    fun rapidLoadingSwipesKeepOnePlayerViewAndOnlyTheFinalPoster() {
        val videos = List(4) { index ->
            video(supportsStreaming = true).copy(
                key = VideoKey(chatId = 10L + index, messageId = 77L),
                fileId = index + 1,
            )
        }
        val first = videos.first()
        val final = videos.last()
        var state by mutableStateOf(
            loadingUiState(first).copy(
                items = videos.mapIndexed { index, item ->
                    FeedVideoItem(item, "第 ${index + 1} 页")
                },
            ),
        )
        val fakePlayer = FakeVideoPlaybackController()
        setPlaybackScreen(
            uiState = { state },
            onAttachPlayer = fakePlayer::attach,
            onDetachPlayer = fakePlayer::detach,
        )

        composeRule.onNodeWithTag(VideoFeedTestTags.LoadingPoster).performTouchInput {
            repeat(3) {
                swipeUp(durationMillis = 80L)
                advanceEventTime(40L)
            }
        }
        composeRule.waitForIdle()

        assertEquals(final.key.toPosterIdentity(), loadingPosterIdentity())
        assertEquals(1f, loadingPosterAlpha())
        assertEquals(1, fakePlayer.attachCalls)
        assertEquals(0, fakePlayer.detachCalls)

        composeRule.runOnIdle {
            state = state.copy(player = readySnapshot(first, hasRenderedFirstFrame = true))
        }
        composeRule.waitForIdle()

        assertEquals(final.key.toPosterIdentity(), loadingPosterIdentity())
        assertEquals(1f, loadingPosterAlpha())
        assertEquals(1, fakePlayer.attachCalls)
    }

    @Test
    fun loadingDragBelowSnapMidpointKeepsTheOriginalPosterWithoutWrongTarget() {
        val first = video(supportsStreaming = true)
        val second = first.copy(key = VideoKey(chatId = 2L, messageId = 2L), fileId = 2)
        val targetedPages = mutableListOf<Pair<Int, Int>>()
        val settledPages = mutableListOf<Pair<Int, Int>>()
        val fakePlayer = FakeVideoPlaybackController()
        setPlaybackScreen(
            uiState = {
                loadingUiState(first).copy(
                    items = listOf(
                        FeedVideoItem(first, "第一页"),
                        FeedVideoItem(second, "第二页"),
                    ),
                )
            },
            onPageTargeted = { pagerPage, logicalPage ->
                targetedPages += pagerPage to logicalPage
            },
            onPageSettled = { pagerPage, logicalPage ->
                settledPages += pagerPage to logicalPage
            },
            onAttachPlayer = fakePlayer::attach,
        )
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            targetedPages.clear()
            settledPages.clear()
        }

        composeRule.onNodeWithTag(VideoFeedTestTags.LoadingPoster).performTouchInput {
            swipe(
                start = center,
                end = center + Offset(0f, -center.y * 0.24f),
                durationMillis = 1_200L,
            )
        }
        composeRule.waitForIdle()

        assertTrue(targetedPages.none { (_, logicalPage) -> logicalPage == 1 })
        assertTrue(settledPages.none { (_, logicalPage) -> logicalPage == 1 })
        assertEquals(first.key.toPosterIdentity(), loadingPosterIdentity())
        assertEquals(1f, loadingPosterAlpha())
        assertEquals(1, fakePlayer.attachCalls)
    }

    @Test
    fun fullscreenStillPreventsLoadingPagerSwipe() {
        val first = video(supportsStreaming = true, width = 1_920, height = 1_080)
        val second = first.copy(key = VideoKey(chatId = 2L, messageId = 2L), fileId = 2)
        var unstableCalls = 0
        var targetedCalls = 0
        var settledCalls = 0
        var pointerDownCalls = 0
        var pointerReleasedCalls = 0
        setPlaybackScreen(
            uiState = {
                loadingUiState(first).copy(
                    items = listOf(
                        FeedVideoItem(first, "第一页"),
                        FeedVideoItem(second, "第二页"),
                    ),
                )
            },
            onPageUnstable = { unstableCalls += 1 },
            onPageTargeted = { _, _ -> targetedCalls += 1 },
            onPageSettled = { _, _ -> settledCalls += 1 },
            onPagerPointerDown = { pointerDownCalls += 1 },
            onPagerPointerReleased = { pointerReleasedCalls += 1 },
            isFullscreen = true,
        )
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            unstableCalls = 0
            targetedCalls = 0
            settledCalls = 0
            pointerDownCalls = 0
            pointerReleasedCalls = 0
        }

        composeRule.onNodeWithTag(VideoFeedTestTags.LoadingPoster).performTouchInput {
            swipeUp(durationMillis = 400L)
        }
        composeRule.waitForIdle()

        assertEquals(0, unstableCalls)
        assertEquals(0, targetedCalls)
        assertEquals(0, settledCalls)
        assertEquals(0, pointerDownCalls)
        assertEquals(0, pointerReleasedCalls)
        assertEquals(first.key.toPosterIdentity(), loadingPosterIdentity())
        assertEquals(1f, loadingPosterAlpha())
    }

    @Test
    fun controlsAndMetadataStayAbsentBeforeFirstFrame() {
        val current = video(supportsStreaming = true, width = 1_920, height = 1_080)
        setPlaybackScreen(uiState = { loadingUiState(current) })

        listOf(
            VideoFeedTestTags.Metadata,
            VideoFeedTestTags.Mute,
            VideoFeedTestTags.OriginalLink,
            VideoFeedTestTags.Progress,
            VideoFeedTestTags.TapSurface,
            VideoFeedTestTags.DetailsExpand,
        ).forEach { tag ->
            composeRule.onAllNodesWithTag(tag).assertCountEquals(0)
        }
    }

    @Test
    fun matchingFirstFrameRestoresControlsAndExistingDetailsSheet() {
        val current = video(
            supportsStreaming = true,
            caption = overflowCaption("首帧后的详情文案"),
        )
        var state by mutableStateOf(loadingUiState(current))
        setPlaybackScreen(uiState = { state })

        composeRule.runOnIdle {
            state = playingUiState(current).copy(order = VideoFeedOrder.LATEST)
        }
        composeRule.waitForIdle()

        composeRule.onAllNodesWithTag(VideoFeedTestTags.LoadingPoster).assertCountEquals(0)
        composeRule.onNodeWithTag(VideoFeedTestTags.Metadata).assertIsDisplayed()
        composeRule.onNodeWithTag(VideoFeedTestTags.Mute).assertIsDisplayed()
        composeRule.onNodeWithTag(VideoFeedTestTags.OriginalLink).assertIsDisplayed()
        composeRule.onNodeWithTag(VideoFeedTestTags.Progress).assertIsDisplayed()
        composeRule.onNodeWithTag(VideoFeedTestTags.DetailsExpand).performClick()
        composeRule.onNodeWithTag(VideoFeedTestTags.DetailsSheet).assertIsDisplayed()
        composeRule.onNodeWithTag(VideoFeedTestTags.DetailsCaption)
            .assertTextEquals(current.caption)
    }

    @Test
    fun posterAndVideoKeyChangesNeverIncreasePlayerViewAttachments() {
        val first = video(supportsStreaming = true)
        val second = first.copy(key = VideoKey(chatId = 2L, messageId = 2L), fileId = 2)
        val fakePlayer = FakeVideoPlaybackController()
        var state by mutableStateOf(loadingUiState(first))
        setPlaybackScreen(
            uiState = { state },
            onAttachPlayer = fakePlayer::attach,
            onDetachPlayer = fakePlayer::detach,
        )
        composeRule.waitForIdle()

        composeRule.runOnIdle { state = loadingUiState(second) }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            state = state.copy(player = readySnapshot(second, hasRenderedFirstFrame = true))
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle { state = loadingUiState(first) }
        composeRule.waitForIdle()

        assertEquals(1, fakePlayer.attachCalls)
        assertEquals(0, fakePlayer.detachCalls)
        composeRule.onNodeWithTag(VideoFeedTestTags.LoadingPoster).assertIsDisplayed()
    }

    @Test
    fun pagerIdentityUsesChatIdAndMessageIdEvenWhenMessageIdsMatch() {
        val first = pagerItemKey(
            pagerPage = 0,
            order = VideoFeedOrder.LATEST,
            videoKey = VideoKey(chatId = 10L, messageId = 77L),
        )
        val second = pagerItemKey(
            pagerPage = 0,
            order = VideoFeedOrder.LATEST,
            videoKey = VideoKey(chatId = 11L, messageId = 77L),
        )

        assertTrue(first != second)
    }

    private fun setPlaybackScreen(
        uiState: () -> VideoPlaybackUiState,
        onTogglePause: () -> Unit = {},
        onTemporaryPlaybackSpeedChanged: (Boolean) -> Unit = {},
        onSeek: (Long) -> Unit = {},
        onPageUnstable: () -> Unit = {},
        onPageTargeted: (Int, Int) -> Unit = { _, _ -> },
        onPageSettled: (Int, Int) -> Unit = { _, _ -> },
        onPagerPointerDown: (Long) -> Unit = {},
        onPagerPointerReleased: (Long) -> Unit = {},
        onAttachPlayer: (PlayerView) -> Unit = {},
        onDetachPlayer: (PlayerView) -> Unit = {},
        isFullscreen: Boolean = false,
    ) {
        composeRule.setContent {
            VideoPlaybackScreen(
                uiState = uiState(),
                onBack = {},
                onLogout = {},
                onRetry = {},
                onTogglePause = onTogglePause,
                onTemporaryPlaybackSpeedChanged = onTemporaryPlaybackSpeedChanged,
                onSeek = onSeek,
                onToggleMute = {},
                onOriginalMessage = {},
                onOrderChanged = {},
                onPageUnstable = onPageUnstable,
                onPageTargeted = onPageTargeted,
                onPageSettled = onPageSettled,
                onPagerPointerDown = onPagerPointerDown,
                onPagerPointerReleased = onPagerPointerReleased,
                onAttachPlayer = onAttachPlayer,
                onDetachPlayer = onDetachPlayer,
                isFullscreen = isFullscreen,
            )
        }
    }

    private fun loadingUiState(video: IndexedVideo) = VideoPlaybackUiState(
        phase = VideoFeedPhase.CONTENT,
        items = listOf(FeedVideoItem(video, "测试频道")),
        order = VideoFeedOrder.LATEST,
        player = VideoPlayerSnapshot(VideoPlaybackState.Loading(video)),
    )

    private fun readySnapshot(
        video: IndexedVideo,
        hasRenderedFirstFrame: Boolean,
    ) = VideoPlayerSnapshot(
        playbackState = VideoPlaybackState.Ready(
            video = video,
            firstReadyWaitMillis = null,
            observedLocalBytes = null,
        ),
        positionMillis = 10_000L,
        durationMillis = 60_000L,
        bufferedPositionMillis = 20_000L,
        isSeekable = true,
        hasRenderedFirstFrame = hasRenderedFirstFrame,
        isPlaying = hasRenderedFirstFrame,
    )

    private fun loadingPosterAlpha(): Float = composeRule
        .onNodeWithTag(VideoFeedTestTags.LoadingPoster)
        .fetchSemanticsNode()
        .config[LoadingPosterAlphaSemanticsKey]

    private fun loadingPosterPalette(): Int = composeRule
        .onNodeWithTag(VideoFeedTestTags.LoadingPoster)
        .fetchSemanticsNode()
        .config[LoadingPosterPaletteSemanticsKey]

    private fun loadingPosterIdentity(): String = composeRule
        .onNodeWithTag(VideoFeedTestTags.LoadingPoster)
        .fetchSemanticsNode()
        .config[LoadingPosterVideoIdentitySemanticsKey]

    private fun VideoKey.toPosterIdentity(): String = "$chatId:$messageId"

    private fun video(
        supportsStreaming: Boolean,
        width: Int = 1,
        height: Int = 1,
        caption: String = "视频说明",
        tags: List<VideoTag> = emptyList(),
        publishTime: Long = 1L,
    ) = IndexedVideo(
        key = VideoKey(1, 1),
        fileId = 1,
        remoteUniqueId = "remote-1",
        caption = caption,
        supportsStreaming = supportsStreaming,
        fileSize = 1,
        durationSeconds = 1,
        width = width,
        height = height,
        publishTime = publishTime,
        editTime = null,
        canBeSaved = true,
        tags = tags,
    )

    private fun overflowCaption(prefix: String): String =
        List(8) { line -> "$prefix 第 $line 行 🎬" }.joinToString("\n")

    private fun playingUiState(video: IndexedVideo) = VideoPlaybackUiState(
        phase = VideoFeedPhase.CONTENT,
        items = listOf(FeedVideoItem(video, "测试频道")),
        player = playingSnapshot(video),
    )

    private fun playingSnapshot(video: IndexedVideo) = VideoPlayerSnapshot(
        playbackState = VideoPlaybackState.Ready(
            video = video,
            firstReadyWaitMillis = null,
            observedLocalBytes = null,
        ),
        positionMillis = 10_000L,
        durationMillis = 60_000L,
        bufferedPositionMillis = 20_000L,
        isSeekable = true,
        hasRenderedFirstFrame = true,
        isPlaying = true,
    )

    private class FakeVideoPlaybackController : VideoPlaybackController {
        override val snapshot: StateFlow<VideoPlayerSnapshot> = MutableStateFlow(VideoPlayerSnapshot())
        var attachCalls = 0
        var retryCalls = 0
        var pauseCalls = 0
        var detachCalls = 0
        val seekCalls = mutableListOf<Long>()

        override fun attach(playerView: PlayerView) {
            attachCalls += 1
        }
        override fun detach(playerView: PlayerView) {
            detachCalls += 1
        }
        override fun bind(video: IndexedVideo) = Unit
        override fun retry() {
            retryCalls += 1
        }
        override fun pause() {
            pauseCalls += 1
        }
        override fun resume() = Unit
        override fun seekTo(positionMillis: Long) {
            seekCalls += positionMillis
        }
        override fun pauseForPageTransition() = Unit
        override fun setMuted(muted: Boolean) = Unit
        override fun onAppBackgrounded() = Unit
        override fun releaseBinding() = Unit
        override fun release() = Unit
    }
}
