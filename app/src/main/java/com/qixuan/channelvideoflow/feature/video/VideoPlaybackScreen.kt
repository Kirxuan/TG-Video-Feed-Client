package com.qixuan.channelvideoflow.feature.video

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContent
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.qixuan.channelvideoflow.model.video.IndexedVideo
import com.qixuan.channelvideoflow.model.video.VideoFeedOrder
import com.qixuan.channelvideoflow.model.video.VideoFilter
import com.qixuan.channelvideoflow.model.video.VideoKey
import com.qixuan.channelvideoflow.player.VideoPlaybackFailure
import com.qixuan.channelvideoflow.player.VideoPlaybackState
import com.qixuan.channelvideoflow.player.VideoPlaybackSpeeds
import com.qixuan.channelvideoflow.ui.theme.ChannelVideoFlowTokens
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.roundToLong

internal object VideoFeedTestTags {
    const val Loading = "video-feed-loading"
    const val Empty = "video-feed-empty"
    const val EmptyAction = "video-feed-empty-action"
    const val Retry = "video-feed-retry"
    const val TapSurface = "video-feed-tap-surface"
    const val PausedOverlay = "video-feed-paused-overlay"
    const val Progress = "video-feed-progress"
    const val Metadata = "video-feed-metadata"
    const val Fullscreen = "video-feed-fullscreen"
    const val ExitFullscreen = "video-feed-exit-fullscreen"
    const val Mute = "video-feed-mute"
    const val OriginalLink = "video-feed-original-link"
    const val Pager = "video-feed-pager"
    const val LatestOrder = "video-feed-order-latest"
    const val RandomOrder = "video-feed-order-random"
    const val Logout = "video-feed-logout"
    const val SwipeHint = "video-feed-swipe-hint"
    const val TemporarySpeed = "video-feed-temporary-speed"
    const val DetailsExpand = "video-feed-details-expand"
    const val DetailsSheet = "video-feed-details-sheet"
    const val DetailsContent = "video-feed-details-content"
    const val DetailsClose = "video-feed-details-close"
    const val DetailsCaption = "video-feed-details-caption"
    const val DetailsTags = "video-feed-details-tags"
    const val DetailsPublishTime = "video-feed-details-publish-time"
    const val LoadingPoster = "video-feed-loading-poster"
}

internal val LoadingPosterAlphaSemanticsKey = SemanticsPropertyKey<Float>(
    name = "LoadingPosterAlpha",
)
internal var SemanticsPropertyReceiver.loadingPosterAlpha by LoadingPosterAlphaSemanticsKey

internal val LoadingPosterPaletteSemanticsKey = SemanticsPropertyKey<Int>(
    name = "LoadingPosterPalette",
)
internal var SemanticsPropertyReceiver.loadingPosterPalette by LoadingPosterPaletteSemanticsKey

internal val LoadingPosterVideoIdentitySemanticsKey = SemanticsPropertyKey<String>(
    name = "LoadingPosterVideoIdentity",
)
internal var SemanticsPropertyReceiver.loadingPosterVideoIdentity by
    LoadingPosterVideoIdentitySemanticsKey

@Composable
@UnstableApi
fun VideoPlaybackRoute(
    onBack: () -> Unit,
    onLogout: () -> Unit,
    initialFilter: VideoFilter? = null,
    viewModel: VideoPlaybackViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    var isFullscreen by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(viewModel, initialFilter) {
        initialFilter?.let(viewModel::setFilter)
    }

    FullscreenSystemUiEffect(isFullscreen = isFullscreen)
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.onForegroundChanged(true)
                Lifecycle.Event.ON_STOP -> viewModel.onForegroundChanged(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.releasePage()
        }
    }
    LaunchedEffect(viewModel, context) {
        viewModel.openOriginalMessageLinks.collect { httpsUrl ->
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(httpsUrl)))
            }.onFailure {
                viewModel.onOriginalMessageLinkOpenFailed()
            }
        }
    }
    VideoPlaybackScreen(
        uiState = uiState,
        playbackProgress = viewModel.playbackProgress,
        onBack = onBack,
        onLogout = {
            viewModel.releasePage()
            onLogout()
        },
        onRetry = viewModel::retry,
        onTogglePause = viewModel::togglePause,
        onTemporaryPlaybackSpeedChanged = viewModel::setTemporaryPlaybackSpeed,
        onSeek = viewModel::seekTo,
        onToggleMute = viewModel::toggleMute,
        onOriginalMessage = viewModel::requestOriginalMessageLink,
        onOrderChanged = viewModel::setOrder,
        onPageUnstable = viewModel::onPageUnstable,
        onPageTargeted = viewModel::onPageTargeted,
        onPageSettled = viewModel::onPageSettled,
        onPagerPointerDown = viewModel::onPagerPointerDown,
        onPagerPointerReleased = viewModel::onPagerPointerReleased,
        onAttachPlayer = viewModel::attachPlayer,
        onDetachPlayer = viewModel::detachPlayer,
        isFullscreen = isFullscreen,
        onFullscreenChanged = { isFullscreen = it },
    )
}

@Composable
@UnstableApi
internal fun VideoPlaybackScreen(
    uiState: VideoPlaybackUiState,
    playbackProgress: StateFlow<VideoPlaybackProgressUiState>? = null,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onRetry: () -> Unit,
    onTogglePause: () -> Unit,
    onTemporaryPlaybackSpeedChanged: (Boolean) -> Unit = {},
    onSeek: (Long) -> Unit,
    onToggleMute: () -> Unit,
    onOriginalMessage: () -> Unit,
    onOrderChanged: (VideoFeedOrder) -> Unit,
    onPageUnstable: () -> Unit,
    onPageTargeted: (Int, Int) -> Unit = { _, _ -> },
    onPageSettled: (Int, Int) -> Unit,
    onPagerPointerDown: (Long) -> Unit = {},
    onPagerPointerReleased: (Long) -> Unit = {},
    onAttachPlayer: (PlayerView) -> Unit,
    onDetachPlayer: (PlayerView) -> Unit = {},
    onPagerComposed: () -> Unit = {},
    isFullscreen: Boolean = false,
    onFullscreenChanged: (Boolean) -> Unit = {},
) {
    var detailVideoKey by remember { mutableStateOf<VideoKey?>(null) }
    val detailItem = detailVideoKey?.let { key ->
        (uiState.items + uiState.upcomingItems).firstOrNull { item -> item.video.key == key }
    }
    val activeDetailItem = detailItem?.takeIf {
        uiState.phase == VideoFeedPhase.CONTENT && !isFullscreen
    }

    LaunchedEffect(uiState.queueGeneration) {
        detailVideoKey = null
    }
    LaunchedEffect(uiState.phase, detailItem, isFullscreen) {
        if (
            detailVideoKey != null &&
            (uiState.phase != VideoFeedPhase.CONTENT || detailItem == null || isFullscreen)
        ) {
            detailVideoKey = null
        }
    }
    BackHandler {
        if (detailVideoKey != null) {
            detailVideoKey = null
        } else if (isFullscreen) {
            onFullscreenChanged(false)
        } else {
            onBack()
        }
    }
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (activeDetailItem == null) {
                        Modifier
                    } else {
                        Modifier.clearAndSetSemantics { }
                    },
                ),
        ) {
            val detachedMessageFailure = (uiState.player.playbackState as? VideoPlaybackState.Failed)
                ?.takeIf { failed ->
                    failed.reason == VideoPlaybackFailure.MESSAGE_UNAVAILABLE &&
                        uiState.items.none { item -> item.video.key == failed.video.key }
                }
            if (detachedMessageFailure != null) {
                ImmersiveMessageUnavailableState(onBack = onBack)
            } else when (uiState.phase) {
                VideoFeedPhase.LOADING -> ImmersiveLoadingState()
                VideoFeedPhase.EMPTY -> ImmersiveEmptyState(onBack = onBack)
                VideoFeedPhase.CONTENT -> FeedPager(
                    uiState = uiState,
                    playbackProgress = playbackProgress,
                    onRetry = onRetry,
                    onTogglePause = onTogglePause,
                    onTemporaryPlaybackSpeedChanged = onTemporaryPlaybackSpeedChanged,
                    onSeek = onSeek,
                    onToggleMute = onToggleMute,
                    onOriginalMessage = onOriginalMessage,
                    onPageUnstable = onPageUnstable,
                    onPageTargeted = onPageTargeted,
                    onPageSettled = onPageSettled,
                    onPagerPointerDown = onPagerPointerDown,
                    onPagerPointerReleased = onPagerPointerReleased,
                    onAttachPlayer = onAttachPlayer,
                    onDetachPlayer = onDetachPlayer,
                    onPagerComposed = onPagerComposed,
                    isFullscreen = isFullscreen,
                    onFullscreenChanged = onFullscreenChanged,
                    detailsVisible = activeDetailItem != null,
                    onShowDetails = { key -> detailVideoKey = key },
                    onCurrentVideoKeyChanged = { currentKey ->
                        if (detailVideoKey != null && detailVideoKey != currentKey) {
                            detailVideoKey = null
                        }
                    },
                )
            }
            if (!isFullscreen) {
                FeedTopBar(
                    order = uiState.order,
                    onBack = onBack,
                    onLogout = onLogout,
                    onOrderChanged = onOrderChanged,
                )
            }
        }

        activeDetailItem?.let { item ->
            VideoDetailsBottomSheet(
                item = item,
                onDismiss = { detailVideoKey = null },
            )
        }
    }
}

