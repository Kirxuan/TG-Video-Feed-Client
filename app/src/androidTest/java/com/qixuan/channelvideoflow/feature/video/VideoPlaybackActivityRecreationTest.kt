package com.qixuan.channelvideoflow.feature.video

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.media3.ui.PlayerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.qixuan.channelvideoflow.MainActivity
import com.qixuan.channelvideoflow.model.video.IndexedVideo
import com.qixuan.channelvideoflow.model.video.VideoKey
import com.qixuan.channelvideoflow.player.VideoPlaybackController
import com.qixuan.channelvideoflow.player.VideoPlaybackState
import com.qixuan.channelvideoflow.player.VideoPlayerSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VideoPlaybackActivityRecreationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun activityRecreationDetachesOldPlayerViewBeforeAttachingReplacement() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val player = RecreationBindingProbe()
        val video = IndexedVideo(
            key = VideoKey(chatId = 41L, messageId = 73L),
            fileId = 101,
            remoteUniqueId = "recreation-probe",
            caption = "",
            supportsStreaming = true,
            fileSize = 1,
            durationSeconds = 1,
            width = 1,
            height = 1,
            publishTime = 1,
            editTime = null,
            canBeSaved = true,
            tags = emptyList(),
        )
        val uiState = VideoPlaybackUiState(
            phase = VideoFeedPhase.CONTENT,
            items = listOf(FeedVideoItem(video, "重建测试")),
            player = VideoPlayerSnapshot(
                playbackState = VideoPlaybackState.Ready(
                    video = video,
                    firstReadyWaitMillis = null,
                    observedLocalBytes = null,
                ),
                hasRenderedFirstFrame = true,
            ),
            showSwipeHint = true,
        )

        val scenario = composeRule.activityRule.scenario
        scenario.mountPlaybackContent(uiState, player)
        composeRule.waitForIdle()
        assertEquals(1, player.attachCalls)
        assertEquals(0, player.detachCalls)
        assertEquals(1, player.activeBindings)
        composeRule.onAllNodesWithTag(VideoFeedTestTags.SwipeHint).assertCountEquals(1)

        scenario.recreate()
        composeRule.waitForIdle()
        assertEquals(1, player.detachCalls)
        assertEquals(0, player.activeBindings)

        scenario.mountPlaybackContent(uiState, player)
        composeRule.waitForIdle()
        assertEquals(2, player.attachCalls)
        assertEquals(1, player.detachCalls)
        assertEquals(1, player.activeBindings)
        assertEquals(1, player.maxActiveBindings)
        composeRule.onAllNodesWithTag(VideoFeedTestTags.SwipeHint).assertCountEquals(1)

        scenario.close()

        instrumentation.waitForIdleSync()
        assertEquals(2, player.detachCalls)
        assertEquals(0, player.activeBindings)
        assertTrue(player.attachedViews.zipWithNext().all { (old, new) -> old !== new })
    }

    private fun ActivityScenario<MainActivity>.mountPlaybackContent(
        uiState: VideoPlaybackUiState,
        player: RecreationBindingProbe,
    ) {
        onActivity { activity ->
            activity.setContent {
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
                    onAttachPlayer = player::attach,
                    onDetachPlayer = player::detach,
                )
            }
        }
    }

    private class RecreationBindingProbe : VideoPlaybackController {
        override val snapshot: StateFlow<VideoPlayerSnapshot> =
            MutableStateFlow(VideoPlayerSnapshot())

        var attachCalls = 0
            private set
        var detachCalls = 0
            private set
        var activeBindings = 0
            private set
        var maxActiveBindings = 0
            private set
        val attachedViews = mutableListOf<PlayerView>()

        override fun attach(playerView: PlayerView) {
            attachCalls += 1
            activeBindings += 1
            maxActiveBindings = maxOf(maxActiveBindings, activeBindings)
            attachedViews += playerView
        }

        override fun detach(playerView: PlayerView) {
            detachCalls += 1
            activeBindings -= 1
        }

        override fun bind(video: IndexedVideo) = Unit
        override fun retry() = Unit
        override fun pause() = Unit
        override fun resume() = Unit
        override fun seekTo(positionMillis: Long) = Unit
        override fun pauseForPageTransition() = Unit
        override fun setMuted(muted: Boolean) = Unit
        override fun onAppBackgrounded() = Unit
        override fun releaseBinding() = Unit
        override fun release() = Unit
    }
}
