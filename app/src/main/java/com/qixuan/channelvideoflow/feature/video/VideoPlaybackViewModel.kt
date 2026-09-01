package com.qixuan.channelvideoflow.feature.video

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.ui.PlayerView
import androidx.media3.common.util.UnstableApi
import com.qixuan.channelvideoflow.domain.channel.TelegramChatRepository
import com.qixuan.channelvideoflow.domain.cache.MediaCacheController
import com.qixuan.channelvideoflow.domain.message.TelegramMessageRepository
import com.qixuan.channelvideoflow.domain.message.VideoReferenceFailure
import com.qixuan.channelvideoflow.domain.message.VideoReferenceResolution
import com.qixuan.channelvideoflow.domain.media.DevicePreloadPolicySource
import com.qixuan.channelvideoflow.domain.media.NetworkTransport
import com.qixuan.channelvideoflow.domain.media.NoOpStreamingNetworkMetricsRepository
import com.qixuan.channelvideoflow.domain.media.PreloadOwnerHandoffPhase
import com.qixuan.channelvideoflow.domain.media.StreamingNetworkMetricsRepository
import com.qixuan.channelvideoflow.domain.media.VideoQualitySelector
import com.qixuan.channelvideoflow.domain.media.VideoPreloadController
import com.qixuan.channelvideoflow.domain.video.VideoPlaybackQueue
import com.qixuan.channelvideoflow.domain.video.VideoFeedOnboardingPreferences
import com.qixuan.channelvideoflow.domain.video.RandomRoundEntry
import com.qixuan.channelvideoflow.model.video.DEFAULT_VIDEO_FEED_ORDER
import com.qixuan.channelvideoflow.model.video.OriginalMessageLinkResult
import com.qixuan.channelvideoflow.model.video.TagFilterMode
import com.qixuan.channelvideoflow.model.video.VideoFeedOrder
import com.qixuan.channelvideoflow.model.video.VideoFilter
import com.qixuan.channelvideoflow.model.video.VideoKey
import com.qixuan.channelvideoflow.model.video.VideoPlaybackVariant
import com.qixuan.channelvideoflow.model.video.VideoQualityPreference
import com.qixuan.channelvideoflow.player.PlaybackPlanRefreshOutcome
import com.qixuan.channelvideoflow.player.PlaybackTransitionDirection
import com.qixuan.channelvideoflow.player.PlaybackTransitionEvent
import com.qixuan.channelvideoflow.player.TransparentRecoveryOutcome
import com.qixuan.channelvideoflow.player.VideoPlaybackController
import com.qixuan.channelvideoflow.player.VideoPlaybackFailure
import com.qixuan.channelvideoflow.player.VideoPlaybackState
import com.qixuan.channelvideoflow.player.VideoPlayerSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext

