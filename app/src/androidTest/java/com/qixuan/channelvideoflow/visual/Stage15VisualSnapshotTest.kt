package com.qixuan.channelvideoflow.visual

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.qixuan.channelvideoflow.MainActivity
import com.qixuan.channelvideoflow.domain.cache.MediaCacheState
import com.qixuan.channelvideoflow.model.channel.TelegramChatFailure
import com.qixuan.channelvideoflow.feature.auth.LoginScreen
import com.qixuan.channelvideoflow.feature.auth.LoginStep
import com.qixuan.channelvideoflow.feature.auth.LoginUiState
import com.qixuan.channelvideoflow.feature.channels.ChannelListPhase
import com.qixuan.channelvideoflow.feature.channels.ChannelSelectionItem
import com.qixuan.channelvideoflow.feature.channels.ChannelSelectionScreen
import com.qixuan.channelvideoflow.feature.channels.ChannelSelectionUiState
import com.qixuan.channelvideoflow.feature.settings.CacheSettingsScreen
import com.qixuan.channelvideoflow.feature.settings.CacheSettingsUiState
import com.qixuan.channelvideoflow.feature.tags.TagFilterItem
import com.qixuan.channelvideoflow.feature.tags.TagFilterScreen
import com.qixuan.channelvideoflow.feature.tags.TagFilterUiState
import com.qixuan.channelvideoflow.feature.video.FeedVideoItem
import com.qixuan.channelvideoflow.feature.video.VideoFeedPhase
import com.qixuan.channelvideoflow.feature.video.VideoFeedTestTags
import com.qixuan.channelvideoflow.feature.video.VideoPlaybackScreen
import com.qixuan.channelvideoflow.feature.video.VideoPlaybackUiState
import com.qixuan.channelvideoflow.model.video.IndexedVideo
import com.qixuan.channelvideoflow.model.video.TagFilterMode
import com.qixuan.channelvideoflow.model.video.TagSummary
import com.qixuan.channelvideoflow.model.video.VideoFeedOrder
import com.qixuan.channelvideoflow.model.video.VideoKey
import com.qixuan.channelvideoflow.model.video.VideoQualityPreference
import com.qixuan.channelvideoflow.model.video.VideoScanStatus
import com.qixuan.channelvideoflow.model.video.VideoTag
import com.qixuan.channelvideoflow.player.VideoPlaybackFailure
import com.qixuan.channelvideoflow.player.VideoPlaybackSpeeds
import com.qixuan.channelvideoflow.player.VideoPlaybackState
import com.qixuan.channelvideoflow.player.VideoPlayerSnapshot
import com.qixuan.channelvideoflow.ui.theme.ChannelVideoFlowTheme
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test-only visual evidence generator. It renders production composables with inert local state;
 * no demo route, synthetic Telegram source, or screenshot dependency is shipped in the app APK.
 */
@RunWith(AndroidJUnit4::class)
@UnstableApi
class Stage15VisualSnapshotTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun capturesAuthenticationAndResponsiveStates() {
        setThemedContent(darkTheme = false) {
            LoginScreen(
                uiState = LoginUiState(step = LoginStep.PHONE_NUMBER),
                onInputChanged = {},
                onSubmit = {},
                onResendCode = {},
                onRetry = {},
                onLogout = {},
            )
        }
        capture("01-login-light.png")

        setThemedContent(darkTheme = true) {
            LoginScreen(
                uiState = LoginUiState(step = LoginStep.PHONE_NUMBER, input = "+86 138 •••• 0000"),
                onInputChanged = {},
                onSubmit = {},
                onResendCode = {},
                onRetry = {},
                onLogout = {},
            )
        }
        capture("02-login-dark.png")