@Composable
private fun FeedTopBar(
    order: VideoFeedOrder,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onOrderChanged: (VideoFeedOrder) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(124.dp)
            .zIndex(2f)
            .drawWithCache {
                val scrim = Brush.verticalGradient(
                    listOf(Color.Black.copy(alpha = 0.72f), Color.Transparent),
                )
                onDrawBehind { drawRect(scrim) }
            },
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Horizontal + WindowInsetsSides.Top,
                ),
            )
            .height(56.dp)
            .zIndex(3f)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.CenterStart),
        ) {
            FeedIcon(FeedIconType.BACK, "返回频道")
        }
        Row(
            modifier = Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FeedOrderTab(
                text = "最新",
                selected = order == VideoFeedOrder.LATEST,
                testTag = VideoFeedTestTags.LatestOrder,
                onClick = { onOrderChanged(VideoFeedOrder.LATEST) },
            )
            FeedOrderTab(
                text = "随机",
                selected = order == VideoFeedOrder.RANDOM,
                testTag = VideoFeedTestTags.RandomOrder,
                onClick = { onOrderChanged(VideoFeedOrder.RANDOM) },
            )
        }
        IconButton(
            onClick = onLogout,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .testTag(VideoFeedTestTags.Logout),
        ) {
            FeedIcon(FeedIconType.LOGOUT, "退出登录")
        }
    }
}

@Composable
private fun FeedOrderTab(
    text: String,
    selected: Boolean,
    testTag: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(ChannelVideoFlowTokens.Shapes.pill)
            .background(
                if (selected) {
                    ChannelVideoFlowTokens.Feed.electricBlue.copy(alpha = 0.24f)
                } else {
                    ChannelVideoFlowTokens.Feed.overlay.copy(alpha = 0.54f)
                },
            )
            .border(
                width = 1.dp,
                color = if (selected) {
                    ChannelVideoFlowTokens.Feed.electricBlue.copy(alpha = 0.54f)
                } else {
                    ChannelVideoFlowTokens.Feed.outline
                },
                shape = ChannelVideoFlowTokens.Shapes.pill,
            )
            .clickable(
                enabled = !selected,
                role = Role.Tab,
                onClick = onClick,
            )
            .semantics {
                role = Role.Tab
                this.selected = selected
            }
            .testTag(testTag)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = text,
            color = if (selected) Color.White else Color.White.copy(alpha = 0.58f),
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}

@Composable
@UnstableApi
private fun FeedPager(
    uiState: VideoPlaybackUiState,
    playbackProgress: StateFlow<VideoPlaybackProgressUiState>?,
    onRetry: () -> Unit,
    onTogglePause: () -> Unit,
    onTemporaryPlaybackSpeedChanged: (Boolean) -> Unit,
    onSeek: (Long) -> Unit,
    onToggleMute: () -> Unit,
    onOriginalMessage: () -> Unit,
    onPageUnstable: () -> Unit,
    onPageTargeted: (Int, Int) -> Unit,
    onPageSettled: (Int, Int) -> Unit,
    onPagerPointerDown: (Long) -> Unit,
    onPagerPointerReleased: (Long) -> Unit,
    onAttachPlayer: (PlayerView) -> Unit,
    onDetachPlayer: (PlayerView) -> Unit,
    onPagerComposed: () -> Unit,
    isFullscreen: Boolean,
    onFullscreenChanged: (Boolean) -> Unit,
    detailsVisible: Boolean,
    onShowDetails: (VideoKey) -> Unit,
    onCurrentVideoKeyChanged: (VideoKey?) -> Unit,
) {
    SideEffect(onPagerComposed)
    val context = LocalContext.current
    val pagerState = rememberPagerState(
        pageCount = {
            if (uiState.order == VideoFeedOrder.RANDOM) RANDOM_PAGER_PAGE_COUNT else uiState.items.size
        },
    )
    LaunchedEffect(uiState.queueGeneration) {
        if (uiState.items.isNotEmpty()) {
            pagerState.scrollToPage(
                if (uiState.order == VideoFeedOrder.RANDOM) {
                    randomPagerStart(uiState.items.size)
                } else {
                    0
                },
            )
        }
    }
    LaunchedEffect(uiState.items.size) {
        if (
            uiState.order == VideoFeedOrder.LATEST &&
            pagerState.currentPage >= uiState.items.size &&
            uiState.items.isNotEmpty()
        ) {
            pagerState.scrollToPage(uiState.items.lastIndex)
        }
    }
    LaunchedEffect(
        pagerState,
        uiState.items.size,
        uiState.upcomingItems,
        uiState.randomRoundStartPagerPage,
    ) {
        snapshotFlow {
            PagerSignal(
                currentPage = pagerState.currentPage,
                targetPage = pagerState.targetPage,
                settledPage = pagerState.settledPage,
                isScrollInProgress = pagerState.isScrollInProgress,
            )
        }
            .collectLatest { signal ->
                if (signal.isScrollInProgress) {
                    onPageUnstable()
                    val committedTargetPage = committedPagerTargetPage(
                        currentPage = signal.currentPage,
                        predictedTargetPage = signal.targetPage,
                    )
                    resolvePagerItem(uiState, committedTargetPage)?.let { target ->
                        onPageTargeted(committedTargetPage, target.logicalPage)
                    }
                } else {
                    resolvePagerItem(uiState, signal.settledPage)?.let { settled ->
                        onPageSettled(signal.settledPage, settled.logicalPage)
                    }
                }
            }
    }

    val currentItem = resolvePagerItem(uiState, pagerState.currentPage)?.item
    val pagerInteractionEnabled =
        !isFullscreen &&
            !detailsVisible
    LaunchedEffect(currentItem?.video?.key) {
        onCurrentVideoKeyChanged(currentItem?.video?.key)
    }
    val currentPointerDown = rememberUpdatedState(onPagerPointerDown)
    val currentPointerReleased = rememberUpdatedState(onPagerPointerReleased)
    ProtectedContentWindowEffect(isProtected = currentItem?.video?.canBeSaved == false)
    if (uiState.items.any { item -> item.video.supportsStreaming }) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = {
                PlayerView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    onAttachPlayer(this)
                }
            },
            onRelease = onDetachPlayer,
        )
    }

    VerticalPager(
        state = pagerState,
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (!pagerInteractionEnabled) {
                    Modifier
                } else {
                    Modifier.observePagerPointerLifecycle(
                        onPointerDown = currentPointerDown,
                        onPointerReleased = currentPointerReleased,
                    )
                },
            )
            .testTag(VideoFeedTestTags.Pager),
        userScrollEnabled = pagerInteractionEnabled,
        key = { page ->
            val resolved = requireNotNull(resolvePagerItem(uiState, page))
            pagerItemKey(
                pagerPage = page,
                order = uiState.order,
                videoKey = resolved.item.video.key,
            )
        },
    ) { page ->
        val resolved = requireNotNull(resolvePagerItem(uiState, page))
        FeedPage(
            item = resolved.item,
            isCurrentPage = page == pagerState.currentPage,
            isPageScrolling = pagerState.isScrollInProgress,
            isFullscreen = isFullscreen,
            uiState = uiState,
            playbackProgress = playbackProgress,
            onRetry = onRetry,
            onTogglePause = onTogglePause,
            onTemporaryPlaybackSpeedChanged = onTemporaryPlaybackSpeedChanged,
            onSeek = onSeek,
            onToggleMute = onToggleMute,
            onOriginalMessage = onOriginalMessage,
            onFullscreenChanged = onFullscreenChanged,
            detailsVisible = detailsVisible,
            onShowDetails = onShowDetails,
        )
    }
}

private data class PagerSignal(
    val currentPage: Int,
    val targetPage: Int,
    val settledPage: Int,
    val isScrollInProgress: Boolean,
)

/**
 * Treats crossing Pager's snap midpoint as the spatial hysteresis for preparation.
 * targetPage is a useful prediction, but device traces show it can point at the next
 * page during a small drag that ultimately settles back on the current page.
 */
internal fun committedPagerTargetPage(
    currentPage: Int,
    predictedTargetPage: Int,
): Int = if (currentPage == predictedTargetPage) predictedTargetPage else currentPage

internal fun pagerItemKey(
    pagerPage: Int,
    order: VideoFeedOrder,
    videoKey: VideoKey,
): String = if (order == VideoFeedOrder.RANDOM) {
    "$pagerPage:${videoKey.chatId}:${videoKey.messageId}"
} else {
    "${videoKey.chatId}:${videoKey.messageId}"
}

internal data class ResolvedPagerItem(
    val item: FeedVideoItem,
    val logicalPage: Int,
)

internal fun resolvePagerItem(
    uiState: VideoPlaybackUiState,
    pagerPage: Int,
): ResolvedPagerItem? {
    val current = uiState.items
    if (current.isEmpty()) return null
    if (uiState.order != VideoFeedOrder.RANDOM) {
        return current.getOrNull(pagerPage)?.let { ResolvedPagerItem(it, pagerPage) }
    }
    val roundStart = uiState.randomRoundStartPagerPage
    if (roundStart == null) {
        val index = Math.floorMod(pagerPage, current.size)
        return ResolvedPagerItem(current[index], index)
    }
    val offset = pagerPage - roundStart
    if (offset < 0 || uiState.upcomingItems.isEmpty()) {
        val index = Math.floorMod(offset, current.size)
        return ResolvedPagerItem(current[index], index)
    }
    if (offset < current.size) {
        return ResolvedPagerItem(current[offset], offset)
    }
    val upcomingIndex = Math.floorMod(offset - current.size, uiState.upcomingItems.size)
    return ResolvedPagerItem(uiState.upcomingItems[upcomingIndex], upcomingIndex)
}

