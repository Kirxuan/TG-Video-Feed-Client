package com.qixuan.channelvideoflow.visual

import android.graphics.Bitmap
import android.view.WindowInsets
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.qixuan.channelvideoflow.MainActivity
import com.qixuan.channelvideoflow.feature.channels.ChannelListPhase
import com.qixuan.channelvideoflow.feature.channels.ChannelScanSummary
import com.qixuan.channelvideoflow.feature.channels.ChannelSelectionItem
import com.qixuan.channelvideoflow.feature.channels.ChannelSelectionScreen
import com.qixuan.channelvideoflow.feature.channels.ChannelSelectionUiState
import com.qixuan.channelvideoflow.feature.tags.TagFilterItem
import com.qixuan.channelvideoflow.feature.tags.TagFilterScreen
import com.qixuan.channelvideoflow.feature.tags.TagFilterUiState
import com.qixuan.channelvideoflow.model.video.TagFilterMode
import com.qixuan.channelvideoflow.model.video.TagSummary
import com.qixuan.channelvideoflow.model.video.VideoScanStatus
import com.qixuan.channelvideoflow.ui.theme.ChannelVideoFlowTheme
import java.io.File
import java.io.FileOutputStream
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Stage 16-only visual evidence using production composables and local test fixtures. */
@RunWith(AndroidJUnit4::class)
class Stage16VisualSnapshotTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun capturesChannelSelectionStates() {
        setThemedContent(darkTheme = false) {
            ChannelSnapshot(scanningChannelState())
        }
        capture("01-channels-scanning.png")

        setThemedContent(darkTheme = false) {
            ChannelSnapshot(noVideoChannelState())
        }
        capture("02-channels-no-videos-disabled.png")