@HiltViewModel
@UnstableApi
@OptIn(ExperimentalCoroutinesApi::class)
class VideoPlaybackViewModel private constructor(
    private val chatRepository: TelegramChatRepository,
    private val messageRepository: TelegramMessageRepository,
    private val playerController: VideoPlaybackController,
    private val preloadController: VideoPreloadController,
    private val cacheController: MediaCacheController,
    private val devicePolicySource: DevicePreloadPolicySource,
    private val onboardingPreferences: VideoFeedOnboardingPreferences,
    private val playbackQueue: VideoPlaybackQueue,
    private val networkMetrics: StreamingNetworkMetricsRepository,
) : ViewModel() {
    @Inject
    constructor(
        chatRepository: TelegramChatRepository,
        messageRepository: TelegramMessageRepository,
        playerController: VideoPlaybackController,
        preloadController: VideoPreloadController,
        cacheController: MediaCacheController,
        devicePolicySource: DevicePreloadPolicySource,
        onboardingPreferences: VideoFeedOnboardingPreferences,
        networkMetrics: StreamingNetworkMetricsRepository,
    ) : this(
        chatRepository = chatRepository,
        messageRepository = messageRepository,
        playerController = playerController,
        preloadController = preloadController,
        cacheController = cacheController,
        devicePolicySource = devicePolicySource,
        onboardingPreferences = onboardingPreferences,
        playbackQueue = VideoPlaybackQueue(),
        networkMetrics = networkMetrics,
    )

    internal constructor(
        chatRepository: TelegramChatRepository,
        messageRepository: TelegramMessageRepository,
        playerController: VideoPlaybackController,
        preloadController: VideoPreloadController,
        cacheController: MediaCacheController,
        devicePolicySource: DevicePreloadPolicySource,
        playbackQueue: VideoPlaybackQueue,
        onboardingPreferences: VideoFeedOnboardingPreferences,
        networkMetrics: StreamingNetworkMetricsRepository =
            NoOpStreamingNetworkMetricsRepository,
        @Suppress("UNUSED_PARAMETER") testMarker: Unit,
    ) : this(
        chatRepository = chatRepository,
        messageRepository = messageRepository,
        playerController = playerController,
        preloadController = preloadController,
        cacheController = cacheController,
        devicePolicySource = devicePolicySource,
        onboardingPreferences = onboardingPreferences,
        playbackQueue = playbackQueue,
        networkMetrics = networkMetrics,
    )

    private val mutableUiState = MutableStateFlow(VideoPlaybackUiState())
    val uiState: StateFlow<VideoPlaybackUiState> = mutableUiState.asStateFlow()
    private val mutablePlaybackProgress = MutableStateFlow(VideoPlaybackProgressUiState())
    val playbackProgress: StateFlow<VideoPlaybackProgressUiState> =
        mutablePlaybackProgress.asStateFlow()

    private val criteria = MutableStateFlow(FeedCriteria())
    private val mutableOpenOriginalMessageLinks = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val openOriginalMessageLinks: SharedFlow<String> = mutableOpenOriginalMessageLinks.asSharedFlow()

    private var items = emptyList<FeedVideoItem>()
    private var playerSnapshot = VideoPlayerSnapshot()
    private var currentPage = 0
    private var sourceLoaded = false
    private var lastAppliedSource: FeedSource? = null
    private var latestSourceVideos = emptyList<com.qixuan.channelvideoflow.model.video.IndexedVideo>()
    private var latestChannelTitles = emptyMap<Long, String>()
    private var randomRoundStartPagerPage: Int? = null
    private var queueGeneration = 0L
    private var playbackQueueGeneration = 0L
    private var accountGeneration = 0L
    private var qualitySelectionGeneration = 0L
    private var qualitySelection = devicePolicySource.signals.value.let { signals ->
        networkMetrics.resetNetworkContext(signals.network, signals.networkGeneration)
        val preference = cacheController.state.value.videoQualityPreference
        QualitySelection(
            preference = preference,
            network = signals.network,
            availableBandwidthBitsPerSecond = networkMetrics.estimate.value
                ?.availableBitsPerSecond
                ?.takeIf { preference == VideoQualityPreference.AUTO },
        )
    }
    private val playbackPlans = AtomicReference(PlaybackPlanSlots())
    private var pagerIsUnstable = false
    private var pendingPointerDownAtMillis: Long? = null
    private var unstableTarget: QueueTarget? = null
    private var lastSettledPage: SettledPage? = null
    private var stablePageGeneration = 0L
    private var planPreparationGeneration = 0L
    private var stablePageJob: Job? = null
    private var planPreparation: PlanPreparation? = null
    private var linkJob: Job? = null
    private var retryJob: Job? = null
    private var retryGeneration = 0L
    private var transparentRecoveryJob: Job? = null
    private var transparentRecoveryAttempt: TransparentRecoveryAttempt? = null
    private val activeReferenceResolutionCounts = mutableMapOf<VideoKey, Int>()
    private var swipeHintPreferenceLoaded = false
    private var swipeHintSeen = false
    private var swipeHintVisible = false
    private var swipeHintUserInteracted = false
    private var swipeHintHandledThisSession = false
    private var swipeHintMarkStarted = false
    private var swipeHintTimeoutJob: Job? = null
    private var isForeground = true

    init {
        viewModelScope.launch {
            onboardingPreferences.hasSeenSwipeHint.collect { seen ->
                swipeHintPreferenceLoaded = true
                swipeHintSeen = seen
                if (seen) swipeHintHandledThisSession = true
                if (seen && swipeHintVisible) {
                    swipeHintTimeoutJob?.cancel()
                    swipeHintTimeoutJob = null
                    swipeHintVisible = false
                    rebuildUiState()
                } else if (!seen) {
                    maybeShowSwipeHint()
                }
            }
        }
        viewModelScope.launch {
            combine(chatRepository.channels, criteria) { channels, selection ->
                val channelIds = selection.channelIds ?: channels
                    .asSequence()
                    .filter { channel -> channel.isSelected }
                    .map { channel -> channel.chatId }
                    .toSet()
                FeedSource(
                    filter = VideoFilter(
                        channelIds = channelIds,
                        normalizedTags = selection.normalizedTags,
                        tagMode = selection.tagMode,
                    ),
                    order = selection.order,
                    channelTitles = channels.associate { channel -> channel.chatId to channel.title },
                )
            }
                .distinctUntilChanged()
                .flatMapLatest { source ->
                    messageRepository.observeVideos(source.filter).map { videos ->
                        FeedSourceResult(source, videos)
                    }
                }
                .collect { result -> reconcileFeed(result) }
        }
        viewModelScope.launch {
            playerController.snapshot.collect { snapshot ->
                if (interceptFileUnavailableForRecovery(snapshot)) return@collect
                val previous = playerSnapshot
                playerSnapshot = snapshot
                mutablePlaybackProgress.value = snapshot.toProgressUiState()
                maybeShowSwipeHint()
                if (!snapshot.hasRenderedFirstFrame) {
                    val loading = snapshot.playbackState as? VideoPlaybackState.Loading
                    if (loading != null) {
                        preloadController.onCurrentPlaybackStarting(loading.video)
                    } else {
                        preloadController.stop()
                    }
                } else if (
                    !previous.hasRenderedFirstFrame ||
                    previous.playbackState.videoKeyOrNull() !=
                    snapshot.playbackState.videoKeyOrNull()
                ) {
                    transparentRecoveryAttempt = null
                    transparentRecoveryJob = null
                    prepareNextVideoPlan()
                }
                if (previous.toPresentationSnapshot() != snapshot.toPresentationSnapshot()) {
                    rebuildUiState()
                }
            }
        }
        viewModelScope.launch {
            combine(
                cacheController.state.map { state -> state.videoQualityPreference },
                devicePolicySource.signals,
                networkMetrics.estimate,
            ) { preference, signals, _ ->
                networkMetrics.resetNetworkContext(signals.network, signals.networkGeneration)
                QualitySelection(
                    preference = preference,
                    network = signals.network,
                    availableBandwidthBitsPerSecond = networkMetrics.estimate.value
                        ?.availableBitsPerSecond
                        ?.takeIf { preference == VideoQualityPreference.AUTO },
                )
            }
                .distinctUntilChanged()
                .collect { selection ->
                    if (selection == qualitySelection) return@collect
                    qualitySelection = selection
                    qualitySelectionGeneration += 1L
                    cancelTransparentRecovery(clearAttempt = true)
                    invalidatePlaybackPlans()
                    if (isForeground && playerSnapshot.hasRenderedFirstFrame && !pagerIsUnstable) {
                        prepareNextVideoPlan()
                    }
                }
        }
    }

    /** Called by the pager as soon as it starts moving, before the target is stable. */
    fun onPageUnstable() {
        playerController.setTemporaryPlaybackSpeed(active = false)
        if (pagerIsUnstable) return
        pagerIsUnstable = true
        unstableTarget = null
        val pointerDownAtMillis = pendingPointerDownAtMillis
        playerController.recordTransition(
            if (pointerDownAtMillis == null) {
                PlaybackTransitionEvent.PageUnstable
            } else {
                PlaybackTransitionEvent.GestureStarted(pointerDownAtMillis)
            },
        )
        stablePageGeneration += 1
        stablePageJob?.cancel()
        stablePageJob = null
        retryJob?.cancel()
        retryJob = null
        cancelTransparentRecovery(clearAttempt = true)
        playerController.pauseForPageTransition()
    }

    fun onPagerPointerDown(observedAtMillis: Long) {
        recordSwipeHintInteraction()
        if (pendingPointerDownAtMillis != null) return
        pendingPointerDownAtMillis = observedAtMillis
        if (pagerIsUnstable) {
            playerController.recordTransition(
                PlaybackTransitionEvent.GestureStarted(observedAtMillis),
            )
            unstableTarget = null
            cancelPlanPreparation(clearNextPlan = false)
        }
    }

    fun onPagerPointerReleased(observedAtMillis: Long) {
        if (pagerIsUnstable && pendingPointerDownAtMillis != null) {
            playerController.recordTransition(
                PlaybackTransitionEvent.GestureReleased(observedAtMillis),
            )
        }
        pendingPointerDownAtMillis = null
    }

    /**
     * Starts cancelable metadata/quality preparation for Compose Pager's current target.
     * It never binds the shared player; binding remains gated by [onPageSettled].
     */
    fun onPageTargeted(pagerPage: Int, logicalPage: Int) {
        if (!pagerIsUnstable) return
        val target = resolveQueueTarget(pagerPage, logicalPage) ?: return
        val item = buildItem(target.video)
        val previousTarget = unstableTarget
        unstableTarget = target
        val currentKey = items.getOrNull(currentPage)?.video?.key
        if (item.video.key == currentKey) {
            if (previousTarget != null && !previousTarget.samePosition(target)) {
                if (pendingPointerDownAtMillis == null) {
                    // targetPage may bounce to the old page during release/fling hand-off.
                    // settledPage is authoritative once direct manipulation has ended.
                    unstableTarget = previousTarget
                    return
                }
                playerController.recordTransition(PlaybackTransitionEvent.TargetAbandoned)
                preloadController.abandonTargetPromotion()
                cancelPlanPreparation(clearNextPlan = true)
            }
            return
        }
        if (previousTarget?.samePosition(target) != true) {
            val replacesExistingCandidate =
                previousTarget?.video?.key != null && previousTarget.video.key != currentKey
            val replacesForwardCandidate = nextTarget()?.samePosition(target) != true
            if (replacesExistingCandidate || replacesForwardCandidate) {
                preloadController.setNextVideo(null)
            }
            playerController.recordTransition(targetKnownEvent(target, pagerPage))
            playerController.recordTransition(
                PlaybackTransitionEvent.PlanPreparationStarted(item.video.key),
            )
        }
        preloadController.commitTargetPromotion(promotionVideo(target))
        ensurePlanPreparation(target)
    }

    /** Only the latest settled page is allowed to bind the shared player. */
    fun onPageSettled(pagerPage: Int, logicalPage: Int) {
        val reportedTarget = resolveQueueTarget(pagerPage, logicalPage) ?: return
        val reportedItem = buildItem(reportedTarget.video)
        val reportedSettledEvent = pageSettledEvent(reportedTarget, pagerPage)
        val expectedTarget = unstableTarget
        if (
            pagerIsUnstable &&
            expectedTarget != null &&
            !reportedTarget.samePosition(expectedTarget)
        ) {
            if (reportedItem.video.key == items.getOrNull(currentPage)?.video?.key) {
                playerController.recordTransition(PlaybackTransitionEvent.TargetAbandoned)
            } else {
                playerController.recordTransition(
                    targetKnownEvent(reportedTarget, pagerPage),
                )
            }
            preloadController.abandonTargetPromotion()
            cancelPlanPreparation(clearNextPlan = true)
            unstableTarget = reportedTarget
        }
        val wasUnstable = pagerIsUnstable
        pagerIsUnstable = false
        pendingPointerDownAtMillis = null
        unstableTarget = null
        val settledTarget = if (reportedTarget.randomEntry == null) {
            reportedTarget
        } else {
            val previousGeneration = playbackQueue.randomRoundState?.current?.generation
            val settledState = playbackQueue.settleRandom(reportedTarget.randomEntry)
            if (settledState.current.generation != previousGeneration) {
                items = buildItems(settledState.current.items, latestChannelTitles)
                randomRoundStartPagerPage = pagerPage - reportedTarget.randomEntry.index
            } else if (randomRoundStartPagerPage == null) {
                randomRoundStartPagerPage = pagerPage - reportedTarget.randomEntry.index
            }
            val currentEntry = settledState.current.entry(settledState.currentIndex)
            QueueTarget(currentEntry.video, currentEntry)
        }
        val item = buildItem(settledTarget.video)
        val settledPage = SettledPage(
            pagerPage = pagerPage,
            key = item.video.key,
            queueGeneration = playbackQueueGeneration,
        )
        if (!wasUnstable && lastSettledPage == settledPage) return
        lastSettledPage = settledPage
        playerController.recordTransition(reportedSettledEvent)
        currentPage = reportedTarget.randomEntry?.index ?: logicalPage
        if (
            wasUnstable &&
            playerSnapshot.playbackState.videoKeyOrNull() == item.video.key
        ) {
            preloadController.abandonTargetPromotion()
            playerController.resume()
            prepareNextVideoPlan()
            rebuildUiState(originalMessageLink = OriginalMessageLinkUiState.Idle)
            return
        }
        if (wasUnstable) {
            preloadController.commitTargetPromotion(promotionVideo(settledTarget))
        }
        if (planPreparation != null && planPreparation?.key != item.video.key) {
            cancelPlanPreparation(clearNextPlan = true)
        }
        val requestGeneration = stablePageGeneration + 1
        stablePageGeneration = requestGeneration
        stablePageJob?.cancel()
        stablePageJob = viewModelScope.launch {
            if (
                requestGeneration != stablePageGeneration ||
                items.getOrNull(currentPage)?.video?.key != item.video.key
            ) {
                return@launch
            }
            val token = currentPlanToken(settledTarget.randomEntry?.roundGeneration)
            var plan = promoteNextPlan(item.video.key, token)
            if (plan == null) {
                val preparation = planPreparation?.takeIf { pending ->
                    pending.key == item.video.key && pending.token == token
                }
                if (preparation != null) {
                    preparation.deferred.await()
                    plan = promoteNextPlan(item.video.key, token)
                }
            }
            if (plan == null) {
                playerController.recordTransition(
                    PlaybackTransitionEvent.PlanStarted(
                        key = item.video.key,
                        promoted = false,
                    ),
                )
                plan = preparePlaybackPlan(
                    video = item.video,
                    token = token,
                    recordRefresh = true,
                )
                if (plan != null && isPlanRequestCurrent(token, item.video.key)) {
                    installCurrentPlan(plan)
                }
            } else {
                playerController.recordTransition(
                    PlaybackTransitionEvent.PlanStarted(
                        key = item.video.key,
                        promoted = true,
                        planAgeMillis =
                            (monotonicTimeMillis() - plan.preparedAtMillis).coerceAtLeast(0L),
                        preparedRefreshOutcome = plan.refreshOutcome,
                        preparedRefreshMillis = plan.refreshMillis,
                    ),
                )
            }
            val currentItem = items.getOrNull(currentPage)
            val terminalFailure = plan?.terminalFailure
            if (
                terminalFailure != null &&
                lastSettledPage?.key == item.video.key &&
                !pagerIsUnstable
            ) {
                planPreparation = null
                preloadController.stop()
                playerController.showFailure(
                    requireNotNull(plan).toVideo(item.video),
                    terminalFailure,
                )
                return@launch
            }
            if (
                requestGeneration != stablePageGeneration ||
                currentItem?.video?.key != item.video.key ||
                plan == null ||
                !plan.matches(
                    item.video.key,
                    currentPlanToken(settledTarget.randomEntry?.roundGeneration),
                )
            ) {
                return@launch
            }
            planPreparation = null
            val plannedVideo = plan.toVideo(currentItem.video)
            if (plan.terminalFailure == null) {
                bindStableItem(plannedVideo)
            } else {
                preloadController.stop()
                playerController.showFailure(plannedVideo, plan.terminalFailure)
            }
        }
        rebuildUiState()
    }

    fun togglePause() {
        if (playerSnapshot.isPaused) {
            playerController.resume()
        } else {
            playerController.setTemporaryPlaybackSpeed(active = false)
            playerController.pause()
        }
    }

    fun setTemporaryPlaybackSpeed(active: Boolean) {
        if (!active) {
            playerController.setTemporaryPlaybackSpeed(active = false)
            return
        }
        val ready = playerSnapshot.playbackState as? VideoPlaybackState.Ready
        val currentKey = items.getOrNull(currentPage)?.video?.key
        val canActivate = ready != null &&
            ready.video.key == currentKey &&
            lastSettledPage?.key == currentKey &&
            !pagerIsUnstable &&
            playerSnapshot.hasRenderedFirstFrame &&
            playerSnapshot.isPlaying &&
            !playerSnapshot.isPaused
        playerController.setTemporaryPlaybackSpeed(active = canActivate)
    }

    fun attachPlayer(playerView: PlayerView) {
        playerController.attach(playerView)
    }

    fun detachPlayer(playerView: PlayerView) {
        playerController.detach(playerView)
    }

    fun seekTo(positionMillis: Long) {
        playerController.setTemporaryPlaybackSpeed(active = false)
        preloadController.stop()
        playerController.seekTo(positionMillis)
    }

    fun toggleMute() {
        playerController.setMuted(!playerSnapshot.isMuted)
    }

    fun retry() {
        val failed = playerSnapshot.playbackState as? VideoPlaybackState.Failed
        if (failed?.reason == VideoPlaybackFailure.MESSAGE_UNAVAILABLE) return
        if (failed?.reason != VideoPlaybackFailure.FILE_UNAVAILABLE) {
            playerController.retry()
            return
        }
        val failedKey = failed.video.key
        cancelTransparentRecovery(clearAttempt = true)
        val requestGeneration = stablePageGeneration
        val token = currentPlanToken()
        val retryRequestGeneration = retryGeneration + 1L
        retryGeneration = retryRequestGeneration
        retryJob?.cancel()
        retryJob = viewModelScope.launch {
            val resolution = resolveVideoReference(failedKey)
            if (!isManualRetryPresentationCurrent(failedKey, retryRequestGeneration, token)) {
                return@launch
            }
            when (resolution) {
                is VideoReferenceResolution.Resolved -> {
                    val currentItem = items.getOrNull(currentPage)
                    if (
                        requestGeneration != stablePageGeneration ||
                        currentItem?.video?.key != failedKey ||
                        token != currentPlanToken()
                    ) {
                        return@launch
                    }
                    playerController.bind(selectPlaybackVideo(resolution.video, token.selection))
                }
                VideoReferenceResolution.MessageMissing,
                VideoReferenceResolution.UnsupportedMessage,
                -> publishManualRetryMessageUnavailable(failed.video)
                is VideoReferenceResolution.Unavailable,
                null,
                -> Unit
            }
        }
    }

    private fun isManualRetryPresentationCurrent(
        failedKey: VideoKey,
        retryRequestGeneration: Long,
        token: PlaybackPlanToken,
    ): Boolean {
        val failed = playerSnapshot.playbackState as? VideoPlaybackState.Failed ?: return false
        return retryRequestGeneration == retryGeneration &&
            retryJob?.isActive == true &&
            !pagerIsUnstable &&
            failed.reason == VideoPlaybackFailure.FILE_UNAVAILABLE &&
            failed.video.key == failedKey &&
            token.qualitySelectionGeneration == qualitySelectionGeneration &&
            token.accountGeneration == accountGeneration &&
            token.selection == qualitySelection
    }

    private fun publishManualRetryMessageUnavailable(
        video: com.qixuan.channelvideoflow.model.video.IndexedVideo,
    ) {
        playerSnapshot = playerSnapshot.copy(
            playbackState = VideoPlaybackState.Failed(
                video,
                VideoPlaybackFailure.MESSAGE_UNAVAILABLE,
            ),
            isPaused = false,
            hasRenderedFirstFrame = false,
        )
        mutablePlaybackProgress.value = playerSnapshot.toProgressUiState()
        playerController.showFailure(video, VideoPlaybackFailure.MESSAGE_UNAVAILABLE)
        rebuildUiState()
    }

    fun setOrder(order: VideoFeedOrder) {
        if (criteria.value.order == order) return
        stopOldFeedRequests()
        criteria.value = criteria.value.copy(order = order)
    }

    /**
     * Allows a future channel/tag screen to atomically replace the feed source.
     * An empty channel set intentionally means an empty feed, never all channels.
     */
    fun setFilter(filter: VideoFilter) {
        if (
            criteria.value.channelIds == filter.channelIds &&
            criteria.value.normalizedTags == filter.normalizedTags &&
            criteria.value.tagMode == filter.tagMode
        ) return
        stopOldFeedRequests()
        criteria.value = criteria.value.copy(
            channelIds = filter.channelIds,
            normalizedTags = filter.normalizedTags,
            tagMode = filter.tagMode,
        )
    }

    fun requestOriginalMessageLink() {
        val item = items.getOrNull(currentPage) ?: return
        linkJob?.cancel()
        rebuildUiState(originalMessageLink = OriginalMessageLinkUiState.Loading)
        linkJob = viewModelScope.launch {
            when (val result = messageRepository.getOriginalMessageLink(item.video.key)) {
                is OriginalMessageLinkResult.Available -> {
                    rebuildUiState(originalMessageLink = OriginalMessageLinkUiState.Idle)
                    mutableOpenOriginalMessageLinks.emit(result.httpsUrl)
                }
                OriginalMessageLinkResult.Unavailable -> showUnavailableLink("无法打开 Telegram 原消息")
                OriginalMessageLinkResult.NetworkUnavailable -> showUnavailableLink("网络不可用，无法打开 Telegram 原消息")
                OriginalMessageLinkResult.Unknown -> showUnavailableLink("无法打开 Telegram 原消息")
            }
        }
    }

    fun onOriginalMessageLinkOpenFailed() {
        showUnavailableLink("无法打开 Telegram 原消息")
    }

    fun onForegroundChanged(isForeground: Boolean) {
        this.isForeground = isForeground
        if (!isForeground) {
            playerController.setTemporaryPlaybackSpeed(active = false)
            cancelPlanPreparation(clearNextPlan = false)
            preloadController.stop()
            playerController.onAppBackgrounded()
        } else if (playerSnapshot.playbackState.videoKeyOrNull() != null) {
            prepareNextVideoPlan()
        }
    }

    fun releasePage() {
        playerController.setTemporaryPlaybackSpeed(active = false)
        dismissSwipeHintIfVisible()
        accountGeneration += 1L
        stablePageJob?.cancel()
        stablePageJob = null
        cancelPlanPreparation(clearNextPlan = true)
        playbackPlans.set(PlaybackPlanSlots())
        linkJob?.cancel()
        retryJob?.cancel()
        retryJob = null
        cancelTransparentRecovery(clearAttempt = true)
        preloadController.stop()
        playerController.release()
        viewModelScope.launch { cacheController.trimToLimit() }
    }

    override fun onCleared() {
        releasePage()
        super.onCleared()
    }

    private fun reconcileFeed(result: FeedSourceResult) {
        val sourceChanged = lastAppliedSource?.let { previous ->
            previous.filter != result.source.filter || previous.order != result.source.order
        } ?: true
        if (sourceChanged) {
            stopOldFeedRequests()
            queueGeneration += 1
            currentPage = 0
            randomRoundStartPagerPage = null
        }
        lastAppliedSource = result.source
        sourceLoaded = true
        latestSourceVideos = result.videos
        latestChannelTitles = result.source.channelTitles

        val previousRandomState = playbackQueue.randomRoundState
        val randomState = if (result.source.order == VideoFeedOrder.RANDOM) {
            if (sourceChanged) {
                playbackQueue.startRandomSession(result.videos)
            } else {
                playbackQueue.reconcileRandomSession(result.videos)
            }
        } else {
            null
        }
        val orderedVideos = randomState?.current?.items
            ?: playbackQueue.rebuild(result.videos, result.source.order)
        val rebuiltItems = buildItems(orderedVideos, result.source.channelTitles)
        val randomSessionMetadataChanged = randomState != null && previousRandomState != null &&
            (
                previousRandomState.current.items != randomState.current.items ||
                    previousRandomState.upcoming?.items != randomState.upcoming?.items
                )
        val randomSessionStructureChanged = randomState != null && previousRandomState != null &&
            (
                previousRandomState.current.generation != randomState.current.generation ||
                    previousRandomState.upcoming?.generation != randomState.upcoming?.generation ||
                    previousRandomState.current.items.map { video -> video.key } !=
                    randomState.current.items.map { video -> video.key } ||
                    previousRandomState.upcoming?.items?.map { video -> video.key } !=
                    randomState.upcoming?.items?.map { video -> video.key }
                )
        val queueStructureChanged = !sourceChanged &&
            (
                rebuiltItems.map { item -> item.video.key } != items.map { item -> item.video.key } ||
                    randomSessionStructureChanged
                )
        val queueMetadataChanged = !sourceChanged && !queueStructureChanged &&
            (
                rebuiltItems.map(FeedVideoItem::video) != items.map(FeedVideoItem::video) ||
                    randomSessionMetadataChanged
                )
        val recoveryKey = transparentRecoveryAttempt?.key?.videoKey
        val preserveRemovedCurrentRecovery = queueStructureChanged &&
            recoveryKey != null &&
            rebuiltItems.none { item -> item.video.key == recoveryKey } &&
            playerSnapshot.playbackState.videoKeyOrNull() == recoveryKey
        val resolvingSettledKey = lastSettledPage?.key?.takeIf { key ->
            activeReferenceResolutionCounts[key]?.let { count -> count > 0 } == true
        }
        val preserveRemovedCurrentPlan = queueStructureChanged &&
            resolvingSettledKey != null &&
            rebuiltItems.none { item -> item.video.key == resolvingSettledKey } &&
            stablePageJob?.isActive == true
        val retryingFailedKey = (playerSnapshot.playbackState as? VideoPlaybackState.Failed)
            ?.takeIf { failed -> failed.reason == VideoPlaybackFailure.FILE_UNAVAILABLE }
            ?.video
            ?.key
        val preserveRemovedCurrentRetry = queueStructureChanged &&
            retryingFailedKey != null &&
            rebuiltItems.none { item -> item.video.key == retryingFailedKey } &&
            retryJob?.isActive == true
        val messageUnavailableKey = (playerSnapshot.playbackState as? VideoPlaybackState.Failed)
            ?.takeIf { failed -> failed.reason == VideoPlaybackFailure.MESSAGE_UNAVAILABLE }
            ?.video
            ?.key
        val preserveRemovedMessageUnavailable = queueStructureChanged &&
            messageUnavailableKey != null &&
            rebuiltItems.none { item -> item.video.key == messageUnavailableKey }
        val preserveRemovedCurrentPresentation =
            preserveRemovedCurrentRecovery || preserveRemovedCurrentPlan ||
                preserveRemovedCurrentRetry || preserveRemovedMessageUnavailable
        if (queueStructureChanged) {
            preloadController.stop()
            invalidatePlaybackPlans(
                queueChanged = true,
                preserveTransparentRecovery = preserveRemovedCurrentRecovery,
                preserveStablePageJob = preserveRemovedCurrentPlan,
                preserveRetryJob = preserveRemovedCurrentRetry,
            )
        } else if (queueMetadataChanged) {
            // A refresh may publish its same-key Room write just after the prepared plan is
            // installed. Keep only plans whose source fields still describe that exact metadata;
            // this preserves single-flight target -> settle without accepting stale references.
            reconcilePlaybackPlansWith(rebuiltItems)
        }
        items = rebuiltItems
        if (randomState != null) {
            randomRoundStartPagerPage = if (randomState.current.items.isEmpty()) {
                null
            } else {
                lastSettledPage?.pagerPage?.minus(randomState.currentIndex)
                    ?: randomRoundStartPagerPage
            }
        }

        val currentVideoStillExists = items.any { item ->
            item.video.key == playerSnapshot.playbackState.videoKeyOrNull()
        }
        if (
            !currentVideoStillExists &&
            !preserveRemovedCurrentPresentation &&
            playerSnapshot.playbackState !is VideoPlaybackState.Idle
        ) {
            preloadController.stop()
            playerController.releaseBinding()
        }
        currentPage = randomState?.currentIndex
            ?: currentPage.coerceIn(0, (items.lastIndex).coerceAtLeast(0))
        if (
            queueStructureChanged &&
            currentVideoStillExists &&
            playerSnapshot.hasRenderedFirstFrame
        ) {
            prepareNextVideoPlan()
        }
        rebuildUiState()
    }

    private fun bindStableItem(
        video: com.qixuan.channelvideoflow.model.video.IndexedVideo,
    ) {
        playbackQueue.recordPlayed(video.key, criteria.value.order)
        val boundKey = playerSnapshot.playbackState.videoKeyOrNull()
        if (boundKey == video.key) {
            playerController.resume()
        } else {
            preloadController.onCurrentPlaybackStarting(video)
            playerController.bind(video)
        }
        if (
            preloadController.ownerHandoff.value.phase !=
            PreloadOwnerHandoffPhase.TARGET_COMMITTED
        ) {
            prepareNextVideoPlan()
        }
        viewModelScope.launch { cacheController.trimToLimit() }
        rebuildUiState(originalMessageLink = OriginalMessageLinkUiState.Idle)
    }

    private fun stopOldFeedRequests() {
        playerController.setTemporaryPlaybackSpeed(active = false)
        playbackQueueGeneration += 1L
        pagerIsUnstable = false
        pendingPointerDownAtMillis = null
        unstableTarget = null
        lastSettledPage = null
        stablePageGeneration += 1
        stablePageJob?.cancel()
        stablePageJob = null
        cancelPlanPreparation(clearNextPlan = true)
        playbackPlans.set(PlaybackPlanSlots())
        retryJob?.cancel()
        retryJob = null
        cancelTransparentRecovery(clearAttempt = true)
        preloadController.stop()
        playerController.releaseBinding()
    }

    private fun prepareNextVideoPlan() {
        val next = nextTarget()
        if (next == null) {
            cancelPlanPreparation(clearNextPlan = true)
            preloadController.stop()
            return
        }
        val token = currentPlanToken(next.randomEntry?.roundGeneration)
        val readyPlan = playbackPlans.get().next
        if (readyPlan?.matches(next.video.key, token) == true) {
            activateNextPreloadIfEligible(readyPlan, next)
            return
        }
        ensurePlanPreparation(next)
    }

    private fun promotionVideo(
        target: QueueTarget,
    ): com.qixuan.channelvideoflow.model.video.IndexedVideo {
        val video = target.video
        val token = currentPlanToken(target.randomEntry?.roundGeneration)
        val prepared = playbackPlans.get().next
            ?.takeIf { plan -> plan.matches(video.key, token) }
        return prepared?.toVideo(video) ?: video
    }

    private fun ensurePlanPreparation(
        target: QueueTarget,
    ) {
        val video = target.video
        val token = currentPlanToken(target.randomEntry?.roundGeneration)
        val readyPlan = playbackPlans.get().next
        if (readyPlan?.matches(video.key, token) == true) {
            recordTargetPlanPreparedIfApplicable(target)
            activateNextPreloadIfEligible(readyPlan, target)
            return
        }
        val existing = planPreparation
        if (
            existing?.key == video.key &&
            existing.token == token &&
            existing.deferred.isActive
        ) {
            return
        }

        cancelPlanPreparation(clearNextPlan = true)
        val generation = planPreparationGeneration + 1L
        planPreparationGeneration = generation
        val deferred = viewModelScope.async(start = CoroutineStart.LAZY) {
            val plan = preparePlaybackPlan(
                video = video,
                token = token,
                recordRefresh = false,
            )
            if (
                plan != null &&
                planPreparation?.generation == generation &&
                isPlanRequestCurrent(token, video.key)
            ) {
                installNextPlan(plan)
                recordTargetPlanPreparedIfApplicable(target)
                activateNextPreloadIfEligible(plan, target)
            }
            plan
        }
        planPreparation = PlanPreparation(
            generation = generation,
            key = video.key,
            token = token,
            deferred = deferred,
        )
        deferred.start()
    }

    private fun activateNextPreloadIfEligible(
        plan: PlaybackPlan,
        target: QueueTarget,
    ) {
        val video = target.video
        val currentKey = items.getOrNull(currentPage)?.video?.key
        val next = nextTarget()
        val committedTarget = unstableTarget
            ?.takeIf { pagerIsUnstable }
            ?.takeIf { it.samePosition(target) }
        if (
            plan.terminalFailure != null ||
            playerSnapshot.playbackState.videoKeyOrNull() != currentKey ||
            (
                committedTarget == null &&
                    (
                        next?.video?.key != plan.key ||
                            next.randomEntry?.roundGeneration != plan.token.randomRoundGeneration
                        )
                ) ||
            video.key != plan.key ||
            !plan.matches(
                plan.key,
                currentPlanToken(target.randomEntry?.roundGeneration),
            )
        ) {
            return
        }
        if (!playerSnapshot.hasRenderedFirstFrame) return
        val preparedVideo = plan.toVideo(video)
        preloadController.setNextVideo(preparedVideo)
    }

    private suspend fun preparePlaybackPlan(
        video: com.qixuan.channelvideoflow.model.video.IndexedVideo,
        token: PlaybackPlanToken,
        recordRefresh: Boolean,
    ): PlaybackPlan? {
        if (!isPlanRequestCurrent(token, video.key)) return null
        if (recordRefresh) {
            playerController.recordTransition(PlaybackTransitionEvent.RefreshStarted(video.key))
        }
        val refreshStartedAtMillis = monotonicTimeMillis()
        val refreshResult = refreshVideoForPlayback(video, token.selection)
        val refreshMillis =
            (monotonicTimeMillis() - refreshStartedAtMillis).coerceAtLeast(0L)
        if (recordRefresh) {
            playerController.recordTransition(
                PlaybackTransitionEvent.RefreshFinished(
                    key = video.key,
                    outcome = refreshResult.outcome,
                ),
            )
        }
        if (!isPlanRequestCurrent(token, video.key) && refreshResult.terminalFailure == null) {
            return null
        }
        val selected = selectPlaybackVideo(refreshResult.video ?: video, token.selection)
        return PlaybackPlan.from(
            video = selected,
            token = token,
            refreshOutcome = refreshResult.outcome,
            refreshMillis = refreshMillis,
            preparedAtMillis = monotonicTimeMillis(),
            terminalFailure = refreshResult.terminalFailure,
        )
    }

    private fun currentPlanToken(
        randomRoundGeneration: Long? = playbackQueue.randomRoundState
            ?.current
            ?.generation
            ?.takeIf { criteria.value.order == VideoFeedOrder.RANDOM },
    ): PlaybackPlanToken = PlaybackPlanToken(
        qualitySelectionGeneration = qualitySelectionGeneration,
        selection = qualitySelection,
        accountGeneration = accountGeneration,
        queueGeneration = playbackQueueGeneration,
        randomRoundGeneration = randomRoundGeneration,
    )

    private fun isPlanRequestCurrent(
        token: PlaybackPlanToken,
        key: VideoKey,
    ): Boolean {
        if (token != currentPlanToken(token.randomRoundGeneration)) return false
        if (criteria.value.order != VideoFeedOrder.RANDOM) {
            return items.any { item -> item.video.key == key }
        }
        val state = playbackQueue.randomRoundState ?: return false
        val round = when (token.randomRoundGeneration) {
            state.current.generation -> state.current
            state.upcoming?.generation -> state.upcoming
            else -> null
        }
        return round?.items?.any { video -> video.key == key } == true
    }

    private fun installCurrentPlan(plan: PlaybackPlan) {
        while (true) {
            val current = playbackPlans.get()
            val updated = current.copy(current = plan)
            if (playbackPlans.compareAndSet(current, updated)) return
        }
    }

    private fun installNextPlan(plan: PlaybackPlan) {
        while (true) {
            val current = playbackPlans.get()
            val updated = current.copy(next = plan)
            if (playbackPlans.compareAndSet(current, updated)) return
        }
    }

    private fun reconcilePlaybackPlansWith(rebuiltItems: List<FeedVideoItem>) {
        val metadataByKey = rebuiltItems.associate { item -> item.video.key to item.video }
        var removedNextPlan = false
        while (true) {
            val current = playbackPlans.get()
            val updated = PlaybackPlanSlots(
                current = current.current?.takeIf { plan ->
                    metadataByKey[plan.key]?.let(plan::isCompatibleWith) == true
                },
                next = current.next?.takeIf { plan ->
                    metadataByKey[plan.key]?.let(plan::isCompatibleWith) == true
                },
            )
            if (updated == current) return
            if (playbackPlans.compareAndSet(current, updated)) {
                removedNextPlan = current.next != null && updated.next == null
                break
            }
        }
        if (removedNextPlan) preloadController.stop()
    }

    private fun promoteNextPlan(
        key: VideoKey,
        token: PlaybackPlanToken,
    ): PlaybackPlan? {
        while (true) {
            val current = playbackPlans.get()
            val candidate = current.next ?: return null
            if (!candidate.matches(key, token)) return null
            val promoted = PlaybackPlanSlots(current = candidate, next = null)
            if (playbackPlans.compareAndSet(current, promoted)) return candidate
        }
    }

    private fun cancelPlanPreparation(clearNextPlan: Boolean) {
        planPreparationGeneration += 1L
        planPreparation?.deferred?.cancel()
        planPreparation = null
        if (clearNextPlan) {
            while (true) {
                val current = playbackPlans.get()
                if (current.next == null) break
                if (playbackPlans.compareAndSet(current, current.copy(next = null))) break
            }
            preloadController.stop()
        }
    }

    private fun invalidatePlaybackPlans(
        queueChanged: Boolean = false,
        preserveTransparentRecovery: Boolean = false,
        preserveStablePageJob: Boolean = false,
        preserveRetryJob: Boolean = false,
    ) {
        if (queueChanged) playbackQueueGeneration += 1L
        stablePageGeneration += 1L
        if (!preserveStablePageJob) {
            stablePageJob?.cancel()
            stablePageJob = null
        }
        if (!preserveRetryJob) {
            retryJob?.cancel()
            retryJob = null
        }
        if (!preserveTransparentRecovery) cancelTransparentRecovery(clearAttempt = true)
        cancelPlanPreparation(clearNextPlan = true)
        playbackPlans.set(PlaybackPlanSlots())
    }

    private fun recordTargetPlanPreparedIfApplicable(target: QueueTarget) {
        if (pagerIsUnstable && unstableTarget?.samePosition(target) == true) {
            playerController.recordTransition(PlaybackTransitionEvent.PlanPrepared(target.video.key))
        }
    }

    private fun selectPlaybackVideo(
        video: com.qixuan.channelvideoflow.model.video.IndexedVideo,
        selection: QualitySelection,
    ): com.qixuan.channelvideoflow.model.video.IndexedVideo =
        VideoQualitySelector.select(
            video = video,
            preference = selection.preference,
            network = selection.network,
            availableBandwidthBitsPerSecond = selection.availableBandwidthBitsPerSecond,
        )

    private suspend fun refreshVideoForPlayback(
        video: com.qixuan.channelvideoflow.model.video.IndexedVideo,
        selection: QualitySelection,
    ): PlaybackRefreshResult {
        val preference = selection.preference
        val requiresRandomReferenceRefresh = criteria.value.order == VideoFeedOrder.RANDOM
        if (
            preference == VideoQualityPreference.ORIGINAL &&
            !requiresRandomReferenceRefresh
        ) {
            return PlaybackRefreshResult(
                video = null,
                outcome = PlaybackPlanRefreshOutcome.SKIPPED,
            )
        }
        val resolution = resolveVideoReference(video.key)
        return when (resolution) {
            is VideoReferenceResolution.Resolved -> PlaybackRefreshResult(
                video = resolution.video,
                outcome = PlaybackPlanRefreshOutcome.SUCCESS,
            )
            VideoReferenceResolution.MessageMissing,
            VideoReferenceResolution.UnsupportedMessage,
            -> PlaybackRefreshResult(
                video = null,
                outcome = PlaybackPlanRefreshOutcome.FALLBACK,
                terminalFailure = VideoPlaybackFailure.MESSAGE_UNAVAILABLE,
            )
            is VideoReferenceResolution.Unavailable,
            null,
            -> PlaybackRefreshResult(
                video = null,
                outcome = PlaybackPlanRefreshOutcome.FALLBACK,
            )
        }
    }

    private suspend fun resolveVideoReference(videoKey: VideoKey): VideoReferenceResolution? {
        activeReferenceResolutionCounts[videoKey] =
            activeReferenceResolutionCounts.getOrDefault(videoKey, 0) + 1
        return try {
            withTimeoutOrNull(QUALITY_REFRESH_TIMEOUT_MILLIS) {
                messageRepository.refreshVideo(videoKey)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            VideoReferenceResolution.Unavailable(VideoReferenceFailure.Unknown)
        } finally {
            val remaining = activeReferenceResolutionCounts.getOrDefault(videoKey, 1) - 1
            if (remaining <= 0) {
                activeReferenceResolutionCounts.remove(videoKey)
            } else {
                activeReferenceResolutionCounts[videoKey] = remaining
            }
        }
    }

    private fun interceptFileUnavailableForRecovery(snapshot: VideoPlayerSnapshot): Boolean {
        val failed = snapshot.playbackState as? VideoPlaybackState.Failed ?: return false
        if (failed.reason != VideoPlaybackFailure.FILE_UNAVAILABLE) return false
        val currentItem = items.getOrNull(currentPage) ?: return false
        if (currentItem.video.key != failed.video.key) return false
        val token = currentPlanToken()
        val recoveryKey = TransparentRecoveryKey(
            videoKey = failed.video.key,
            stablePageGeneration = stablePageGeneration,
            qualitySelectionGeneration = token.qualitySelectionGeneration,
            accountGeneration = token.accountGeneration,
            queueGeneration = token.queueGeneration,
        )
        val prior = transparentRecoveryAttempt
        if (prior?.key == recoveryKey && prior.attempted) {
            playerController.recordTransition(
                PlaybackTransitionEvent.TransparentRecoveryFinished(
                    failed.video.key,
                    TransparentRecoveryOutcome.REFRESHED_FILE_UNAVAILABLE,
                ),
            )
            playerController.finishFileRecoveryFailure(failed.video.key)
            return false
        }

        cancelTransparentRecovery(clearAttempt = true)
        val attempt = TransparentRecoveryAttempt(
            key = recoveryKey,
            token = token,
            failedSnapshot = snapshot,
            failedPlaybackFileId = failed.video.playbackFileId,
        )
        transparentRecoveryAttempt = attempt
        preloadController.stop()
        playerSnapshot = snapshot.copy(
            playbackState = VideoPlaybackState.Loading(failed.video),
            hasRenderedFirstFrame = false,
        )
        mutablePlaybackProgress.value = playerSnapshot.toProgressUiState()
        rebuildUiState()
        playerController.recordTransition(
            PlaybackTransitionEvent.TransparentRecoveryStarted(failed.video.key),
        )
        transparentRecoveryJob = viewModelScope.launch {
            val resolution = resolveVideoReference(failed.video.key)
            if (!isTransparentRecoveryPresentationCurrent(attempt)) return@launch
            when (resolution) {
                is VideoReferenceResolution.Resolved -> {
                    if (!isTransparentRecoveryCurrent(attempt)) {
                        cancelTransparentRecovery(clearAttempt = true)
                        playerController.releaseBinding()
                        return@launch
                    }
                    val selected = selectPlaybackVideo(resolution.video, attempt.token.selection)
                    if (selected.playbackFileId == attempt.failedPlaybackFileId) {
                        recordTransparentRecoveryFinished(
                            attempt,
                            TransparentRecoveryOutcome.STALE_REFERENCE,
                        )
                        publishDeferredFileFailure(attempt)
                    } else {
                        recordTransparentRecoveryFinished(
                            attempt,
                            TransparentRecoveryOutcome.REBOUND,
                        )
                        playerController.bind(selected)
                    }
                }
                VideoReferenceResolution.MessageMissing,
                VideoReferenceResolution.UnsupportedMessage,
                -> {
                    recordTransparentRecoveryFinished(
                        attempt,
                        TransparentRecoveryOutcome.MESSAGE_UNAVAILABLE,
                    )
                    playerController.showFailure(
                        failed.video,
                        VideoPlaybackFailure.MESSAGE_UNAVAILABLE,
                    )
                }
                is VideoReferenceResolution.Unavailable -> {
                    recordTransparentRecoveryFinished(
                        attempt,
                        TransparentRecoveryOutcome.UNAVAILABLE,
                    )
                    publishDeferredFileFailure(attempt)
                }
                null -> {
                    recordTransparentRecoveryFinished(
                        attempt,
                        TransparentRecoveryOutcome.SOFT_TIMEOUT,
                    )
                    publishDeferredFileFailure(attempt)
                }
            }
        }
        return true
    }

    private fun isTransparentRecoveryCurrent(attempt: TransparentRecoveryAttempt): Boolean =
        isTransparentRecoveryPresentationCurrent(attempt) &&
            attempt.key.stablePageGeneration == stablePageGeneration &&
            items.getOrNull(currentPage)?.video?.key == attempt.key.videoKey &&
            attempt.token == currentPlanToken(attempt.token.randomRoundGeneration)

    private fun isTransparentRecoveryPresentationCurrent(
        attempt: TransparentRecoveryAttempt,
    ): Boolean =
        transparentRecoveryAttempt == attempt &&
            !pagerIsUnstable &&
            attempt.key.qualitySelectionGeneration == qualitySelectionGeneration &&
            attempt.key.accountGeneration == accountGeneration &&
            attempt.token.selection == qualitySelection &&
            playerSnapshot.playbackState.videoKeyOrNull() == attempt.key.videoKey

    private fun publishDeferredFileFailure(attempt: TransparentRecoveryAttempt) {
        if (!isTransparentRecoveryPresentationCurrent(attempt)) return
        playerController.finishFileRecoveryFailure(attempt.key.videoKey)
        playerSnapshot = attempt.failedSnapshot
        mutablePlaybackProgress.value = playerSnapshot.toProgressUiState()
        rebuildUiState()
    }

    private fun recordTransparentRecoveryFinished(
        attempt: TransparentRecoveryAttempt,
        outcome: TransparentRecoveryOutcome,
    ) {
        if (!isTransparentRecoveryPresentationCurrent(attempt)) return
        playerController.recordTransition(
            PlaybackTransitionEvent.TransparentRecoveryFinished(attempt.key.videoKey, outcome),
        )
    }

    private fun cancelTransparentRecovery(clearAttempt: Boolean) {
        transparentRecoveryJob?.cancel()
        transparentRecoveryJob = null
        if (clearAttempt) transparentRecoveryAttempt = null
    }

    private fun nextTarget(): QueueTarget? {
        if (items.isEmpty()) return null
        return if (criteria.value.order == VideoFeedOrder.RANDOM) {
            playbackQueue.randomRoundState?.nextEntry()?.let { QueueTarget(it.video, it) }
        } else {
            items.getOrNull(currentPage + 1)?.video?.let { QueueTarget(it, null) }
        }
    }

    private fun resolveQueueTarget(
        pagerPage: Int,
        logicalPage: Int,
    ): QueueTarget? {
        if (criteria.value.order != VideoFeedOrder.RANDOM) {
            return items.getOrNull(logicalPage)?.video?.let { QueueTarget(it, null) }
        }
        val state = playbackQueue.randomRoundState ?: return null
        val roundStart = randomRoundStartPagerPage
        val entry = if (roundStart == null) {
            state.current.items.getOrNull(logicalPage)?.let { state.current.entry(logicalPage) }
        } else {
            val offset = pagerPage - roundStart
            when {
                offset in state.current.items.indices -> state.current.entry(offset)
                offset >= state.current.items.size -> {
                    val upcoming = state.upcoming
                    if (upcoming == null || upcoming.items.isEmpty()) {
                        state.current.entry(Math.floorMod(offset, state.current.items.size))
                    } else {
                        val upcomingIndex = Math.floorMod(
                            offset - state.current.items.size,
                            upcoming.items.size,
                        )
                        upcoming.entry(upcomingIndex)
                    }
                }
                state.current.items.isNotEmpty() -> {
                    state.current.entry(Math.floorMod(offset, state.current.items.size))
                }
                else -> null
            }
        }
        return entry?.let { QueueTarget(it.video, it) }
    }

    private fun showUnavailableLink(message: String) {
        rebuildUiState(originalMessageLink = OriginalMessageLinkUiState.Unavailable(message))
    }

    private fun targetKnownEvent(
        target: QueueTarget,
        pagerPage: Int,
    ): PlaybackTransitionEvent.TargetKnown {
        val context = transitionContext(pagerPage, target)
        return PlaybackTransitionEvent.TargetKnown(
            key = target.video.key,
            order = context.order,
            direction = context.direction,
            randomRoundBoundary = context.randomRoundBoundary,
        )
    }

    private fun pageSettledEvent(
        target: QueueTarget,
        pagerPage: Int,
    ): PlaybackTransitionEvent.PageSettled {
        val context = transitionContext(pagerPage, target)
        return PlaybackTransitionEvent.PageSettled(
            key = target.video.key,
            order = context.order,
            direction = context.direction,
            randomRoundBoundary = context.randomRoundBoundary,
        )
    }

    private fun transitionContext(
        pagerPage: Int,
        target: QueueTarget,
    ): PlaybackTransitionContext {
        val previousPagerPage = lastSettledPage?.pagerPage
        val direction = when {
            previousPagerPage == null -> PlaybackTransitionDirection.INITIAL
            pagerPage > previousPagerPage -> PlaybackTransitionDirection.FORWARD
            pagerPage < previousPagerPage -> PlaybackTransitionDirection.REVERSE
            else -> PlaybackTransitionDirection.UNCHANGED
        }
        val order = criteria.value.order
        val randomRoundBoundary = order == VideoFeedOrder.RANDOM &&
            previousPagerPage != null &&
            target.randomEntry?.roundGeneration !=
            playbackQueue.randomRoundState?.current?.generation
        return PlaybackTransitionContext(
            order = order,
            direction = direction,
            randomRoundBoundary = randomRoundBoundary,
        )
    }

    private fun buildItems(
        videos: List<com.qixuan.channelvideoflow.model.video.IndexedVideo>,
        channelTitles: Map<Long, String>,
    ): List<FeedVideoItem> = videos.map { video ->
        buildItem(video, channelTitles)
    }

    private fun buildItem(
        video: com.qixuan.channelvideoflow.model.video.IndexedVideo,
        channelTitles: Map<Long, String> = latestChannelTitles,
    ): FeedVideoItem = FeedVideoItem(
        video = video,
        channelTitle = channelTitles[video.key.chatId] ?: "未知频道",
    )

    private fun rebuildUiState(
        originalMessageLink: OriginalMessageLinkUiState = mutableUiState.value.originalMessageLink,
    ) {
        mutableUiState.value = VideoPlaybackUiState(
            phase = when {
                !sourceLoaded -> VideoFeedPhase.LOADING
                items.isEmpty() -> VideoFeedPhase.EMPTY
                else -> VideoFeedPhase.CONTENT
            },
            items = items,
            upcomingItems = if (criteria.value.order == VideoFeedOrder.RANDOM) {
                buildItems(
                    playbackQueue.randomRoundState?.upcoming?.items.orEmpty(),
                    latestChannelTitles,
                )
            } else {
                emptyList()
            },
            order = criteria.value.order,
            queueGeneration = queueGeneration,
            randomRoundStartPagerPage = randomRoundStartPagerPage,
            currentPage = currentPage,
            player = playerSnapshot.toPresentationSnapshot(),
            showSwipeHint = swipeHintVisible,
            originalMessageLink = originalMessageLink,
        )
    }

    private fun maybeShowSwipeHint() {
        if (
            !swipeHintPreferenceLoaded ||
            swipeHintSeen ||
            swipeHintVisible ||
            swipeHintUserInteracted ||
            swipeHintHandledThisSession
        ) {
            return
        }
        val ready = playerSnapshot.playbackState as? VideoPlaybackState.Ready ?: return
        val currentVideo = items.getOrNull(currentPage)?.video ?: return
        if (
            !currentVideo.supportsStreaming ||
            ready.video.key != currentVideo.key ||
            !playerSnapshot.hasRenderedFirstFrame
        ) {
            return
        }
        swipeHintVisible = true
        rebuildUiState()
        swipeHintTimeoutJob?.cancel()
        swipeHintTimeoutJob = viewModelScope.launch {
            delay(SWIPE_HINT_DURATION_MILLIS)
            swipeHintTimeoutJob = null
            dismissSwipeHintIfVisible()
        }
    }

    private fun recordSwipeHintInteraction() {
        if (swipeHintUserInteracted) return
        swipeHintUserInteracted = true
        completeSwipeHintForSession()
    }

    private fun dismissSwipeHintIfVisible() {
        if (!swipeHintVisible) return
        completeSwipeHintForSession()
    }

    private fun completeSwipeHintForSession() {
        swipeHintTimeoutJob?.cancel()
        swipeHintTimeoutJob = null
        val wasVisible = swipeHintVisible
        swipeHintVisible = false
        swipeHintHandledThisSession = true
        if (wasVisible) rebuildUiState()
        if (swipeHintSeen || swipeHintMarkStarted) return
        swipeHintMarkStarted = true
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                withContext(NonCancellable) {
                    onboardingPreferences.markSwipeHintSeen()
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                // The current session remains handled even when persistence is unavailable.
            }
        }
    }

    private fun VideoPlayerSnapshot.toPresentationSnapshot(): VideoPlayerSnapshot = copy(
        positionMillis = 0L,
        durationMillis = 0L,
        bufferedPositionMillis = 0L,
        isSeekable = false,
    )

    private fun VideoPlayerSnapshot.toProgressUiState(): VideoPlaybackProgressUiState =
        VideoPlaybackProgressUiState(
            key = playbackState.videoKeyOrNull(),
            positionMillis = positionMillis,
            durationMillis = durationMillis,
            bufferedPositionMillis = bufferedPositionMillis,
            isSeekable = isSeekable,
        )

    private fun VideoPlaybackState.videoKeyOrNull(): VideoKey? = when (this) {
        VideoPlaybackState.Idle -> null
        is VideoPlaybackState.Loading -> video.key
        is VideoPlaybackState.Ready -> video.key
        is VideoPlaybackState.Unsupported -> video.key
        is VideoPlaybackState.Failed -> video.key
    }

    private data class FeedCriteria(
        val channelIds: Set<Long>? = null,
        val normalizedTags: Set<String> = emptySet(),
        val tagMode: TagFilterMode = TagFilterMode.OR,
        val order: VideoFeedOrder = DEFAULT_VIDEO_FEED_ORDER,
    )

    private data class FeedSource(
        val filter: VideoFilter,
        val order: VideoFeedOrder,
        val channelTitles: Map<Long, String>,
    )

    private data class FeedSourceResult(
        val source: FeedSource,
        val videos: List<com.qixuan.channelvideoflow.model.video.IndexedVideo>,
    )

    private data class PlaybackRefreshResult(
        val video: com.qixuan.channelvideoflow.model.video.IndexedVideo?,
        val outcome: PlaybackPlanRefreshOutcome,
        val terminalFailure: VideoPlaybackFailure? = null,
    )

    private data class QualitySelection(
        val preference: VideoQualityPreference,
        val network: NetworkTransport,
        val availableBandwidthBitsPerSecond: Long?,
    )

    private data class PlaybackPlanToken(
        val qualitySelectionGeneration: Long,
        val selection: QualitySelection,
        val accountGeneration: Long,
        val queueGeneration: Long,
        val randomRoundGeneration: Long?,
    )

    private enum class PlaybackSelectionResult {
        ORIGINAL,
        SERVER_VARIANT,
    }

    /**
     * Session-only playback metadata. It intentionally contains no caption, tags, or TDLib object.
     */
    private data class PlaybackPlan(
        val key: VideoKey,
        val sourceFileId: Int,
        val sourceRemoteUniqueId: String,
        val sourceFileSize: Long?,
        val sourceWidth: Int,
        val sourceHeight: Int,
        val durationSeconds: Int,
        val canBeSaved: Boolean,
        val playbackFileId: Int,
        val supportsStreaming: Boolean,
        val selectedAlternative: VideoPlaybackVariant?,
        val selectionResult: PlaybackSelectionResult,
        val token: PlaybackPlanToken,
        val refreshOutcome: PlaybackPlanRefreshOutcome,
        val refreshMillis: Long,
        val preparedAtMillis: Long,
        val terminalFailure: VideoPlaybackFailure?,
    ) {
        fun matches(expectedKey: VideoKey, expectedToken: PlaybackPlanToken): Boolean =
            key == expectedKey &&
                token == expectedToken &&
                playbackFileId == (selectedAlternative?.fileId ?: sourceFileId)

        fun isCompatibleWith(
            metadata: com.qixuan.channelvideoflow.model.video.IndexedVideo,
        ): Boolean =
            key == metadata.key &&
                sourceFileId == metadata.fileId &&
                sourceRemoteUniqueId == metadata.remoteUniqueId &&
                sourceFileSize == metadata.fileSize &&
                sourceWidth == metadata.width &&
                sourceHeight == metadata.height &&
                durationSeconds == metadata.durationSeconds &&
                canBeSaved == metadata.canBeSaved &&
                supportsStreaming == metadata.supportsStreaming &&
                (
                    selectedAlternative == null ||
                        selectedAlternative in metadata.alternativeVariants
                    )

        fun toVideo(base: com.qixuan.channelvideoflow.model.video.IndexedVideo):
            com.qixuan.channelvideoflow.model.video.IndexedVideo {
            require(base.key == key) { "PlaybackPlan key mismatch" }
            return base.copy(
                fileId = sourceFileId,
                remoteUniqueId = sourceRemoteUniqueId,
                supportsStreaming = supportsStreaming,
                fileSize = sourceFileSize,
                durationSeconds = durationSeconds,
                width = sourceWidth,
                height = sourceHeight,
                canBeSaved = canBeSaved,
                alternativeVariants = emptyList(),
                selectedAlternative = selectedAlternative,
            )
        }

        companion object {
            fun from(
                video: com.qixuan.channelvideoflow.model.video.IndexedVideo,
                token: PlaybackPlanToken,
                refreshOutcome: PlaybackPlanRefreshOutcome,
                refreshMillis: Long,
                preparedAtMillis: Long,
                terminalFailure: VideoPlaybackFailure? = null,
            ): PlaybackPlan = PlaybackPlan(
                key = video.key,
                sourceFileId = video.fileId,
                sourceRemoteUniqueId = video.remoteUniqueId,
                sourceFileSize = video.fileSize,
                sourceWidth = video.width,
                sourceHeight = video.height,
                durationSeconds = video.durationSeconds,
                canBeSaved = video.canBeSaved,
                playbackFileId = video.playbackFileId,
                supportsStreaming = video.supportsStreaming,
                selectedAlternative = video.selectedAlternative,
                selectionResult = if (video.selectedAlternative == null) {
                    PlaybackSelectionResult.ORIGINAL
                } else {
                    PlaybackSelectionResult.SERVER_VARIANT
                },
                token = token,
                refreshOutcome = refreshOutcome,
                refreshMillis = refreshMillis,
                preparedAtMillis = preparedAtMillis,
                terminalFailure = terminalFailure,
            )
        }
    }

    private data class PlaybackPlanSlots(
        val current: PlaybackPlan? = null,
        val next: PlaybackPlan? = null,
    )

    private data class PlanPreparation(
        val generation: Long,
        val key: VideoKey,
        val token: PlaybackPlanToken,
        val deferred: Deferred<PlaybackPlan?>,
    )

    private data class TransparentRecoveryKey(
        val videoKey: VideoKey,
        val stablePageGeneration: Long,
        val qualitySelectionGeneration: Long,
        val accountGeneration: Long,
        val queueGeneration: Long,
    )

    private data class TransparentRecoveryAttempt(
        val key: TransparentRecoveryKey,
        val token: PlaybackPlanToken,
        val failedSnapshot: VideoPlayerSnapshot,
        val failedPlaybackFileId: Int,
        val attempted: Boolean = true,
    )

    private data class SettledPage(
        val pagerPage: Int,
        val key: VideoKey,
        val queueGeneration: Long,
    )

    private data class QueueTarget(
        val video: com.qixuan.channelvideoflow.model.video.IndexedVideo,
        val randomEntry: RandomRoundEntry?,
    ) {
        fun samePosition(other: QueueTarget): Boolean =
            video.key == other.video.key &&
                randomEntry?.roundGeneration == other.randomEntry?.roundGeneration &&
                randomEntry?.index == other.randomEntry?.index
    }

    private data class PlaybackTransitionContext(
        val order: VideoFeedOrder,
        val direction: PlaybackTransitionDirection,
        val randomRoundBoundary: Boolean,
    )

    private companion object {
        const val QUALITY_REFRESH_TIMEOUT_MILLIS = 3_000L
        const val SWIPE_HINT_DURATION_MILLIS = 2_000L

        fun monotonicTimeMillis(): Long = System.nanoTime() / 1_000_000L
    }
}