private sealed interface FeedPagePresentation {
    data object Content : FeedPagePresentation
    data object Loading : FeedPagePresentation
    data object Unsupported : FeedPagePresentation
    data class Failure(
        val reason: VideoPlaybackFailure,
    ) : FeedPagePresentation
}

private fun feedPagePresentation(
    item: FeedVideoItem,
    player: com.qixuan.channelvideoflow.player.VideoPlayerSnapshot,
): FeedPagePresentation {
    val playbackState = player.playbackState
    if (!item.video.supportsStreaming) return FeedPagePresentation.Unsupported
    return when {
        playbackState is VideoPlaybackState.Unsupported &&
            playbackState.video.key == item.video.key -> FeedPagePresentation.Unsupported
        playbackState is VideoPlaybackState.Failed &&
            playbackState.video.key == item.video.key ->
            FeedPagePresentation.Failure(playbackState.reason)
        playbackState is VideoPlaybackState.Ready &&
            playbackState.video.key == item.video.key &&
            player.hasRenderedFirstFrame -> FeedPagePresentation.Content
        else -> FeedPagePresentation.Loading
    }
}

private fun Modifier.observePagerPointerLifecycle(
    onPointerDown: State<(Long) -> Unit>,
    onPointerReleased: State<(Long) -> Unit>,
): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(
            requireUnconsumed = false,
            pass = PointerEventPass.Initial,
        )
        onPointerDown.value(monotonicTimeMillis())
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val anyPressed = event.changes.any { change -> change.pressed }
        } while (anyPressed)
        onPointerReleased.value(monotonicTimeMillis())
    }
}

@Composable
private fun FeedPage(
    item: FeedVideoItem,
    isCurrentPage: Boolean,
    isPageScrolling: Boolean,
    isFullscreen: Boolean,
    uiState: VideoPlaybackUiState,
    playbackProgress: StateFlow<VideoPlaybackProgressUiState>?,
    onRetry: () -> Unit,
    onTogglePause: () -> Unit,
    onTemporaryPlaybackSpeedChanged: (Boolean) -> Unit,
    onSeek: (Long) -> Unit,
    onToggleMute: () -> Unit,
    onOriginalMessage: () -> Unit,
    onFullscreenChanged: (Boolean) -> Unit,
    detailsVisible: Boolean,
    onShowDetails: (VideoKey) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (item.video.supportsStreaming) Modifier else Modifier.background(Color.Black),
            ),
    ) {
        if (!isCurrentPage) return@Box

        val presentation = feedPagePresentation(item, uiState.player)
        when (presentation) {
            is FeedPagePresentation.Failure -> {
                ImmersivePlaybackFailure(
                    failure = presentation.reason,
                    onRetry = onRetry,
                )
            }

            FeedPagePresentation.Unsupported -> {
                ImmersiveUnsupportedState(
                    onOriginalMessage = onOriginalMessage,
                    linkLoading = uiState.originalMessageLink is OriginalMessageLinkUiState.Loading,
                )
            }

            FeedPagePresentation.Content,
            FeedPagePresentation.Loading,
            -> {
                if (presentation == FeedPagePresentation.Content) {
                    FeedContentOverlay(
                        item = item,
                        uiState = uiState,
                        playbackProgress = playbackProgress,
                        isPageScrolling = isPageScrolling,
                        isFullscreen = isFullscreen,
                        onTogglePause = onTogglePause,
                        onTemporaryPlaybackSpeedChanged = onTemporaryPlaybackSpeedChanged,
                        onSeek = onSeek,
                        onToggleMute = onToggleMute,
                        onOriginalMessage = onOriginalMessage,
                        onFullscreenChanged = onFullscreenChanged,
                        detailsVisible = detailsVisible,
                        onShowDetails = onShowDetails,
                    )
                }
                ImmersiveVideoLoadingState(
                    video = item.video,
                    visible = presentation == FeedPagePresentation.Loading,
                )
            }
        }
    }
}

@Composable
private fun BoxScope.FeedContentOverlay(
    item: FeedVideoItem,
    uiState: VideoPlaybackUiState,
    playbackProgress: StateFlow<VideoPlaybackProgressUiState>?,
    isPageScrolling: Boolean,
    isFullscreen: Boolean,
    onTogglePause: () -> Unit,
    onTemporaryPlaybackSpeedChanged: (Boolean) -> Unit,
    onSeek: (Long) -> Unit,
    onToggleMute: () -> Unit,
    onOriginalMessage: () -> Unit,
    onFullscreenChanged: (Boolean) -> Unit,
    detailsVisible: Boolean,
    onShowDetails: (VideoKey) -> Unit,
) {
    var isScrubbing by remember(item.video.key) { mutableStateOf(false) }
    var isMetadataDimmed by remember(item.video.key) { mutableStateOf(false) }
    val isInteracting = isPageScrolling || isScrubbing
    LaunchedEffect(item.video.key, isInteracting) {
        if (isInteracting) {
            isMetadataDimmed = true
        } else if (isMetadataDimmed) {
            delay(METADATA_RESTORE_DELAY_MILLIS)
            isMetadataDimmed = false
        }
    }
    val metadataAlpha by animateFloatAsState(
        targetValue = if (isMetadataDimmed) METADATA_INTERACTION_ALPHA else 1f,
        animationSpec = tween(
            durationMillis = if (isMetadataDimmed) {
                METADATA_FADE_OUT_MILLIS
            } else {
                METADATA_FADE_IN_MILLIS
            },
        ),
        label = "video metadata interaction alpha",
    )
    val currentTogglePause = rememberUpdatedState(onTogglePause)
    val currentTemporarySpeedChanged = rememberUpdatedState(onTemporaryPlaybackSpeedChanged)

    if (!isFullscreen) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.46f)
                .graphicsLayer { alpha = metadataAlpha }
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.48f to Color.Black.copy(alpha = 0.24f),
                        1f to Color.Black.copy(alpha = 0.86f),
                    ),
                ),
        )
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (detailsVisible) {
                    Modifier
                } else {
                    Modifier.temporarySpeedTapGesture(
                        onTap = currentTogglePause,
                        onTemporarySpeedChanged = currentTemporarySpeedChanged,
                    )
                },
            )
            .then(
                if (uiState.player.isPaused || detailsVisible) {
                    Modifier.clearAndSetSemantics { }
                } else {
                    Modifier.semantics {
                        role = Role.Button
                        contentDescription = "暂停视频"
                        onClick(label = "暂停视频") {
                            currentTogglePause.value()
                            true
                        }
                    }
                },
            )
            .testTag(VideoFeedTestTags.TapSurface),
    )
    AnimatedVisibility(
        visible = uiState.player.isPaused,
        modifier = Modifier.align(Alignment.Center),
        enter = fadeIn(
            animationSpec = tween(PAUSED_OVERLAY_ANIMATION_MILLIS),
        ) + scaleIn(
            initialScale = PAUSED_OVERLAY_INITIAL_SCALE,
            animationSpec = tween(PAUSED_OVERLAY_ANIMATION_MILLIS),
        ),
        exit = fadeOut(
            animationSpec = tween(PAUSED_OVERLAY_ANIMATION_MILLIS),
        ) + scaleOut(
            targetScale = PAUSED_OVERLAY_INITIAL_SCALE,
            animationSpec = tween(PAUSED_OVERLAY_ANIMATION_MILLIS),
        ),
        label = "paused playback overlay",
    ) {
        PausedPlaybackOverlay(
            enabled = uiState.player.isPaused && !detailsVisible,
            onClick = onTogglePause,
        )
    }
    val temporarySpeedActive =
        uiState.player.playbackSpeed == VideoPlaybackSpeeds.TEMPORARY_FAST_FORWARD
    AnimatedVisibility(
        visible = temporarySpeedActive,
        modifier = Modifier
            .align(Alignment.TopCenter)
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Horizontal + WindowInsetsSides.Top,
                ),
            )
            .padding(top = TEMPORARY_SPEED_TOP_PADDING),
        enter = fadeIn(tween(TEMPORARY_SPEED_ANIMATION_MILLIS)) + scaleIn(
            initialScale = TEMPORARY_SPEED_INITIAL_SCALE,
            animationSpec = tween(TEMPORARY_SPEED_ANIMATION_MILLIS),
        ),
        exit = fadeOut(tween(TEMPORARY_SPEED_ANIMATION_MILLIS)) + scaleOut(
            targetScale = TEMPORARY_SPEED_INITIAL_SCALE,
            animationSpec = tween(TEMPORARY_SPEED_ANIMATION_MILLIS),
        ),
        label = "temporary playback speed",
    ) {
        TemporarySpeedIndicator(enabled = temporarySpeedActive)
    }
    if (uiState.showSwipeHint && !isFullscreen) {
        SwipeHint(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = 78.dp),
        )
    }
    if (!isFullscreen) {
        FeedMetadata(
            item = item,
            linkState = uiState.originalMessageLink,
            onShowDetails = onShowDetails,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .windowInsetsPadding(
                    WindowInsets.safeContent.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                    ),
                )
                .padding(start = 16.dp, end = 92.dp, bottom = 36.dp)
                .graphicsLayer { alpha = metadataAlpha }
                .semantics {
                    stateDescription = if (isMetadataDimmed) "简介已淡化" else "简介可见"
                }
                .testTag(VideoFeedTestTags.Metadata),
        )
        FeedActionRail(
            isMuted = uiState.player.isMuted,
            originalLinkLoading = uiState.originalMessageLink is OriginalMessageLinkUiState.Loading,
            interactionEnabled = !detailsVisible,
            onToggleMute = onToggleMute,
            onOriginalMessage = onOriginalMessage,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(
                    WindowInsets.safeContent.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                    ),
                )
                .padding(end = 10.dp, bottom = 162.dp),
        )
        if (item.video.isLandscapeVideo()) {
            LandscapeFullscreenPrompt(
                videoWidth = item.video.width,
                videoHeight = item.video.height,
                onClick = {
                    if (!detailsVisible) onFullscreenChanged(true)
                },
            )
        }
    } else {
        ExitFullscreenButton(
            onClick = { onFullscreenChanged(false) },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 12.dp, end = 12.dp),
        )
    }
    PlaybackProgressState(
        key = item.video.key,
        playbackProgress = playbackProgress,
        fallbackPlayer = uiState.player,
        onSeek = if (detailsVisible) ({ _ -> }) else onSeek,
        onScrubbingChanged = { isScrubbing = it },
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .windowInsetsPadding(
                WindowInsets.safeContent.only(
                    WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                ),
            )
            .padding(bottom = PROGRESS_BOTTOM_OFFSET),
    )
}

