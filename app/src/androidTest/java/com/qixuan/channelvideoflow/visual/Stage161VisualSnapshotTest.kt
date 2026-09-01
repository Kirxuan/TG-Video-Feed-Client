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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.qixuan.channelvideoflow.MainActivity
import com.qixuan.channelvideoflow.feature.channels.ChannelListPhase
import com.qixuan.channelvideoflow.feature.channels.ChannelScanSummary
import com.qixuan.channelvideoflow.feature.channels.ChannelSelectionItem
import com.qixuan.channelvideoflow.feature.channels.ChannelSelectionScreen
import com.qixuan.channelvideoflow.feature.channels.ChannelSelectionTestTags
import com.qixuan.channelvideoflow.feature.channels.ChannelSelectionUiState
import com.qixuan.channelvideoflow.model.video.VideoScanStatus
import com.qixuan.channelvideoflow.ui.theme.ChannelVideoFlowTheme
import java.io.File
import java.io.FileOutputStream
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Stage 16.1 visual evidence for compact channel selection and scan summaries. */
@RunWith(AndroidJUnit4::class)
class Stage161VisualSnapshotTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun capturesCollapsedAndExpandedSummaries() {
        setThemedContent(widthDp = 360, fontScale = 1f)
        capture("01-channel-summaries-collapsed.png")

        composeRule.onNodeWithTag(ChannelSelectionTestTags.PinDetails).performClick()
        composeRule.onNodeWithTag(ChannelSelectionTestTags.ScanDetails).performClick()
        composeRule.waitForIdle()
        capture("02-channel-summaries-expanded.png")
    }

    @Test
    fun capturesExpandedSummariesAt320DpAndLargeFont() {
        setThemedContent(widthDp = 320, fontScale = 1.35f)
        composeRule.onNodeWithTag(ChannelSelectionTestTags.PinDetails).performClick()
        composeRule.onNodeWithTag(ChannelSelectionTestTags.ScanDetails).performClick()
        composeRule.waitForIdle()
        capture("03-channel-summaries-expanded-320dp-font135.png")
    }

    private fun setThemedContent(widthDp: Int, fontScale: Float) {
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                val systemDensity = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(systemDensity.density, fontScale),
                ) {
                    ChannelVideoFlowTheme(darkTheme = false) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFFF4F6FA)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(widthDp.dp)
                                    .fillMaxHeight()
                                    .testTag(SNAPSHOT_ROOT),
                            ) {
                                ChannelSnapshot()
                            }
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Composable
    private fun ChannelSnapshot() {
        ChannelSelectionScreen(
            uiState = snapshotState(),
            onSearchQueryChanged = {},
            onToggleChannel = {},
            onToggleChannelPinned = {},
            onSave = {},
            onRetry = {},
            onPauseScan = {},
            onResumeScan = {},
            onLogout = {},
            onOpenPlayback = {},
            onOpenCacheSettings = {},
            logoutEnabled = true,
        )
    }

    private fun capture(fileName: String) {
        val bitmap = composeRule.onNodeWithTag(SNAPSHOT_ROOT).captureToImage().asAndroidBitmap()
        val outputDirectory = requireNotNull(
            InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
        ).resolve("stage16-1-visuals")
        check(outputDirectory.exists() || outputDirectory.mkdirs())
        FileOutputStream(File(outputDirectory, fileName)).use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
    }

    private fun snapshotState() = ChannelSelectionUiState(
        phase = ChannelListPhase.CONTENT,
        channels = listOf(
            ChannelSelectionItem(
                chatId = -1001L,
                title = "城市影像档案",
                username = "city_archive",
                isSelected = true,
                isPinned = true,
                scanStatus = VideoScanStatus.SCANNING,
                videoSearchPageCount = 195,
                processedVideoCandidateCount = 17_946,
                indexedVideoCount = 3_605,
            ),
            ChannelSelectionItem(-1002L, "轻松短片收藏", "short_clips", false),
            ChannelSelectionItem(-1003L, "动画与镜头研究", "motion_study", false),
            ChannelSelectionItem(-1004L, "周末观影清单", "weekend_watch", false),
        ),
        selectedCount = 1,
        scanSummary = ChannelScanSummary(
            processedVideoCandidateCount = 17_946,
            videoSearchPageCount = 195,
            indexedVideoCount = 3_605,
            approximateVideoCount = 3_620,
            completedChannelCount = 0,
            totalChannelCount = 1,
            canControl = true,
        ),
    )

    private companion object {
        const val SNAPSHOT_ROOT = "stage16-1-snapshot-root"
    }
}