        setThemedContent(darkTheme = false, widthDp = 320) {
            ChannelSelectionScreen(
                uiState = channelState(),
                onSearchQueryChanged = {},
                onToggleChannel = {},
                onSave = {},
                onRetry = {},
                onLogout = {},
                logoutEnabled = true,
            )
        }
        capture("03-channels-narrow-320dp.png")
    }

    @Test
    fun capturesChannelAndTagSurfaces() {
        setThemedContent(darkTheme = false) {
            ChannelSelectionScreen(
                uiState = channelState(),
                onSearchQueryChanged = {},
                onToggleChannel = {},
                onSave = {},
                onRetry = {},
                onLogout = {},
                logoutEnabled = true,
            )
        }
        capture("04-channels-light.png")

        setThemedContent(darkTheme = true) {
            TagFilterScreen(
                uiState = tagState(),
                onBack = {},
                onTagToggle = {},
                onModeChanged = {},
                onClearSelection = {},
                onContinue = {},
            )
        }
        capture("05-tags-dark-long-labels.png")

        setThemedContent(darkTheme = false) {
            ChannelSelectionScreen(
                uiState = channelState().copy(
                    searchQuery = "电影",
                    channels = channelState().channels.filter { it.title.contains("电影") },
                ),
                onSearchQueryChanged = {},
                onToggleChannel = {},
                onSave = {},
                onRetry = {},
                onLogout = {},
                logoutEnabled = true,
            )
        }
        capture("16-channels-search.png")

        setThemedContent(darkTheme = false) {
            ChannelSelectionScreen(
                uiState = ChannelSelectionUiState(phase = ChannelListPhase.EMPTY),
                onSearchQueryChanged = {},
                onToggleChannel = {},
                onSave = {},
                onRetry = {},
                onLogout = {},
                logoutEnabled = true,
            )
        }
        capture("17-channels-empty.png")

        setThemedContent(darkTheme = true) {
            ChannelSelectionScreen(
                uiState = ChannelSelectionUiState(
                    phase = ChannelListPhase.ERROR,
                    failure = TelegramChatFailure.NetworkUnavailable,
                ),
                onSearchQueryChanged = {},
                onToggleChannel = {},
                onSave = {},
                onRetry = {},
                onLogout = {},
                logoutEnabled = true,
            )
        }
        capture("18-channels-error-dark.png")
    }

    @Test
    fun capturesFeedLoadingPlaybackAndTerminalStates() {
        val video = visualVideo()
        var state by mutableStateOf(loadingState(video))
        composeRule.mainClock.autoAdvance = false
        setThemedContent(darkTheme = true) {
            FeedSnapshot(state)
        }
        // The clock is paused so the immediate poster can be captured before the
        // delayed disclosure; two frames are enough for Pager's subcomposition.
        composeRule.mainClock.advanceTimeBy(32L)
        composeRule.waitForIdle()
        captureFullScreen("06-feed-loading-immediate.png")
        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(VideoFeedTestTags.LoadingPoster).assertIsDisplayed()
        captureFullScreen("07-feed-loading-delayed.png")
        composeRule.mainClock.autoAdvance = true

        composeRule.runOnIdle { state = readyState(video) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(VideoFeedTestTags.Metadata).assertIsDisplayed()
        captureFullScreen("08-feed-first-frame.png")

        composeRule.runOnIdle {
            state = readyState(video).copy(player = readyPlayer(video, isPaused = true))
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(VideoFeedTestTags.PausedOverlay).assertIsDisplayed()
        captureFullScreen("09-feed-paused.png")

        composeRule.runOnIdle {
            state = readyState(video).copy(
                player = readyPlayer(
                    video = video,
                    playbackSpeed = VideoPlaybackSpeeds.TEMPORARY_FAST_FORWARD,
                ),
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(VideoFeedTestTags.TemporarySpeed).assertIsDisplayed()
        captureFullScreen("10-feed-2x.png")

        composeRule.runOnIdle {
            state = loadingState(video).copy(
                player = VideoPlayerSnapshot(
                    playbackState = VideoPlaybackState.Failed(
                        video = video,
                        reason = VideoPlaybackFailure.NETWORK,
                    ),
                ),
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(VideoFeedTestTags.Retry).assertIsDisplayed()
        captureFullScreen("11-feed-failed.png")

        val unsupported = video.copy(
            key = VideoKey(chatId = 42L, messageId = 74L),
            fileId = 102,
            remoteUniqueId = "visual-unsupported",
            supportsStreaming = false,
        )
        composeRule.runOnIdle {
            state = VideoPlaybackUiState(
                phase = VideoFeedPhase.CONTENT,
                items = listOf(FeedVideoItem(unsupported, "不支持流式的频道")),
                order = VideoFeedOrder.LATEST,
                player = VideoPlayerSnapshot(VideoPlaybackState.Unsupported(unsupported)),
            )
        }
        composeRule.waitForIdle()
        captureFullScreen("12-feed-unsupported.png")

        composeRule.runOnIdle { state = readyState(video) }
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithTag(VideoFeedTestTags.DetailsExpand).performClick()
        // Give the popup composition a frame to mount before advancing its entrance animation.
        composeRule.mainClock.advanceTimeBy(32L)
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(VideoFeedTestTags.DetailsSheet).assertIsDisplayed()
        composeRule.onNodeWithTag(VideoFeedTestTags.DetailsContent).assertIsDisplayed()
        captureNode("13-feed-details-sheet.png", VideoFeedTestTags.DetailsContent)
        composeRule.mainClock.autoAdvance = true
    }

    @Test
    fun capturesSettingsAndLargeFontFallback() {
        val state = CacheSettingsUiState(
            cache = MediaCacheState(
                usedBytes = 188L * 1024L * 1024L,
                limitBytes = 500L * 1024L * 1024L,
                mobileDataPreloadEnabled = false,
                videoQualityPreference = VideoQualityPreference.AUTO,
                isExactUsage = true,
            ),
        )
        setThemedContent(darkTheme = false) {
            SettingsSnapshot(state)
        }
        capture("14-settings-light.png")

        setThemedContent(darkTheme = true, fontScale = 1.35f) {
            SettingsSnapshot(state)
        }
        capture("15-settings-dark-large-font.png")
    }

    @Composable
    private fun FeedSnapshot(state: VideoPlaybackUiState) {
        VideoPlaybackScreen(
            uiState = state,
            onBack = {},
            onLogout = {},
            onRetry = {},
            onTogglePause = {},
            onTemporaryPlaybackSpeedChanged = {},
            onSeek = {},
            onToggleMute = {},
            onOriginalMessage = {},
            onOrderChanged = {},
            onPageUnstable = {},
            onPageTargeted = { _, _ -> },
            onPageSettled = { _, _ -> },
            onAttachPlayer = {},
            onDetachPlayer = {},
        )
    }

    @Composable
    private fun SettingsSnapshot(state: CacheSettingsUiState) {
        CacheSettingsScreen(
            uiState = state,
            onBack = {},
            onLimitSelected = {},
            onMobilePreloadChanged = {},
            onVideoQualitySelected = {},
            onRefresh = {},
            onClear = {},
            onLogout = {},
        )
    }

    private fun setThemedContent(
        darkTheme: Boolean,
        fontScale: Float = 1f,
        widthDp: Int? = null,
        content: @Composable () -> Unit,
    ) {
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                val systemDensity = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(systemDensity.density, fontScale),
                ) {
                    ChannelVideoFlowTheme(darkTheme = darkTheme) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(if (darkTheme) Color(0xFF080B12) else Color(0xFFF4F6FA)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .then(
                                        if (widthDp == null) {
                                            Modifier.fillMaxSize()
                                        } else {
                                            Modifier.width(widthDp.dp).fillMaxHeight()
                                        },
                                    )
                                    .testTag(SNAPSHOT_ROOT),
                            ) {
                                content()
                            }
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun capture(fileName: String) {
        val bitmap = composeRule.onNodeWithTag(SNAPSHOT_ROOT).captureToImage().asAndroidBitmap()
        writeBitmap(fileName, bitmap)
    }

    private fun captureNode(fileName: String, testTag: String) {
        val bitmap = composeRule.onNodeWithTag(testTag).captureToImage().asAndroidBitmap()
        writeBitmap(fileName, bitmap)
    }

    private fun captureFullScreen(fileName: String) {
        // UiAutomation captures the composed window, including the PlayerView SurfaceView.
        // Wait for two real window frames so it cannot observe the previous Compose state.
        val framesDrawn = CountDownLatch(1)
        composeRule.activity.runOnUiThread {
            val decorView = composeRule.activity.window.decorView
            decorView.invalidate()
            decorView.postOnAnimation {
                decorView.invalidate()
                decorView.postOnAnimation {
                    framesDrawn.countDown()
                }
            }
        }
        check(framesDrawn.await(5L, TimeUnit.SECONDS)) {
            "Timed out waiting for the snapshot window to draw"
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        val bitmap = requireNotNull(
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot(),
        )
        writeBitmap(fileName, bitmap)
    }

    private fun writeBitmap(fileName: String, bitmap: Bitmap) {
        val outputDirectory = requireNotNull(
            InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
        ).resolve("stage15-visuals")
        check(outputDirectory.exists() || outputDirectory.mkdirs())
        FileOutputStream(File(outputDirectory, fileName)).use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
    }

    private fun channelState() = ChannelSelectionUiState(
        phase = ChannelListPhase.CONTENT,
        channels = listOf(
            ChannelSelectionItem(-1001L, "城市漫游计划", "urban_walks", true, true, VideoScanStatus.COMPLETED, 812, 37),
            ChannelSelectionItem(-1002L, "独立电影与镜头语言", "indie_cinema", true, false, VideoScanStatus.SCANNING, 240, 18),
            ChannelSelectionItem(-1003L, "设计灵感研究所", "design_labs", false, true, VideoScanStatus.NOT_STARTED),
            ChannelSelectionItem(-1004L, "Weekend / 周末去哪里", null, false, false, VideoScanStatus.NOT_STARTED),
            ChannelSelectionItem(-1005L, "料理与日常", "slow_kitchen", false, false, VideoScanStatus.NOT_STARTED),
        ),
        selectedCount = 2,
        canSave = true,
    )

    private fun tagState() = TagFilterUiState(
        isLoading = false,
        channelIds = setOf(-1001L, -1002L),
        tags = listOf(
            TagFilterItem(TagSummary("citywalk", "#CityWalk", 32), true),
            TagFilterItem(TagSummary("cinematography", "#电影摄影与超长镜头语言研究", 18), false),
            TagFilterItem(TagSummary("weekend", "#周末灵感", 14), true),
            TagFilterItem(TagSummary("architecture", "#Architecture", 11), false),
            TagFilterItem(TagSummary("夜景", "#夜景", 9), false),
        ),
        selectedNames = setOf("citywalk", "weekend"),
        mode = TagFilterMode.OR,
    )

    private fun visualVideo() = IndexedVideo(
        key = VideoKey(chatId = 41L, messageId = 73L),
        fileId = 101,
        remoteUniqueId = "visual-video",
        caption = "黄昏时沿着海边慢慢走，城市的光线刚好落在镜头里。".repeat(18),
        supportsStreaming = true,
        fileSize = 24L * 1024L * 1024L,
        durationSeconds = 86,
        width = 1080,
        height = 1920,
        publishTime = 1_786_903_200L,
        editTime = null,
        canBeSaved = true,
        tags = listOf(
            VideoTag("citywalk", "#CityWalk"),
            VideoTag("夜景", "#夜景"),
        ),
    )

    private fun loadingState(video: IndexedVideo) = VideoPlaybackUiState(
        phase = VideoFeedPhase.CONTENT,
        items = listOf(FeedVideoItem(video, "城市漫游计划")),
        order = VideoFeedOrder.LATEST,
        player = VideoPlayerSnapshot(VideoPlaybackState.Loading(video)),
    )

    private fun readyState(video: IndexedVideo) = loadingState(video).copy(player = readyPlayer(video))

    private fun readyPlayer(
        video: IndexedVideo,
        isPaused: Boolean = false,
        playbackSpeed: Float = VideoPlaybackSpeeds.NORMAL,
    ) = VideoPlayerSnapshot(
        playbackState = VideoPlaybackState.Ready(
            video = video,
            firstReadyWaitMillis = 118L,
            observedLocalBytes = 262_144L,
        ),
        isPaused = isPaused,
        positionMillis = 28_000L,
        durationMillis = 86_000L,
        bufferedPositionMillis = 46_000L,
        isSeekable = true,
        hasRenderedFirstFrame = true,
        isPlaying = !isPaused,
        playbackSpeed = playbackSpeed,
    )

    private companion object {
        const val SNAPSHOT_ROOT = "stage15-snapshot-root"
    }
}