private enum class BeforeLongPressResult {
    TAP,
    CANCELLED,
}

private fun Modifier.temporarySpeedTapGesture(
    onTap: State<() -> Unit>,
    onTemporarySpeedChanged: State<(Boolean) -> Unit>,
): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        var temporarySpeedRequested = false
        try {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (down.isConsumed) return@awaitEachGesture
            val pointerId = down.id
            val touchSlop = viewConfiguration.touchSlop
            val result = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Main)
                    val change = event.changes.firstOrNull { candidate ->
                        candidate.id == pointerId
                    } ?: return@withTimeoutOrNull BeforeLongPressResult.CANCELLED
                    if (change.isConsumed) {
                        return@withTimeoutOrNull BeforeLongPressResult.CANCELLED
                    }
                    if (!change.pressed) {
                        change.consume()
                        return@withTimeoutOrNull BeforeLongPressResult.TAP
                    }
                    if ((change.position - down.position).getDistance() > touchSlop) {
                        return@withTimeoutOrNull BeforeLongPressResult.CANCELLED
                    }
                }
            }
            when (result) {
                BeforeLongPressResult.TAP -> onTap.value()
                BeforeLongPressResult.CANCELLED -> Unit
                null -> {
                    temporarySpeedRequested = true
                    onTemporarySpeedChanged.value(true)
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val change = event.changes.firstOrNull { candidate ->
                            candidate.id == pointerId
                        }
                        event.changes.forEach { pointerChange -> pointerChange.consume() }
                    } while (change?.pressed == true)
                }
            }
        } finally {
            if (temporarySpeedRequested) {
                onTemporarySpeedChanged.value(false)
            }
        }
    }
}

