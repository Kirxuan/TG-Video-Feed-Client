package com.qixuan.channelvideoflow.feature.tags

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qixuan.channelvideoflow.R
import com.qixuan.channelvideoflow.model.video.TagFilterMode
import com.qixuan.channelvideoflow.model.video.VideoFilter
import com.qixuan.channelvideoflow.ui.components.BottomPrimaryAction
import com.qixuan.channelvideoflow.ui.components.GlossCard
import com.qixuan.channelvideoflow.ui.components.GlossSearchField
import com.qixuan.channelvideoflow.ui.components.PremiumBackdrop
import com.qixuan.channelvideoflow.ui.components.PremiumTopBar
import com.qixuan.channelvideoflow.ui.components.SegmentedControl
import com.qixuan.channelvideoflow.ui.components.StatePanel
import com.qixuan.channelvideoflow.ui.components.StatusPill
import com.qixuan.channelvideoflow.ui.theme.ChannelVideoFlowTokens
import com.qixuan.channelvideoflow.ui.theme.glossColors

internal object TagFilterTestTags {
    const val Back = "tag-filter-back"
    const val Search = "tag-filter-search"
    const val ClearSearch = "tag-filter-clear-search"
    const val Mode = "tag-filter-mode"
    const val ClearSelection = "tag-filter-clear-selection"
    const val Continue = "tag-filter-continue"
    const val Loading = "tag-filter-loading"
    const val NoResults = "tag-filter-no-results"
    const val List = "tag-filter-list"
    fun tag(name: String) = "tag-filter-$name"
}

@Composable
fun TagFilterRoute(
    onBack: () -> Unit,
    onContinue: (VideoFilter) -> Unit,
    viewModel: TagFilterViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    TagFilterScreen(
        uiState = uiState,
        onBack = onBack,
        onSearchQueryChanged = viewModel::onSearchQueryChanged,
        onClearSearch = viewModel::clearSearch,
        onTagToggle = viewModel::toggleTag,
        onModeChanged = viewModel::setMode,
        onClearSelection = viewModel::clearSelection,
        onContinue = { onContinue(viewModel.currentFilter()) },
    )
}

