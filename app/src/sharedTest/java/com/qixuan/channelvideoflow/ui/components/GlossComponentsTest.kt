package com.qixuan.channelvideoflow.ui.components

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qixuan.channelvideoflow.ui.theme.ChannelVideoFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GlossComponentsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun statefulPrimaryButtonKeepsStatusVisibleAndBlocksLoadingClicks() {
        val state = mutableStateOf(PrimaryActionState.Idle)
        var clicks = 0
        composeRule.setContent {
            ChannelVideoFlowTheme {
                StatefulPrimaryButton(
                    text = "保存",
                    state = state.value,
                    stateText = if (state.value == PrimaryActionState.Loading) "正在保存" else null,
                    onClick = { clicks += 1 },
                    modifier = Modifier.testTag("primary-action"),
                    progressIndicatorModifier = Modifier.testTag("primary-progress"),
                )
            }
        }

        composeRule.onNodeWithTag("primary-action").performClick()
        assertEquals(1, clicks)

        state.value = PrimaryActionState.Loading
        composeRule.onNodeWithText("正在保存").assertIsDisplayed()
        composeRule.onNodeWithTag("primary-progress").assertIsDisplayed()
        composeRule.onNodeWithTag("primary-action").assertIsNotEnabled().performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun statefulPrimaryButtonRemainsDisabledDuringAndAfterLoadingTransition() {
        val state = mutableStateOf(PrimaryActionState.Idle)
        var clicks = 0
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            ChannelVideoFlowTheme {
                StatefulPrimaryButton(
                    text = "继续",
                    state = state.value,
                    stateText = if (state.value == PrimaryActionState.Loading) "正在处理" else null,
                    onClick = {
                        clicks += 1
                        state.value = PrimaryActionState.Loading
                    },
                    modifier = Modifier.testTag("animated-primary-action"),
                )
            }
        }

        composeRule.onNodeWithTag("animated-primary-action").performClick()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeBy(100L)
        composeRule.onNodeWithTag("animated-primary-action")
            .assertIsNotEnabled()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    "正在处理",
                ),
            )
            .performClick()
        assertEquals(1, clicks)

        composeRule.mainClock.advanceTimeBy(500L)
        composeRule.onNodeWithTag("animated-primary-action")
            .assertIsNotEnabled()
            .performClick()
        assertEquals(1, clicks)
        composeRule.mainClock.autoAdvance = true
    }

    @Test
    fun segmentedControlExposesOneSelectedTabAndEmitsSelection() {
        var selected = 0
        composeRule.setContent {
            ChannelVideoFlowTheme {
                SegmentedControl(
                    options = listOf("任一", "全部"),
                    selectedIndex = selected,
                    onSelected = { selected = it },
                )
            }
        }

        composeRule.onNodeWithText("任一").assertIsSelected()
        composeRule.onNodeWithText("全部").performClick()
        assertEquals(1, selected)
    }

    @Test
    fun glossCardExposesSelectedDisabledAndErrorStateWithoutColorOnlyMeaning() {
        composeRule.setContent {
            ChannelVideoFlowTheme {
                GlossCard(
                    modifier = Modifier.testTag("state-card"),
                    enabled = false,
                    selected = true,
                    isError = true,
                    onClick = {},
                    stateDescription = "已选择，存在错误",
                ) {
                    androidx.compose.material3.Text("频道")
                }
            }
        }

        composeRule.onNodeWithTag("state-card")
            .assertIsSelected()
            .assertIsNotEnabled()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    "已选择，存在错误",
                ),
            )
    }
}