        setThemedContent(darkTheme = false, widthDp = 320, fontScale = 1.35f) {
            ChannelSnapshot(scanningChannelState())
        }
        capture("03-channels-320dp-font135.png")
    }

    @Test
    fun capturesTagFilterDefaultAndDarkStates() {
        setThemedContent(darkTheme = false) {
            TagSnapshot(defaultTagState())
        }
        capture("04-tags-default.png")

        setThemedContent(darkTheme = true) {
            TagSnapshot(defaultTagState())
        }
        capture("07-tags-dark.png")
    }

    @Test
    fun capturesTagSearchResult() {
        var state by mutableStateOf(defaultTagState())
        setThemedContent(darkTheme = false) {
            TagSnapshot(state)
        }
        composeRule.runOnIdle {
            state = defaultTagState().copy(
                    searchQuery = "#CITY",
                    tags = defaultTagState().tags.filter { it.summary.normalizedName == "citywalk" },
            )
        }
        composeRule.waitForIdle()
        captureWindowWithoutSystemNavigation("05-tags-search-result.png")
    }

    @Test
    fun capturesTagNoResults() {
        setThemedContent(darkTheme = false) {
            TagSnapshot(defaultTagState().copy(searchQuery = "不存在", tags = emptyList()))
        }
        capture("06-tags-no-results.png")
    }

    @Composable
    private fun ChannelSnapshot(state: ChannelSelectionUiState) {
        ChannelSelectionScreen(
            uiState = state,
            onSearchQueryChanged = {},
            onToggleChannel = {},
            onSave = {},
            onRetry = {},
            onLogout = {},
            onOpenPlayback = {},
            onOpenCacheSettings = {},
            logoutEnabled = true,
            onPauseScan = {},
            onResumeScan = {},
            onToggleChannelPinned = {},
        )
    }

    @Composable
    private fun TagSnapshot(state: TagFilterUiState) {
        TagFilterScreen(
            uiState = state,
            onBack = {},
            onTagToggle = {},
            onModeChanged = {},
            onClearSelection = {},
            onContinue = {},
            onSearchQueryChanged = {},
            onClearSearch = {},
        )
    }

    private fun setThemedContent(
        darkTheme: Boolean,
        widthDp: Int? = 360,
        fontScale: Float = 1f,
        content: @Composable () -> Unit,
    ) {
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                val systemDensity = LocalDensity.current
                val rootFocusRequester = remember { FocusRequester() }
                LaunchedEffect(rootFocusRequester) {
                    rootFocusRequester.requestFocus()
                }
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
                                    .focusRequester(rootFocusRequester)
                                    .focusable()
                                    .graphicsLayer()
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

    /**
     * The API 36 desktop emulator can return stale pixels from captureToImage after a LazyColumn
     * changes from many rows to one. Use the actual window for that transition-only snapshot and
     * crop the emulator taskbar using the reported navigation-bar inset.
     */
    private fun captureWindowWithoutSystemNavigation(fileName: String) {
        val rootBitmap = composeRule.onNodeWithTag(SNAPSHOT_ROOT).captureToImage().asAndroidBitmap()
        var navigationBarBottom = 0
        composeRule.runOnUiThread {
            navigationBarBottom = composeRule.activity.window.decorView.rootWindowInsets
                ?.getInsets(WindowInsets.Type.navigationBars())
                ?.bottom
                ?: 0
        }
        val windowBitmap = requireNotNull(
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot(),
        )
        val left = ((windowBitmap.width - rootBitmap.width) / 2).coerceAtLeast(0)
        val top = ((windowBitmap.height - rootBitmap.height) / 2).coerceAtLeast(0)
        val width = rootBitmap.width.coerceAtMost(windowBitmap.width - left)
        val height = (rootBitmap.height - navigationBarBottom)
            .coerceAtMost(windowBitmap.height - top)
            .coerceAtLeast(1)
        val cropped = Bitmap.createBitmap(windowBitmap, left, top, width, height)
        try {
            writeBitmap(fileName, cropped)
        } finally {
            cropped.recycle()
            windowBitmap.recycle()
        }
    }

    private fun writeBitmap(fileName: String, bitmap: Bitmap) {
        val outputDirectory = requireNotNull(
            InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
        ).resolve("stage16-visuals")
        check(outputDirectory.exists() || outputDirectory.mkdirs())
        FileOutputStream(File(outputDirectory, fileName)).use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
    }

    private fun scanningChannelState() = ChannelSelectionUiState(
        phase = ChannelListPhase.CONTENT,
        channels = listOf(
            ChannelSelectionItem(-1001L, "城市漫游计划", "urban_walks", true, true, VideoScanStatus.COMPLETED, 812, 37),
            ChannelSelectionItem(-1002L, "独立电影与镜头语言研究", "indie_cinema", true, false, VideoScanStatus.SCANNING, 240, 18),
            ChannelSelectionItem(-1003L, "设计灵感研究所", "design_labs", false, true, VideoScanStatus.NOT_STARTED),
            ChannelSelectionItem(-1004L, "Weekend / 周末去哪里", null, false, false, VideoScanStatus.NOT_STARTED),
            ChannelSelectionItem(-1005L, "料理与日常", "slow_kitchen", false, false, VideoScanStatus.NOT_STARTED),
            ChannelSelectionItem(-1006L, "建筑、摄影与城市观察的长标题示例", "urban_architecture", false, false, VideoScanStatus.NOT_STARTED),
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

    private fun noVideoChannelState(): ChannelSelectionUiState {
        val content = scanningChannelState()
        return content.copy(
            channels = content.channels.map { channel ->
                channel.copy(
                    scanStatus = VideoScanStatus.NOT_STARTED,
                    processedVideoCandidateCount = 0,
                    indexedVideoCount = 0,
                )
            },
            scanSummary = ChannelScanSummary(
                totalChannelCount = content.selectedCount,
                canControl = true,
            ),
        )
    }

    private fun defaultTagState() = TagFilterUiState(
        isLoading = false,
        channelIds = setOf(-1001L, -1002L),
        tags = listOf(
            TagFilterItem(TagSummary("citywalk", "#CityWalk", 32), true),
            TagFilterItem(TagSummary("电影摄影", "#电影摄影", 18), false),
            TagFilterItem(TagSummary("weekend", "#周末灵感", 14), true),
            TagFilterItem(TagSummary("architecture", "#Architecture", 11), false),
            TagFilterItem(TagSummary("夜景", "#夜景", 9), false),
            TagFilterItem(TagSummary("longform", "#长标题也应该保持清晰且不过度增高", 6), false),
        ),
        totalTagCount = 6,
        selectedNames = setOf("citywalk", "weekend"),
        mode = TagFilterMode.OR,
    )

    private companion object {
        const val SNAPSHOT_ROOT = "stage16-snapshot-root"
    }
}