@Composable
internal fun TagFilterScreen(
    uiState: TagFilterUiState,
    onBack: () -> Unit,
    onTagToggle: (String) -> Unit,
    onModeChanged: (TagFilterMode) -> Unit,
    onClearSelection: () -> Unit,
    onContinue: () -> Unit,
    onSearchQueryChanged: (String) -> Unit = {},
    onClearSearch: () -> Unit = {},
) {
    val totalTagCount = uiState.totalTagCount.takeIf { it > 0 } ?: uiState.tags.size
    val stateScrollState = rememberScrollState()
    PremiumBackdrop {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                PremiumTopBar(
                    title = stringResource(R.string.tags_title),
                    modifier = Modifier.windowInsetsPadding(
                        WindowInsets.safeDrawing.only(
                            WindowInsetsSides.Horizontal + WindowInsetsSides.Top,
                        ),
                    ),
                    navigation = {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag(TagFilterTestTags.Back),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_arrow_back_outlined),
                                contentDescription = stringResource(R.string.tags_back),
                                modifier = Modifier.size(ChannelVideoFlowTokens.Sizes.icon),
                            )
                        }
                    },
                )
            },
            bottomBar = {
                BottomPrimaryAction(
                    text = stringResource(
                        if (uiState.selectedNames.isEmpty()) {
                            R.string.tags_browse_all
                        } else {
                            R.string.tags_apply_and_browse
                        },
                    ),
                    onClick = onContinue,
                    buttonModifier = Modifier.testTag(TagFilterTestTags.Continue),
                    enabled = uiState.canContinue,
                )
            },
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = ChannelVideoFlowTokens.Spacing.large),
                verticalArrangement = Arrangement.spacedBy(ChannelVideoFlowTokens.Spacing.small),
            ) {
                GlossSearchField(
                    value = uiState.searchQuery,
                    onValueChange = onSearchQueryChanged,
                    label = stringResource(R.string.tags_search),
                    searchIcon = painterResource(R.drawable.ic_search_outlined),
                    searchIconContentDescription = stringResource(R.string.tags_search_icon),
                    clearIcon = painterResource(R.drawable.ic_clear_outlined),
                    clearIconContentDescription = stringResource(R.string.tags_search_clear),
                    modifier = Modifier.testTag(TagFilterTestTags.Search),
                    clearButtonModifier = Modifier.testTag(TagFilterTestTags.ClearSearch),
                )
                Text(
                    text = stringResource(R.string.tags_match_summary),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                SegmentedControl(
                    options = listOf(
                        stringResource(R.string.tags_mode_any),
                        stringResource(R.string.tags_mode_all),
                    ),
                    selectedIndex = if (uiState.mode == TagFilterMode.OR) 0 else 1,
                    onSelected = { index ->
                        onModeChanged(if (index == 0) TagFilterMode.OR else TagFilterMode.AND)
                    },
                    modifier = Modifier.testTag(TagFilterTestTags.Mode),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatusPill(
                        text = stringResource(
                            R.string.tags_selected_count,
                            uiState.selectedNames.size,
                        ),
                    )
                    if (uiState.hasActiveSearch) {
                        Text(
                            text = stringResource(
                                R.string.tags_visible_count,
                                uiState.tags.size,
                                totalTagCount,
                            ),
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                        )
                    } else {
                        Box(modifier = Modifier.weight(1f))
                    }
                    TextButton(
                        onClick = onClearSelection,
                        enabled = uiState.selectedNames.isNotEmpty(),
                        modifier = Modifier.testTag(TagFilterTestTags.ClearSelection),
                    ) {
                        Text(stringResource(R.string.tags_clear_selection))
                    }
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    val bodyState = when {
                        uiState.isLoading -> TagBodyState.Loading
                        uiState.channelIds.isEmpty() -> TagBodyState.MissingChannels
                        uiState.hasActiveSearch && uiState.tags.isEmpty() && totalTagCount > 0 ->
                            TagBodyState.NoResults
                        totalTagCount == 0 -> TagBodyState.Empty
                        else -> TagBodyState.Content
                    }
                    when (bodyState) {
                        TagBodyState.Loading -> {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .testTag(TagFilterTestTags.Loading),
                            )
                        }
                        TagBodyState.MissingChannels -> {
                            StatePanel(
                                title = stringResource(R.string.tags_missing_channels_title),
                                message = stringResource(R.string.tags_missing_channels_message),
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .verticalScroll(stateScrollState),
                                action = {
                                    TextButton(onClick = onBack) {
                                        Text(stringResource(R.string.tags_return_channels))
                                    }
                                },
                            )
                        }
                        TagBodyState.NoResults -> {
                            StatePanel(
                                title = stringResource(R.string.tags_no_results_title),
                                message = stringResource(R.string.tags_no_results_message),
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .verticalScroll(stateScrollState)
                                    .testTag(TagFilterTestTags.NoResults),
                                action = {
                                    TextButton(onClick = onClearSearch) {
                                        Text(stringResource(R.string.tags_search_clear))
                                    }
                                },
                            )
                        }
                        TagBodyState.Empty -> {
                            StatePanel(
                                title = stringResource(R.string.tags_empty_title),
                                message = stringResource(R.string.tags_empty_message),
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .verticalScroll(stateScrollState),
                            )
                        }
                        TagBodyState.Content -> TagList(uiState.tags, onTagToggle)
                    }
                }
            }
        }
    }
}

@Composable
private fun TagList(
    tags: List<TagFilterItem>,
    onTagToggle: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag(TagFilterTestTags.List),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(tags, key = { item -> item.summary.normalizedName }) { item ->
            GlossCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        toggleableState = if (item.isSelected) {
                            ToggleableState.On
                        } else {
                            ToggleableState.Off
                        }
                    }
                    .testTag(TagFilterTestTags.tag(item.summary.normalizedName))
                    .sizeIn(minHeight = 64.dp),
                shape = ChannelVideoFlowTokens.Shapes.medium,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 8.dp,
                    vertical = 6.dp,
                ),
                selected = item.isSelected,
                onClick = { onTagToggle(item.summary.normalizedName) },
                role = Role.Checkbox,
                stateDescription = if (item.isSelected) "已选择" else "未选择",
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = item.isSelected, onCheckedChange = null)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(1.dp),
                    ) {
                        Text(
                            text = item.summary.displayName,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = stringResource(
                                R.string.tags_video_count,
                                item.summary.videoCount,
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

private enum class TagBodyState {
    Loading,
    MissingChannels,
    NoResults,
    Empty,
    Content,
}
