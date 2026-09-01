package com.qixuan.channelvideoflow.visual

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.unit.Density
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.qixuan.channelvideoflow.MainActivity
import com.qixuan.channelvideoflow.domain.cache.MediaCacheState
import com.qixuan.channelvideoflow.feature.auth.LoginScreen
import com.qixuan.channelvideoflow.feature.auth.LoginStep
import com.qixuan.channelvideoflow.feature.auth.LoginUiState
import com.qixuan.channelvideoflow.feature.channels.ChannelListPhase
import com.qixuan.channelvideoflow.feature.channels.ChannelScanSummary
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
import com.qixuan.channelvideoflow.feature.video.VideoPlaybackScreen
import com.qixuan.channelvideoflow.feature.video.VideoPlaybackUiState
import com.qixuan.channelvideoflow.model.video.IndexedVideo
import com.qixuan.channelvideoflow.model.video.TagFilterMode
import com.qixuan.channelvideoflow.model.video.TagSummary
import com.qixuan.channelvideoflow.model.video.VideoFeedOrder
import com.qixuan.channelvideoflow.model.video.VideoKey
import com.qixuan.channelvideoflow.model.video.VideoQualityPreference
import com.qixuan.channelvideoflow.model.video.VideoScanStatus
import com.qixuan.channelvideoflow.player.VideoPlaybackState
import com.qixuan.channelvideoflow.player.VideoPlayerSnapshot
import com.qixuan.channelvideoflow.ui.theme.ChannelVideoFlowTheme
import java.io.File
import java.io.FileOutputStream
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Stage 19 after-snapshots. Production composables only; all data is inert local fixture data. */
@RunWith(AndroidJUnit4::class)
@UnstableApi
class Stage19VisualSnapshotTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun capturesFivePolishedSurfacesAndResponsiveSettings() {
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
        capture("01-login-stepper-light.png")

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
        capture("02-channels-selected-light.png")

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
        capture("03-tags-shared-indicator-dark.png")

        setThemedContent(darkTheme = false) {
            CacheSettingsScreen(
                uiState = settingsState(),
                onBack = {},
                onLimitSelected = {},
                onMobilePreloadChanged = {},
                onVideoQualitySelected = {},
                onRefresh = {},
                onClear = {},
            )
        }
        capture("04-settings-capacity-light.png")

        setThemedContent(darkTheme = true) {
            VideoPlaybackScreen(
                uiState = unsupportedVideoState(),
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
        capture("05-video-unsupported-safe-controls.png")

        setThemedContent(darkTheme = true, fontScale = 1.35f) {
            CacheSettingsScreen(
                uiState = settingsState(),
                onBack = {},
                onLimitSelected = {},
                onMobilePreloadChanged = {},
                onVideoQualitySelected = {},
                onRefresh = {},
                onClear = {},
            )
        }
        capture("06-settings-font135-dark.png")
    }

    private fun setThemedContent(
        darkTheme: Boolean,
        fontScale: Float = 1f,
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
                                .background(if (darkTheme) Color(0xFF080B12) else Color(0xFFF4F6FA))
                                .testTag(SNAPSHOT_ROOT),
                        ) {
                            content()
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

    private fun writeBitmap(fileName: String, bitmap: Bitmap) {
        val outputDirectory = requireNotNull(
            InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
        ).resolve("stage19-visuals")
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
        ),
        selectedCount = 2,
        canSave = true,
        scanSummary = ChannelScanSummary(
            processedVideoCandidateCount = 1_052,
            videoSearchPageCount = 11,
            indexedVideoCount = 55,
            completedChannelCount = 1,
            totalChannelCount = 2,
            canControl = true,
        ),
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
        totalTagCount = 5,
        selectedNames = setOf("citywalk", "weekend"),
        mode = TagFilterMode.OR,
    )

    private fun settingsState() = CacheSettingsUiState(
        cache = MediaCacheState(
            usedBytes = 188L * 1024L * 1024L,
            limitBytes = 500L * 1024L * 1024L,
            mobileDataPreloadEnabled = false,
            videoQualityPreference = VideoQualityPreference.AUTO,
            isExactUsage = true,
        ),
    )

    private fun unsupportedVideoState(): VideoPlaybackUiState {
        val video = IndexedVideo(
            key = VideoKey(chatId = 42L, messageId = 74L),
            fileId = 102,
            remoteUniqueId = "stage19-unsupported",
            caption = "该条目用于验证 Android 16 全屏安全区与不支持流式播放状态。",
            supportsStreaming = false,
            fileSize = 18L * 1024L * 1024L,
            durationSeconds = 48,
            width = 1080,
            height = 1920,
            publishTime = 1_786_903_200L,
            editTime = null,
            canBeSaved = false,
            tags = emptyList(),
        )
        return VideoPlaybackUiState(
            phase = VideoFeedPhase.CONTENT,
            items = listOf(FeedVideoItem(video, "城市漫游计划")),
            order = VideoFeedOrder.LATEST,
            player = VideoPlayerSnapshot(VideoPlaybackState.Unsupported(video)),
        )
    }

    private companion object {
        const val SNAPSHOT_ROOT = "stage19-snapshot-root"
    }
}
