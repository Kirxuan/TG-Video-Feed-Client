package com.qixuan.channelvideoflow.visual

import android.view.WindowInsets
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qixuan.channelvideoflow.MainActivity
import com.qixuan.channelvideoflow.feature.auth.LoginScreen
import com.qixuan.channelvideoflow.feature.auth.LoginStep
import com.qixuan.channelvideoflow.feature.auth.LoginTestTags
import com.qixuan.channelvideoflow.feature.auth.LoginUiState
import com.qixuan.channelvideoflow.feature.channels.ChannelListPhase
import com.qixuan.channelvideoflow.feature.channels.ChannelSelectionScreen
import com.qixuan.channelvideoflow.feature.channels.ChannelSelectionTestTags
import com.qixuan.channelvideoflow.feature.channels.ChannelSelectionUiState
import com.qixuan.channelvideoflow.ui.theme.ChannelVideoFlowTheme
import kotlin.math.max
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real API 36 window evidence; run once in gestural mode and once in three-button mode. */
@RunWith(AndroidJUnit4::class)
class Stage19SystemUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun loginSafeDrawingAndImeKeepSubmitReachable() {
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                ChannelVideoFlowTheme {
                    LoginScreen(
                        uiState = LoginUiState(step = LoginStep.PHONE_NUMBER),
                        onInputChanged = {},
                        onSubmit = {},
                        onResendCode = {},
                        onRetry = {},
                        onLogout = {},
                    )
                }
            }
        }

        composeRule.onNodeWithTag(LoginTestTags.Input)
            .assertIsDisplayed()
            .performClick()
            .performTextInput("+86 123")
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.activity.window.decorView.rootWindowInsets
                ?.isVisible(WindowInsets.Type.ime()) == true
        }

        val rootInsets = requireNotNull(
            composeRule.activity.window.decorView.rootWindowInsets,
        )
        val status = rootInsets.getInsets(WindowInsets.Type.statusBars())
        val cutout = rootInsets.getInsets(WindowInsets.Type.displayCutout())
        val ime = rootInsets.getInsets(WindowInsets.Type.ime())
        val safeTop = max(status.top, cutout.top).toFloat()
        val imeTop = (composeRule.activity.window.decorView.height - ime.bottom).toFloat()
        val inputBounds = composeRule.onNodeWithTag(LoginTestTags.Input)
            .fetchSemanticsNode().boundsInRoot
        val submitBounds = composeRule.onNodeWithTag(LoginTestTags.Submit)
            .assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot

        assertTrue("Login input overlaps status bar or display cutout", inputBounds.top >= safeTop)
        assertTrue("Login submit overlaps the visible IME", submitBounds.bottom <= imeTop)
    }

    @Test
    fun bottomPrimaryActionStaysAboveNavigationAndMandatoryGestureInsets() {
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                ChannelVideoFlowTheme {
                    ChannelSelectionScreen(
                        uiState = ChannelSelectionUiState(
                            phase = ChannelListPhase.EMPTY,
                        ),
                        onSearchQueryChanged = {},
                        onToggleChannel = {},
                        onSave = {},
                        onRetry = {},
                        onLogout = {},
                        logoutEnabled = true,
                    )
                }
            }
        }
        composeRule.waitForIdle()

        val rootInsets = requireNotNull(
            composeRule.activity.window.decorView.rootWindowInsets,
        )
        val navigation = rootInsets.getInsets(WindowInsets.Type.navigationBars())
        val mandatory = rootInsets.getInsets(WindowInsets.Type.mandatorySystemGestures())
        val safeBottom = max(navigation.bottom, mandatory.bottom)
        val usableBottom = (composeRule.activity.window.decorView.height - safeBottom).toFloat()
        val saveBounds = composeRule.onNodeWithTag(ChannelSelectionTestTags.Save)
            .assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot

        assertTrue("Bottom primary action overlaps navigation gestures", saveBounds.bottom <= usableBottom)
    }
}

/** Requires an enabled AOSP display-cutout emulation overlay. */
@RunWith(AndroidJUnit4::class)
class Stage19DisplayCutoutTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun loginInputStaysBelowNonZeroDisplayCutout() {
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                ChannelVideoFlowTheme {
                    LoginScreen(
                        uiState = LoginUiState(step = LoginStep.PHONE_NUMBER),
                        onInputChanged = {},
                        onSubmit = {},
                        onResendCode = {},
                        onRetry = {},
                        onLogout = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()

        val rootInsets = requireNotNull(
            composeRule.activity.window.decorView.rootWindowInsets,
        )
        val cutout = rootInsets.getInsets(WindowInsets.Type.displayCutout())
        val hasNonZeroCutout = cutout.top > 0 || cutout.left > 0 ||
            cutout.right > 0 || cutout.bottom > 0
        val inputBounds = composeRule.onNodeWithTag(LoginTestTags.Input)
            .assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot

        assertTrue("A non-zero AOSP display cutout overlay is required", hasNonZeroCutout)
        assertTrue("Login input overlaps the emulated display cutout", inputBounds.top >= cutout.top)
    }
}