@Composable
private fun TemporarySpeedIndicator(
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .then(
                if (enabled) {
                    Modifier.semantics(mergeDescendants = true) {
                        contentDescription = TEMPORARY_SPEED_LABEL
                    }
                } else {
                    Modifier.clearAndSetSemantics { }
                },
            )
            .testTag(VideoFeedTestTags.TemporarySpeed)
            .background(
                color = Color.Black.copy(alpha = 0.68f),
                shape = RoundedCornerShape(18.dp),
            )
            .padding(horizontal = 16.dp, vertical = 9.dp),
    ) {
        Text(
            text = TEMPORARY_SPEED_LABEL,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SwipeHint(modifier: Modifier = Modifier) {
    var animationStarted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { animationStarted = true }
    val alpha by animateFloatAsState(
        targetValue = if (animationStarted) 1f else 0f,
        animationSpec = tween(SWIPE_HINT_ENTRANCE_MILLIS),
        label = "swipe hint alpha",
    )
    val verticalOffset by animateDpAsState(
        targetValue = if (animationStarted) 0.dp else 10.dp,
        animationSpec = tween(SWIPE_HINT_ENTRANCE_MILLIS),
        label = "swipe hint vertical offset",
    )
    Column(
        modifier = modifier
            .offset(y = verticalOffset)
            .graphicsLayer { this.alpha = alpha }
            .background(Color.Black.copy(alpha = 0.52f), RoundedCornerShape(20.dp))
            .semantics { contentDescription = "上滑浏览下一条教学提示" }
            .testTag(VideoFeedTestTags.SwipeHint)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Canvas(modifier = Modifier.size(width = 24.dp, height = 28.dp)) {
            val strokeWidth = 2.dp.toPx()
            val centerX = size.width / 2f
            drawLine(
                color = Color.White.copy(alpha = 0.92f),
                start = Offset(centerX, size.height * 0.84f),
                end = Offset(centerX, size.height * 0.18f),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = Color.White.copy(alpha = 0.92f),
                start = Offset(centerX, size.height * 0.18f),
                end = Offset(size.width * 0.28f, size.height * 0.42f),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = Color.White.copy(alpha = 0.92f),
                start = Offset(centerX, size.height * 0.18f),
                end = Offset(size.width * 0.72f, size.height * 0.42f),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
        }
        Text(
            text = "上滑浏览下一条",
            color = Color.White.copy(alpha = 0.94f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun PlaybackProgressState(
    key: com.qixuan.channelvideoflow.model.video.VideoKey,
    playbackProgress: StateFlow<VideoPlaybackProgressUiState>?,
    fallbackPlayer: com.qixuan.channelvideoflow.player.VideoPlayerSnapshot,
    onSeek: (Long) -> Unit,
    onScrubbingChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val collectedProgress = playbackProgress?.collectAsStateWithLifecycle()?.value
    val progress = collectedProgress ?: VideoPlaybackProgressUiState(
        key = fallbackPlayer.playbackState.videoKeyOrNull(),
        positionMillis = fallbackPlayer.positionMillis,
        durationMillis = fallbackPlayer.durationMillis,
        bufferedPositionMillis = fallbackPlayer.bufferedPositionMillis,
        isSeekable = fallbackPlayer.isSeekable,
    )
    val aligned = if (progress.key == key) progress else VideoPlaybackProgressUiState(key = key)
    PlaybackProgressBar(
        positionMillis = aligned.positionMillis,
        durationMillis = aligned.durationMillis,
        isSeekable = aligned.isSeekable,
        onSeek = onSeek,
        onScrubbingChanged = onScrubbingChanged,
        modifier = modifier,
    )
}

@Composable
private fun FeedMetadata(
    item: FeedVideoItem,
    linkState: OriginalMessageLinkUiState,
    onShowDetails: (VideoKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    val caption = item.video.caption
    val tagsText = item.video.tags.joinToString(separator = "  ") { tag -> tag.displayName }
    var captionOverflow by remember(item.video.key, caption) { mutableStateOf(false) }
    var tagsOverflow by remember(item.video.key, tagsText) { mutableStateOf(false) }
    val detailsAvailable = captionOverflow || tagsOverflow
    val openDetails = { onShowDetails(item.video.key) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(18.dp)
                    .background(FEED_ACCENT, RoundedCornerShape(2.dp)),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = item.channelTitle,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (caption.isNotBlank()) {
            Text(
                text = caption,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (detailsAvailable) {
                            Modifier
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(
                                    role = Role.Button,
                                    onClick = openDetails,
                                )
                                .semantics {
                                    contentDescription = "展开视频详情"
                                }
                                .padding(vertical = 4.dp)
                        } else {
                            Modifier
                        },
                    ),
                color = Color.White.copy(alpha = 0.94f),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { result ->
                    if (captionOverflow != result.hasVisualOverflow) {
                        captionOverflow = result.hasVisualOverflow
                    }
                },
            )
        }
        if (item.video.tags.isNotEmpty()) {
            Text(
                text = tagsText,
                color = FEED_ACCENT,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { result ->
                    if (tagsOverflow != result.hasVisualOverflow) {
                        tagsOverflow = result.hasVisualOverflow
                    }
                },
            )
        }
        if (detailsAvailable) {
            TextButton(
                onClick = openDetails,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag(VideoFeedTestTags.DetailsExpand)
                    .semantics { contentDescription = "展开视频详情" },
            ) {
                Text(
                    text = "展开",
                    color = FEED_ACCENT,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Text(
            text = formatPublishTime(item.video.publishTime),
            color = Color.White.copy(alpha = 0.58f),
            style = MaterialTheme.typography.bodySmall,
        )
        when (linkState) {
            OriginalMessageLinkUiState.Idle -> Unit
            OriginalMessageLinkUiState.Loading -> Text(
                "正在获取原消息链接…",
                color = Color.White.copy(alpha = 0.72f),
                style = MaterialTheme.typography.bodySmall,
            )
            is OriginalMessageLinkUiState.Unavailable -> Text(
                linkState.message,
                color = Color(0xFFFFB4AB),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun VideoDetailsBottomSheet(
    item: FeedVideoItem,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val maxContentHeight = LocalConfiguration.current.screenHeightDp.dp * 0.82f
    var dismissInProgress by remember(item.video.key) { mutableStateOf(false) }
    val dismissAnimated = {
        if (!dismissInProgress) {
            dismissInProgress = true
            coroutineScope.launch {
                sheetState.hide()
                if (!sheetState.isVisible) onDismiss()
                dismissInProgress = false
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier
            .testTag(VideoFeedTestTags.DetailsSheet)
            .semantics { paneTitle = "视频详情" },
        containerColor = ChannelVideoFlowTokens.Feed.elevatedGraphite,
        contentColor = Color.White,
        scrimColor = Color.Black.copy(alpha = 0.78f),
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = Color.White.copy(alpha = 0.38f),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxContentHeight)
                .verticalScroll(scrollState)
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                    ),
                )
                .padding(start = 24.dp, end = 16.dp, bottom = 24.dp)
                .testTag(VideoFeedTestTags.DetailsContent),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "视频详情",
                    modifier = Modifier.semantics { heading() },
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                IconButton(
                    onClick = dismissAnimated,
                    enabled = !dismissInProgress,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag(VideoFeedTestTags.DetailsClose),
                ) {
                    FeedIcon(
                        type = FeedIconType.CLOSE,
                        description = "关闭视频详情",
                        tint = Color.White.copy(alpha = 0.88f),
                    )
                }
            }
            if (item.channelTitle.isNotBlank()) {
                VideoDetailsSection(label = "频道") {
                    Text(
                        text = item.channelTitle,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            if (item.video.caption.isNotBlank()) {
                VideoDetailsSection(label = "文案") {
                    Text(
                        text = item.video.caption,
                        modifier = Modifier.testTag(VideoFeedTestTags.DetailsCaption),
                        color = Color.White.copy(alpha = 0.92f),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            if (item.video.tags.isNotEmpty()) {
                VideoDetailsSection(label = "标签") {
                    Text(
                        text = item.video.tags.joinToString(separator = "  ") { tag ->
                            tag.displayName
                        },
                        modifier = Modifier.testTag(VideoFeedTestTags.DetailsTags),
                        color = FEED_ACCENT,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            if (item.video.publishTime > 0L) {
                VideoDetailsSection(label = "发布时间") {
                    Text(
                        text = formatPublishTime(item.video.publishTime),
                        modifier = Modifier.testTag(VideoFeedTestTags.DetailsPublishTime),
                        color = Color.White.copy(alpha = 0.72f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoDetailsSection(
    label: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.52f),
            style = MaterialTheme.typography.labelMedium,
        )
        content()
    }
}

@Composable
private fun BoxScope.LandscapeFullscreenPrompt(
    videoWidth: Int,
    videoHeight: Int,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val fittedVideoHeight = maxWidth * videoHeight.toFloat() / videoWidth.toFloat()
        val videoBottom = (maxHeight + fittedVideoHeight) / 2
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = videoBottom + 14.dp)
                .controlPressScale(interactionSource)
                .clip(RoundedCornerShape(18.dp))
                .background(Color.Black.copy(alpha = 0.28f))
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.42f),
                    shape = RoundedCornerShape(18.dp),
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    onClick = onClick,
                )
                .semantics {
                    role = Role.Button
                    contentDescription = "全屏观看"
                }
                .testTag(VideoFeedTestTags.Fullscreen)
                .sizeIn(minHeight = ChannelVideoFlowTokens.Sizes.touchTarget)
                .padding(horizontal = 14.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FeedIcon(
                type = FeedIconType.FULLSCREEN,
                description = "全屏观看",
                tint = Color.White.copy(alpha = 0.92f),
                iconSize = 16.dp,
            )
            Text(
                text = "全屏观看",
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun ExitFullscreenButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    IconButton(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = modifier
            .controlPressScale(interactionSource)
            .windowInsetsPadding(
                WindowInsets.safeContent.only(
                    WindowInsetsSides.Horizontal + WindowInsetsSides.Top,
                ),
            )
            .size(ChannelVideoFlowTokens.Sizes.touchTarget)
            .background(Color.Black.copy(alpha = 0.38f), CircleShape)
            .testTag(VideoFeedTestTags.ExitFullscreen),
    ) {
        FeedIcon(
            type = FeedIconType.EXIT_FULLSCREEN,
            description = "退出全屏",
            tint = Color.White.copy(alpha = 0.92f),
            iconSize = 22.dp,
        )
    }
}

@Composable
private fun FeedActionRail(
    isMuted: Boolean,
    originalLinkLoading: Boolean,
    interactionEnabled: Boolean,
    onToggleMute: () -> Unit,
    onOriginalMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.width(70.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        FeedActionButton(
            label = if (isMuted) "声音" else "静音",
            icon = if (isMuted) FeedIconType.MUTED else FeedIconType.VOLUME,
            testTag = VideoFeedTestTags.Mute,
            enabled = interactionEnabled,
            onClick = onToggleMute,
        )
        FeedActionButton(
            label = "原消息",
            icon = FeedIconType.EXTERNAL_LINK,
            testTag = VideoFeedTestTags.OriginalLink,
            enabled = interactionEnabled && !originalLinkLoading,
            onClick = onOriginalMessage,
        )
    }
}

@Composable
private fun FeedActionButton(
    label: String,
    icon: FeedIconType,
    testTag: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            interactionSource = interactionSource,
            modifier = Modifier
                .controlPressScale(
                    interactionSource = interactionSource,
                    enabled = enabled,
                )
                .size(50.dp)
                .background(
                    ChannelVideoFlowTokens.Feed.overlay.copy(
                        alpha = if (enabled) 0.82f else 0.42f,
                    ),
                    CircleShape,
                )
                .border(
                    width = 1.dp,
                    color = ChannelVideoFlowTokens.Feed.outline,
                    shape = CircleShape,
                )
                .testTag(testTag),
        ) {
            FeedIcon(
                type = icon,
                description = label,
                tint = Color.White.copy(alpha = if (enabled) 1f else 0.44f),
            )
        }
        Text(
            text = label,
            color = Color.White.copy(alpha = if (enabled) 0.92f else 0.44f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun PausedPlaybackOverlay(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val interactionModifier = if (enabled) {
        Modifier
            .clickable(onClick = onClick)
            .semantics { contentDescription = "继续播放" }
    } else {
        Modifier.clearAndSetSemantics { }
    }

    Box(
        modifier = Modifier
            .size(76.dp)
            .background(ChannelVideoFlowTokens.Feed.overlay.copy(alpha = 0.78f), CircleShape)
            .border(1.dp, ChannelVideoFlowTokens.Feed.outline, CircleShape)
            .testTag(VideoFeedTestTags.PausedOverlay)
            .then(interactionModifier),
        contentAlignment = Alignment.Center,
    ) {
        FeedIcon(
            type = FeedIconType.PLAY,
            description = "继续播放",
            tint = Color.White.copy(alpha = 0.78f),
        )
    }
}

@Composable
private fun Modifier.controlPressScale(
    interactionSource: MutableInteractionSource,
    enabled: Boolean = true,
): Modifier {
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressed = enabled && isPressed
    val scale by animateFloatAsState(
        targetValue = if (pressed) CONTROL_PRESSED_SCALE else 1f,
        animationSpec = tween(
            durationMillis = if (pressed) {
                CONTROL_PRESS_IN_MILLIS
            } else {
                CONTROL_PRESS_OUT_MILLIS
            },
        ),
        label = "feed control press scale",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

@Composable
private fun PlaybackProgressBar(
    positionMillis: Long,
    durationMillis: Long,
    isSeekable: Boolean,
    onSeek: (Long) -> Unit,
    onScrubbingChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!isSeekable || durationMillis <= 0L) return

    val safeDuration = durationMillis.coerceAtLeast(1L)
    var widthPx by remember { mutableIntStateOf(0) }
    var scrubPositionMillis by remember(durationMillis) {
        mutableLongStateOf(positionMillis.coerceIn(0L, safeDuration))
    }
    var isScrubbing by remember { mutableStateOf(false) }

    LaunchedEffect(positionMillis, durationMillis, isScrubbing) {
        if (!isScrubbing) {
            scrubPositionMillis = positionMillis.coerceIn(0L, safeDuration)
        }
    }

    fun positionForX(x: Float): Long {
        if (widthPx <= 0) return scrubPositionMillis
        return ((x / widthPx.toFloat()).coerceIn(0f, 1f) * safeDuration)
            .roundToLong()
            .coerceIn(0L, safeDuration)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(ChannelVideoFlowTokens.Sizes.touchTarget)
            .onSizeChanged { widthPx = it.width }
            .pointerInput(durationMillis, widthPx) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val dragged = drag(down.id) { change ->
                        if (!isScrubbing) {
                            isScrubbing = true
                            onScrubbingChanged(true)
                        }
                        scrubPositionMillis = positionForX(change.position.x)
                        change.consume()
                    }
                    if (isScrubbing) {
                        isScrubbing = false
                        onScrubbingChanged(false)
                    }
                    if (dragged) {
                        onSeek(scrubPositionMillis.coerceIn(0L, safeDuration))
                    } else {
                        onSeek(positionForX(down.position.x))
                    }
                }
            }
            .testTag(VideoFeedTestTags.Progress),
    ) {
        Canvas(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(if (isScrubbing) 5.dp else 2.dp),
        ) {
            val trackY = size.height / 2f
            val fraction = (scrubPositionMillis.toFloat() / safeDuration).coerceIn(0f, 1f)
            val activeX = size.width * fraction
            drawLine(
                color = Color.White.copy(alpha = if (isScrubbing) 0.5f else 0.32f),
                start = Offset(0f, trackY),
                end = Offset(size.width, trackY),
                strokeWidth = if (isScrubbing) 4.dp.toPx() else 1.5.dp.toPx(),
                cap = StrokeCap.Butt,
            )
            drawLine(
                color = Color.White.copy(alpha = 0.96f),
                start = Offset(0f, trackY),
                end = Offset(activeX, trackY),
                strokeWidth = if (isScrubbing) 4.dp.toPx() else 1.5.dp.toPx(),
                cap = StrokeCap.Butt,
            )
            drawCircle(
                color = Color.White.copy(alpha = if (isScrubbing) 1f else 0.84f),
                radius = if (isScrubbing) 5.dp.toPx() else 2.5.dp.toPx(),
                center = Offset(activeX, trackY),
            )
        }
        if (isScrubbing) {
            Text(
                text = "${formatPlaybackTime(scrubPositionMillis)} / ${formatPlaybackTime(safeDuration)}",
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp),
                color = Color.White.copy(alpha = 0.94f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

private fun formatPlaybackTime(timeMillis: Long): String {
    val totalSeconds = (timeMillis.coerceAtLeast(0L) / 1_000L).toInt()
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3_600
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

@Composable
private fun ProtectedContentWindowEffect(isProtected: Boolean) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val controller = remember(activity) {
        activity?.window?.let(::WindowSecurityController)
    }

    DisposableEffect(controller, isProtected) {
        controller?.setProtectedContent(isProtected)
        onDispose {
            if (isProtected) controller?.setProtectedContent(false)
        }
    }
}

@Composable
private fun FullscreenSystemUiEffect(isFullscreen: Boolean) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(activity, lifecycleOwner, isFullscreen) {
        if (activity == null) {
            return@DisposableEffect onDispose {}
        }
        val insetsController = WindowCompat.getInsetsController(
            activity.window,
            activity.window.decorView,
        )
        val previousOrientation = activity.requestedOrientation
        val previousLightStatusBars = insetsController.isAppearanceLightStatusBars
        val previousLightNavigationBars = insetsController.isAppearanceLightNavigationBars

        fun applyForegroundState() {
            insetsController.isAppearanceLightStatusBars = false
            insetsController.isAppearanceLightNavigationBars = false
            if (isFullscreen) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                insetsController.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }

        fun restoreWindowState() {
            insetsController.show(WindowInsetsCompat.Type.systemBars())
            insetsController.isAppearanceLightStatusBars = previousLightStatusBars
            insetsController.isAppearanceLightNavigationBars = previousLightNavigationBars
            activity.requestedOrientation = previousOrientation
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> applyForegroundState()
                Lifecycle.Event.ON_STOP -> restoreWindowState()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            applyForegroundState()
        }

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            restoreWindowState()
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun IndexedVideo.isLandscapeVideo(): Boolean = width > height && height > 0

private fun VideoPlaybackState.videoKeyOrNull(): VideoKey? = when (this) {
    VideoPlaybackState.Idle -> null
    is VideoPlaybackState.Loading -> video.key
    is VideoPlaybackState.Ready -> video.key
    is VideoPlaybackState.Unsupported -> video.key
    is VideoPlaybackState.Failed -> video.key
}

@Composable
private fun ImmersiveLoadingState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
    ) {
        CircularProgressIndicator(
            modifier = Modifier
                .size(46.dp)
                .testTag(VideoFeedTestTags.Loading),
            color = Color.White.copy(alpha = 0.82f),
            strokeWidth = 3.dp,
        )
        Text(
            text = "正在加载视频",
            color = Color.White,
            fontSize = 21.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "正在读取已选择频道的视频索引",
            color = Color.White.copy(alpha = 0.46f),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun ImmersiveVideoLoadingState(
    video: IndexedVideo,
    visible: Boolean,
) {
    val paletteIndex = videoPosterPaletteIndex(video.key)
    val palette = VIDEO_POSTER_PALETTES[paletteIndex]
    val ambientBrush = remember(paletteIndex) {
        Brush.linearGradient(
            colors = listOf(
                palette.upper,
                palette.base,
                palette.lower,
            ),
        )
    }
    val vignetteBrush = remember(paletteIndex) {
        Brush.radialGradient(
            0f to palette.accent.copy(alpha = 0.20f),
            0.52f to Color.Transparent,
            1f to Color.Black.copy(alpha = 0.74f),
        )
    }
    val cardBrush = remember(paletteIndex) {
        Brush.linearGradient(
            colors = listOf(
                palette.accent.copy(alpha = 0.28f),
                palette.base.copy(alpha = 0.98f),
                Color.Black.copy(alpha = 0.62f),
            ),
        )
    }
    val posterAlpha = remember(video.key) {
        Animatable(if (visible) 1f else 0f)
    }
    var showProgress by remember(video.key) { mutableStateOf(false) }

    LaunchedEffect(video.key, visible) {
        showProgress = false
        if (visible) {
            delay(ChannelVideoFlowTokens.Motion.loadingDisclosureMillis)
            showProgress = true
        }
    }

    LaunchedEffect(video.key, visible) {
        if (visible) {
            posterAlpha.snapTo(1f)
        } else if (posterAlpha.value > 0f) {
            posterAlpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = VIDEO_POSTER_FADE_OUT_MILLIS),
            )
        }
    }

    if (visible || posterAlpha.value > 0f) {
        val renderedAlpha = if (visible) 1f else posterAlpha.value
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = renderedAlpha }
                .background(palette.base)
                .background(ambientBrush)
                .background(vignetteBrush)
                .testTag(VideoFeedTestTags.LoadingPoster)
                .semantics {
                    contentDescription = "正在准备视频"
                    loadingPosterAlpha = renderedAlpha
                    loadingPosterPalette = paletteIndex
                    loadingPosterVideoIdentity = video.key.posterIdentity()
                },
            contentAlignment = Alignment.Center,
        ) {
            val aspectRatio = video.posterAspectRatio()
            val maximumCardWidth = if (aspectRatio >= 1f) maxWidth * 0.82f else maxWidth * 0.66f
            val maximumCardHeight = maxHeight * 0.56f
            val cardWidth = minOf(maximumCardWidth, maximumCardHeight * aspectRatio)
            val cardHeight = cardWidth / aspectRatio

            Box(
                modifier = Modifier
                    .width(cardWidth)
                    .height(cardHeight)
                    .clip(RoundedCornerShape(26.dp))
                    .background(cardBrush)
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.10f),
                        shape = RoundedCornerShape(26.dp),
                    )
                    .clearAndSetSemantics { },
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    palette.accent.copy(alpha = 0.58f),
                                    Color.Transparent,
                                ),
                            ),
                        ),
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(58.dp)
                            .background(
                                color = Color.Black.copy(alpha = 0.24f),
                                shape = CircleShape,
                            )
                            .border(
                                width = 1.dp,
                                color = Color.White.copy(alpha = 0.10f),
                                shape = CircleShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (showProgress) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(30.dp)
                                    .testTag(VideoFeedTestTags.Loading),
                                color = Color.White.copy(alpha = 0.76f),
                                strokeWidth = 2.5.dp,
                            )
                        } else {
                            Text(
                                text = "CVF",
                                color = Color.White.copy(alpha = 0.78f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    Text(
                        text = "正在准备视频",
                        color = Color.White.copy(alpha = 0.94f),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "真实首帧到达后自动播放",
                        color = Color.White.copy(alpha = 0.48f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun ImmersiveEmptyState(onBack: () -> Unit) {
    ImmersiveStatusState(
        icon = FeedStateIcon.EMPTY,
        title = "暂无可播放视频",
        message = "请调整频道选择，或等待视频索引完成",
        actionLabel = "返回频道选择",
        actionTestTag = VideoFeedTestTags.EmptyAction,
        titleTestTag = VideoFeedTestTags.Empty,
        onAction = onBack,
    )
}

@Composable
private fun ImmersiveMessageUnavailableState(onBack: () -> Unit) {
    ImmersiveStatusState(
        icon = FeedStateIcon.UNSUPPORTED,
        title = "视频已不可播放",
        message = "消息已删除或不再是普通视频",
        actionLabel = "返回频道选择",
        actionTestTag = VideoFeedTestTags.EmptyAction,
        onAction = onBack,
    )
}

@Composable
private fun ImmersivePlaybackFailure(
    failure: VideoPlaybackFailure,
    onRetry: () -> Unit,
) {
    val presentation = failure.presentation()
    ImmersiveStatusState(
        icon = presentation.icon,
        title = presentation.title,
        message = presentation.message,
        actionLabel = "重试",
        actionTestTag = VideoFeedTestTags.Retry,
        onAction = onRetry,
    )
}

@Composable
private fun ImmersiveUnsupportedState(
    onOriginalMessage: () -> Unit,
    linkLoading: Boolean,
) {
    ImmersiveStatusState(
        icon = FeedStateIcon.UNSUPPORTED,
        title = "该视频暂不支持流式播放。",
        message = "可继续上下滑动，或前往 Telegram 查看原消息",
        actionLabel = if (linkLoading) "正在打开…" else "打开原消息",
        actionTestTag = VideoFeedTestTags.OriginalLink,
        actionEnabled = !linkLoading,
        onAction = onOriginalMessage,
    )
}

@Composable
private fun ImmersiveStatusState(
    icon: FeedStateIcon,
    title: String,
    message: String,
    actionLabel: String,
    actionTestTag: String,
    onAction: () -> Unit,
    actionEnabled: Boolean = true,
    titleTestTag: String? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .windowInsetsPadding(WindowInsets.safeContent)
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        FeedStateGraphic(icon)
        Spacer(modifier = Modifier.height(30.dp))
        Text(
            text = title,
            modifier = if (titleTestTag == null) Modifier else Modifier.testTag(titleTestTag),
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = message,
            color = Color.White.copy(alpha = 0.46f),
            fontSize = 16.sp,
        )
        Spacer(modifier = Modifier.height(54.dp))
        TextButton(
            onClick = onAction,
            enabled = actionEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    if (actionEnabled) FEED_BUTTON else FEED_BUTTON.copy(alpha = 0.45f),
                )
                .testTag(actionTestTag),
        ) {
            Text(
                text = actionLabel,
                color = Color.White.copy(alpha = if (actionEnabled) 1f else 0.48f),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun FeedStateGraphic(icon: FeedStateIcon) {
    Canvas(
        modifier = Modifier
            .size(92.dp)
            .semantics { contentDescription = icon.description },
    ) {
        val color = Color(0xFF3A3A3A)
        val strokeWidth = 8.dp.toPx()
        when (icon) {
            FeedStateIcon.NETWORK -> {
                drawArc(
                    color = color,
                    startAngle = 215f,
                    sweepAngle = 110f,
                    useCenter = false,
                    topLeft = Offset(size.width * 0.08f, size.height * 0.08f),
                    size = Size(size.width * 0.84f, size.height * 0.84f),
                    style = Stroke(strokeWidth, cap = StrokeCap.Round),
                )
                drawArc(
                    color = color,
                    startAngle = 215f,
                    sweepAngle = 110f,
                    useCenter = false,
                    topLeft = Offset(size.width * 0.24f, size.height * 0.28f),
                    size = Size(size.width * 0.52f, size.height * 0.52f),
                    style = Stroke(strokeWidth, cap = StrokeCap.Round),
                )
                drawArc(
                    color = color,
                    startAngle = 215f,
                    sweepAngle = 110f,
                    useCenter = false,
                    topLeft = Offset(size.width * 0.37f, size.height * 0.48f),
                    size = Size(size.width * 0.26f, size.height * 0.26f),
                    style = Stroke(strokeWidth, cap = StrokeCap.Round),
                )
                drawCircle(
                    color = color,
                    radius = size.width * 0.075f,
                    center = Offset(size.width * 0.5f, size.height * 0.78f),
                )
                drawLine(
                    color = Color.Black,
                    start = Offset(size.width * 0.5f, size.height * 0.02f),
                    end = Offset(size.width * 0.5f, size.height * 0.67f),
                    strokeWidth = 5.dp.toPx(),
                )
            }
            FeedStateIcon.EMPTY -> {
                drawCircle(
                    color = color,
                    radius = size.width * 0.39f,
                    style = Stroke(strokeWidth),
                )
                val play = Path().apply {
                    moveTo(size.width * 0.42f, size.height * 0.33f)
                    lineTo(size.width * 0.70f, size.height * 0.50f)
                    lineTo(size.width * 0.42f, size.height * 0.67f)
                    close()
                }
                drawPath(play, color)
            }
            FeedStateIcon.UNSUPPORTED -> {
                drawCircle(
                    color = color,
                    radius = size.width * 0.39f,
                    style = Stroke(strokeWidth),
                )
                val play = Path().apply {
                    moveTo(size.width * 0.42f, size.height * 0.33f)
                    lineTo(size.width * 0.70f, size.height * 0.50f)
                    lineTo(size.width * 0.42f, size.height * 0.67f)
                    close()
                }
                drawPath(play, color)
                drawLine(
                    color = Color.Black,
                    start = Offset(size.width * 0.22f, size.height * 0.22f),
                    end = Offset(size.width * 0.78f, size.height * 0.78f),
                    strokeWidth = 9.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = color,
                    start = Offset(size.width * 0.20f, size.height * 0.20f),
                    end = Offset(size.width * 0.80f, size.height * 0.80f),
                    strokeWidth = 5.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
            FeedStateIcon.ERROR -> {
                drawCircle(
                    color = color,
                    radius = size.width * 0.39f,
                    style = Stroke(strokeWidth),
                )
                drawLine(
                    color = color,
                    start = Offset(size.width * 0.5f, size.height * 0.29f),
                    end = Offset(size.width * 0.5f, size.height * 0.58f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
                drawCircle(
                    color = color,
                    radius = size.width * 0.05f,
                    center = Offset(size.width * 0.5f, size.height * 0.72f),
                )
            }
        }
    }
}

@Composable
private fun FeedIcon(
    type: FeedIconType,
    description: String,
    tint: Color = Color.White,
    iconSize: Dp = 24.dp,
) {
    Canvas(
        modifier = Modifier
            .size(iconSize)
            .semantics { contentDescription = description },
    ) {
        val strokeWidth = 2.1.dp.toPx()
        when (type) {
            FeedIconType.BACK -> {
                drawLine(
                    tint,
                    Offset(size.width * 0.67f, size.height * 0.18f),
                    Offset(size.width * 0.31f, size.height * 0.50f),
                    strokeWidth,
                    StrokeCap.Round,
                )
                drawLine(
                    tint,
                    Offset(size.width * 0.31f, size.height * 0.50f),
                    Offset(size.width * 0.67f, size.height * 0.82f),
                    strokeWidth,
                    StrokeCap.Round,
                )
            }
            FeedIconType.PLAY -> {
                val path = Path().apply {
                    moveTo(size.width * 0.34f, size.height * 0.22f)
                    lineTo(size.width * 0.76f, size.height * 0.50f)
                    lineTo(size.width * 0.34f, size.height * 0.78f)
                    close()
                }
                drawPath(path, tint)
            }
            FeedIconType.PAUSE -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(size.width * 0.28f, size.height * 0.22f),
                    size = Size(size.width * 0.15f, size.height * 0.56f),
                )
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(size.width * 0.57f, size.height * 0.22f),
                    size = Size(size.width * 0.15f, size.height * 0.56f),
                )
            }
            FeedIconType.VOLUME,
            FeedIconType.MUTED,
            -> {
                val speaker = Path().apply {
                    moveTo(size.width * 0.20f, size.height * 0.42f)
                    lineTo(size.width * 0.37f, size.height * 0.42f)
                    lineTo(size.width * 0.54f, size.height * 0.26f)
                    lineTo(size.width * 0.54f, size.height * 0.74f)
                    lineTo(size.width * 0.37f, size.height * 0.58f)
                    lineTo(size.width * 0.20f, size.height * 0.58f)
                    close()
                }
                drawPath(speaker, tint)
                if (type == FeedIconType.VOLUME) {
                    drawArc(
                        color = tint,
                        startAngle = -46f,
                        sweepAngle = 92f,
                        useCenter = false,
                        topLeft = Offset(size.width * 0.46f, size.height * 0.26f),
                        size = Size(size.width * 0.34f, size.height * 0.48f),
                        style = Stroke(strokeWidth, cap = StrokeCap.Round),
                    )
                } else {
                    drawLine(
                        tint,
                        Offset(size.width * 0.64f, size.height * 0.37f),
                        Offset(size.width * 0.84f, size.height * 0.63f),
                        strokeWidth,
                        StrokeCap.Round,
                    )
                    drawLine(
                        tint,
                        Offset(size.width * 0.84f, size.height * 0.37f),
                        Offset(size.width * 0.64f, size.height * 0.63f),
                        strokeWidth,
                        StrokeCap.Round,
                    )
                }
            }
            FeedIconType.EXTERNAL_LINK -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(size.width * 0.16f, size.height * 0.33f),
                    size = Size(size.width * 0.52f, size.height * 0.51f),
                    style = Stroke(strokeWidth),
                )
                drawLine(
                    tint,
                    Offset(size.width * 0.44f, size.height * 0.56f),
                    Offset(size.width * 0.82f, size.height * 0.18f),
                    strokeWidth,
                    StrokeCap.Round,
                )
                drawLine(
                    tint,
                    Offset(size.width * 0.58f, size.height * 0.18f),
                    Offset(size.width * 0.82f, size.height * 0.18f),
                    strokeWidth,
                    StrokeCap.Round,
                )
                drawLine(
                    tint,
                    Offset(size.width * 0.82f, size.height * 0.18f),
                    Offset(size.width * 0.82f, size.height * 0.42f),
                    strokeWidth,
                    StrokeCap.Round,
                )
            }
            FeedIconType.FULLSCREEN,
            FeedIconType.EXIT_FULLSCREEN,
            -> {
                val outer = if (type == FeedIconType.FULLSCREEN) 0.16f else 0.34f
                val inner = if (type == FeedIconType.FULLSCREEN) 0.38f else 0.16f
                listOf(
                    Triple(
                        Offset(size.width * outer, size.height * inner),
                        Offset(size.width * outer, size.height * outer),
                        Offset(size.width * inner, size.height * outer),
                    ),
                    Triple(
                        Offset(size.width * (1f - outer), size.height * inner),
                        Offset(size.width * (1f - outer), size.height * outer),
                        Offset(size.width * (1f - inner), size.height * outer),
                    ),
                    Triple(
                        Offset(size.width * outer, size.height * (1f - inner)),
                        Offset(size.width * outer, size.height * (1f - outer)),
                        Offset(size.width * inner, size.height * (1f - outer)),
                    ),
                    Triple(
                        Offset(size.width * (1f - outer), size.height * (1f - inner)),
                        Offset(size.width * (1f - outer), size.height * (1f - outer)),
                        Offset(size.width * (1f - inner), size.height * (1f - outer)),
                    ),
                ).forEach { (start, corner, end) ->
                    drawLine(tint, start, corner, strokeWidth, StrokeCap.Round)
                    drawLine(tint, corner, end, strokeWidth, StrokeCap.Round)
                }
            }
            FeedIconType.LOGOUT -> {
                drawLine(
                    tint,
                    Offset(size.width * 0.26f, size.height * 0.20f),
                    Offset(size.width * 0.26f, size.height * 0.80f),
                    strokeWidth,
                    StrokeCap.Round,
                )
                drawLine(
                    tint,
                    Offset(size.width * 0.26f, size.height * 0.20f),
                    Offset(size.width * 0.52f, size.height * 0.20f),
                    strokeWidth,
                    StrokeCap.Round,
                )
                drawLine(
                    tint,
                    Offset(size.width * 0.26f, size.height * 0.80f),
                    Offset(size.width * 0.52f, size.height * 0.80f),
                    strokeWidth,
                    StrokeCap.Round,
                )
                drawLine(
                    tint,
                    Offset(size.width * 0.46f, size.height * 0.50f),
                    Offset(size.width * 0.82f, size.height * 0.50f),
                    strokeWidth,
                    StrokeCap.Round,
                )
                drawLine(
                    tint,
                    Offset(size.width * 0.70f, size.height * 0.38f),
                    Offset(size.width * 0.82f, size.height * 0.50f),
                    strokeWidth,
                    StrokeCap.Round,
                )
                drawLine(
                    tint,
                    Offset(size.width * 0.82f, size.height * 0.50f),
                    Offset(size.width * 0.70f, size.height * 0.62f),
                    strokeWidth,
                    StrokeCap.Round,
                )
            }
            FeedIconType.CLOSE -> {
                drawLine(
                    tint,
                    Offset(size.width * 0.24f, size.height * 0.24f),
                    Offset(size.width * 0.76f, size.height * 0.76f),
                    strokeWidth,
                    StrokeCap.Round,
                )
                drawLine(
                    tint,
                    Offset(size.width * 0.76f, size.height * 0.24f),
                    Offset(size.width * 0.24f, size.height * 0.76f),
                    strokeWidth,
                    StrokeCap.Round,
                )
            }
        }
    }
}

private fun VideoPlaybackFailure.presentation(): FailurePresentation = when (this) {
    VideoPlaybackFailure.NETWORK -> FailurePresentation(
        icon = FeedStateIcon.NETWORK,
        title = "网络错误",
        message = "请检查网络连接后重试",
    )
    VideoPlaybackFailure.TIMEOUT -> FailurePresentation(
        icon = FeedStateIcon.NETWORK,
        title = "加载超时",
        message = "网络响应较慢，请稍后重试",
    )
    VideoPlaybackFailure.FILE_UNAVAILABLE -> FailurePresentation(
        icon = FeedStateIcon.ERROR,
        title = "视频暂时不可用",
        message = "Telegram 文件已失效，请重试",
    )
    VideoPlaybackFailure.MESSAGE_UNAVAILABLE -> FailurePresentation(
        icon = FeedStateIcon.UNSUPPORTED,
        title = "视频已不可播放",
        message = "消息已删除或不再是普通视频",
    )
    VideoPlaybackFailure.DECODER_UNSUPPORTED -> FailurePresentation(
        icon = FeedStateIcon.UNSUPPORTED,
        title = "无法播放",
        message = "设备不支持该视频编码",
    )
    VideoPlaybackFailure.PLAYER -> FailurePresentation(
        icon = FeedStateIcon.ERROR,
        title = "播放出错",
        message = "播放器发生错误，请重试",
    )
    VideoPlaybackFailure.UNKNOWN -> FailurePresentation(
        icon = FeedStateIcon.ERROR,
        title = "播放出错",
        message = "发生未知错误，请重试",
    )
}

private data class FailurePresentation(
    val icon: FeedStateIcon,
    val title: String,
    val message: String,
)

private enum class FeedStateIcon(val description: String) {
    EMPTY("暂无视频"),
    NETWORK("网络错误"),
    UNSUPPORTED("不支持播放"),
    ERROR("播放错误"),
}

private enum class FeedIconType {
    BACK,
    PLAY,
    PAUSE,
    VOLUME,
    MUTED,
    EXTERNAL_LINK,
    FULLSCREEN,
    EXIT_FULLSCREEN,
    LOGOUT,
    CLOSE,
}

private fun formatPublishTime(epochSeconds: Long): String = PUBLISH_TIME_FORMATTER.format(
    Instant.ofEpochSecond(epochSeconds),
)

internal fun videoPosterPaletteIndex(videoKey: VideoKey): Int {
    val foldedChatId = videoKey.chatId xor (videoKey.chatId ushr Int.SIZE_BITS)
    val foldedMessageId = videoKey.messageId xor (videoKey.messageId ushr Int.SIZE_BITS)
    val combined = foldedChatId * 31L + foldedMessageId * 17L
    return Math.floorMod(combined, VIDEO_POSTER_PALETTES.size.toLong()).toInt()
}

private fun VideoKey.posterIdentity(): String = "$chatId:$messageId"

private fun IndexedVideo.posterAspectRatio(): Float = when {
    width <= 0 || height <= 0 -> 1f
    else -> (width.toFloat() / height.toFloat()).coerceIn(0.52f, 1.85f)
}

private data class VideoPosterPalette(
    val base: Color,
    val upper: Color,
    val lower: Color,
    val accent: Color,
)

private val VIDEO_POSTER_PALETTES = listOf(
    VideoPosterPalette(
        base = Color(0xFF121B20),
        upper = Color(0xFF1C2930),
        lower = Color(0xFF0C1114),
        accent = Color(0xFF78919A),
    ),
    VideoPosterPalette(
        base = Color(0xFF191920),
        upper = Color(0xFF292834),
        lower = Color(0xFF101015),
        accent = Color(0xFF8B8396),
    ),
    VideoPosterPalette(
        base = Color(0xFF151C19),
        upper = Color(0xFF23302A),
        lower = Color(0xFF0D120F),
        accent = Color(0xFF7F9588),
    ),
    VideoPosterPalette(
        base = Color(0xFF1E1917),
        upper = Color(0xFF302722),
        lower = Color(0xFF120F0D),
        accent = Color(0xFF99877C),
    ),
    VideoPosterPalette(
        base = Color(0xFF151A22),
        upper = Color(0xFF222C3B),
        lower = Color(0xFF0D1015),
        accent = Color(0xFF7F8EAA),
    ),
    VideoPosterPalette(
        base = Color(0xFF1B171A),
        upper = Color(0xFF2E252A),
        lower = Color(0xFF110E10),
        accent = Color(0xFF987F8B),
    ),
)

private val PUBLISH_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter
    .ofPattern("yyyy-MM-dd HH:mm")
    .withZone(ZoneId.systemDefault())

private val FEED_ACCENT = Color(0xFF68E3E0)
private val FEED_BUTTON = Color(0xFF292929)
private val PROGRESS_BOTTOM_OFFSET = 16.dp

private const val METADATA_INTERACTION_ALPHA = 0.30f
private const val METADATA_FADE_OUT_MILLIS = 90
private const val METADATA_FADE_IN_MILLIS = 220
private const val METADATA_RESTORE_DELAY_MILLIS = 320L

private const val CONTROL_PRESSED_SCALE = 0.985f
private const val CONTROL_PRESS_IN_MILLIS = 90
private const val CONTROL_PRESS_OUT_MILLIS = 120
private const val PAUSED_OVERLAY_INITIAL_SCALE = 0.92f
private const val PAUSED_OVERLAY_ANIMATION_MILLIS = 200
private const val SWIPE_HINT_ENTRANCE_MILLIS = 360
private val TEMPORARY_SPEED_TOP_PADDING = 68.dp
private const val TEMPORARY_SPEED_ANIMATION_MILLIS = 180
private const val TEMPORARY_SPEED_INITIAL_SCALE = 0.94f
private const val TEMPORARY_SPEED_LABEL = "2× 快进中"

internal const val VIDEO_POSTER_FADE_OUT_MILLIS = 190

private const val RANDOM_PAGER_PAGE_COUNT = 2_000_000
private const val RANDOM_PAGER_CENTER = RANDOM_PAGER_PAGE_COUNT / 2

private fun randomPagerStart(itemCount: Int): Int =
    RANDOM_PAGER_CENTER - (RANDOM_PAGER_CENTER % itemCount.coerceAtLeast(1))

private fun logicalPage(page: Int, itemCount: Int): Int = page % itemCount.coerceAtLeast(1)

private fun monotonicTimeMillis(): Long = System.nanoTime() / 1_000_000L
