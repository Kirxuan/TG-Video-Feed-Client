package com.qixuan.channelvideoflow.player

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.ui.PlayerView
import com.qixuan.channelvideoflow.domain.media.AdaptivePreloadController
import com.qixuan.channelvideoflow.domain.media.NoOpStreamingNetworkMetricsRepository
import com.qixuan.channelvideoflow.domain.media.AdaptivePreloadReason
import com.qixuan.channelvideoflow.domain.media.PlaybackRiskAction
import com.qixuan.channelvideoflow.domain.media.PlaybackRiskController
import com.qixuan.channelvideoflow.domain.media.PlaybackRiskInput
import com.qixuan.channelvideoflow.domain.media.PlaybackRiskReason
import com.qixuan.channelvideoflow.domain.media.PlaybackRiskState
import com.qixuan.channelvideoflow.domain.media.NextPreloadSafetySnapshot
import com.qixuan.channelvideoflow.domain.media.NetworkTransport
import com.qixuan.channelvideoflow.domain.media.StreamingNetworkMetricsRepository
import com.qixuan.channelvideoflow.domain.media.TelegramFileGateway
import com.qixuan.channelvideoflow.domain.media.TelegramFileOwnerKind
import com.qixuan.channelvideoflow.domain.media.TelegramFileProtectionLease
import com.qixuan.channelvideoflow.domain.media.VideoPreloadController
import com.qixuan.channelvideoflow.model.video.IndexedVideo
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

sealed interface VideoPlaybackState {
    data object Idle : VideoPlaybackState
    data class Loading(val video: IndexedVideo) : VideoPlaybackState
    data class Ready(
        val video: IndexedVideo,
        val firstReadyWaitMillis: Long?,
        val observedLocalBytes: Long?,
    ) : VideoPlaybackState

    data class Unsupported(val video: IndexedVideo) : VideoPlaybackState
    data class Failed(
        val video: IndexedVideo,
        val reason: VideoPlaybackFailure,
    ) : VideoPlaybackState
}

enum class VideoPlaybackFailure {
    NETWORK,
    TIMEOUT,
    FILE_UNAVAILABLE,
    MESSAGE_UNAVAILABLE,
    DECODER_UNSUPPORTED,
    PLAYER,
    UNKNOWN,
}

/**
 * Application-scoped owner for the single ExoPlayer instance used by the test page.
 */
@Singleton
@UnstableApi
class VideoPlayerManager @Inject internal constructor(
    @ApplicationContext context: Context,
    private val gateway: TelegramFileGateway,
    private val adaptivePreloadController: AdaptivePreloadController,
    private val videoPreloadController: VideoPreloadController,
    private val samplePreloadController: SamplePreloadController,
    private val networkMetrics: StreamingNetworkMetricsRepository =
        NoOpStreamingNetworkMetricsRepository,
) : VideoPlaybackController {
    private val appContext = context.applicationContext
    private val bufferPolicy = PlaybackBufferPolicy(
        candidateId = BuildConfig.PLAYBACK_TUNING_CANDIDATE,
        minBufferMillis = MIN_BUFFER_MILLIS,
        maxBufferMillis = MAX_BUFFER_MILLIS,
        bufferForPlaybackMillis = BuildConfig.PLAYBACK_STARTUP_BUFFER_MILLIS,
        bufferForPlaybackAfterRebufferMillis = BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MILLIS,
        prioritizeTimeOverSizeThresholds = BuildConfig.PLAYBACK_PRIORITIZE_TIME_OVER_SIZE,
        backBufferMillis = BuildConfig.PLAYBACK_BACK_BUFFER_MILLIS,
        targetBufferBytes = BuildConfig.PLAYBACK_TARGET_BUFFER_BYTES,
        startOrder = if (BuildConfig.PLAYBACK_PLAY_BEFORE_PREPARE) {
            PlaybackStartOrder.PLAY_THEN_PREPARE
        } else {
            PlaybackStartOrder.PREPARE_THEN_PLAY
        },
    )
    private val mutableState = MutableStateFlow<VideoPlaybackState>(VideoPlaybackState.Idle)
    val state: StateFlow<VideoPlaybackState> = mutableState.asStateFlow()
    private val mutableSnapshot = MutableStateFlow(VideoPlayerSnapshot())
    override val snapshot: StateFlow<VideoPlayerSnapshot> = mutableSnapshot.asStateFlow()

    private val progressHandler = Handler(Looper.getMainLooper())
    private val progressTicker = object : Runnable {
        override fun run() {
            refreshPlaybackProgress()
            if (progressTickerRunning) {
                progressHandler.postDelayed(this, PROGRESS_UPDATE_INTERVAL_MILLIS)
            }
        }
    }

    private val playerLifecycle = ReusablePlayerLifecycle<MediaSource>(
        factory = { createPlayerEngine() },
        startOrder = bufferPolicy.startOrder,
    )
    private var activePlayerListener: Player.Listener? = null
    private val playerViewBinding = StablePlayerViewBinding<PlayerView, Player>(
        currentPlayer = PlayerView::getPlayer,
        setPlayer = PlayerView::setPlayer,
    )
    private var surfaceAttachCount = 0
    private var surfaceDetachCount = 0
    private val callbackGate = PlaybackCallbackGate()
    private var boundVideo: IndexedVideo? = null
    private var bindStartedAtNanos: Long = 0L
    private var firstByteMetricLogged = false
    private var firstReadyWaitMillis: Long? = null
    private var progressTickerRunning = false
    private var currentProtection: TelegramFileProtectionLease? = null
    private var activeHlsSession: TelegramHlsPlaybackSession? = null
    private var activeSourceKind = PlaybackSourceKind.PROGRESSIVE
    private val hlsFallbackGate = HlsFallbackGate()
    private val tdLibBandwidthMeter = TdLibBandwidthMeter(networkMetrics)
    private val playbackRiskController = PlaybackRiskController()
    private var activeRangeSession: PlaybackRangeRequestSession? = null
    private val playbackMetrics = PlaybackSessionMetrics()
    private val transitionMetrics = PlaybackTransitionMetrics()
    private var lastPlaybackSampleAtNanos = 0L
    private var adaptiveRebufferActive = false
    private var reportedAdaptiveRebufferCount = 0
    private var seekRiskActive = false
    private var lastAbrBufferedAheadMillis = 0L
    private var lastAbrEvaluationMillis = 0L
    private var lastAbrMaxBitrate = Int.MAX_VALUE
    private var lastAbrReason: PlaybackRiskReason? = null

    override fun attach(playerView: PlayerView) {
        val exoPlayer = ensurePlayer()
        val change = playerViewBinding.attach(playerView, exoPlayer)
        if (!change.attached) return
        if (change.detached) surfaceDetachCount += 1
        surfaceAttachCount += 1
        trace(
            "surface attach count=$surfaceAttachCount " +
                "detachCount=$surfaceDetachCount playerInstances=${playerLifecycle.instanceCount}",
        )
    }

    override fun detach(playerView: PlayerView) {
        if (!playerViewBinding.detach(playerView)) return
        surfaceDetachCount += 1
        trace("surface detach count=$surfaceDetachCount attachCount=$surfaceAttachCount")
    }

    override fun recordTransition(event: PlaybackTransitionEvent) {
        if (!BuildConfig.DEBUG) return
        transitionMetrics.onEvent(event)?.let(::traceTransitionSummary)
    }

    override fun bind(video: IndexedVideo) {
        restoreNormalPlaybackSpeed(TemporaryPlaybackSpeedTermination.NEW_BINDING)
        adaptivePreloadController.onCurrentBind(
            cacheHit = hasStartupCacheHit(
                snapshot = gateway.currentSnapshot(video.playbackFileId),
                fileSize = video.playbackFileSize,
            ),
        )
        if (BuildConfig.DEBUG) transitionMetrics.onBindStarted(video.key)
        if (!video.supportsStreaming) {
            callbackGate.invalidate()
            detachActivePlayerListener()
            clearCurrentBinding()
            currentProtection?.close()
            currentProtection = null
            boundVideo = video
            updatePlaybackState(VideoPlaybackState.Unsupported(video), isPaused = false)
            if (BuildConfig.DEBUG) {
                transitionMetrics.onUnsupported(video.key)?.let(::traceTransitionSummary)
            }
            return
        }
        val nextProtection = gateway.pinFile(
            fileId = video.playbackFileId,
            ownerToken = "current-playback-${PROTECTION_COUNTER.incrementAndGet()}",
            ownerKind = TelegramFileOwnerKind.CURRENT_PLAYBACK,
        )
        val exoPlayer = ensurePlayer()
        val binding = callbackGate.begin(video.key)
        detachActivePlayerListener()
        retireCurrentBindingForReplacement()
        currentProtection?.close()
        currentProtection = nextProtection
        boundVideo = video
        firstReadyWaitMillis = null
        bindStartedAtNanos = System.nanoTime()
        firstByteMetricLogged = false
        playbackMetrics.start()
        adaptiveRebufferActive = false
        reportedAdaptiveRebufferCount = 0
        seekRiskActive = false
        lastAbrBufferedAheadMillis = 0L
        lastAbrEvaluationMillis = 0L
        lastAbrMaxBitrate = Int.MAX_VALUE
        lastAbrReason = null
        lastPlaybackSampleAtNanos = 0L
        trace(
            "quality alternative=${video.selectedAlternative != null} " +
                "fileId=${video.playbackFileId} width=${video.playbackWidth} " +
                "height=${video.playbackHeight} size=${video.playbackFileSize}",
        )
        updatePlaybackState(VideoPlaybackState.Loading(video), isPaused = false)
        createPlayerListener(exoPlayer, video, binding).also { listener ->
            activePlayerListener = listener
            exoPlayer.addListener(listener)
        }
        val sampleHandoff = samplePreloadController.takeForPlayback(video)
        val requestSession = sampleHandoff?.requestSession ?: PlaybackRangeRequestSession()
        val hlsSession = sampleHandoff?.hlsSession ?: if (BuildConfig.TELEGRAM_HLS_ENABLED) {
            runCatching { TelegramHlsPlaybackSession.create(video, gateway) }
                .onFailure { trace("hls registration result=MP4_FALLBACK") }
                .getOrNull()
        } else {
            null
        }
        activeHlsSession = hlsSession
        activeSourceKind = sampleHandoff?.sourceKind ?: if (hlsSession != null) {
            PlaybackSourceKind.HLS
        } else {
            PlaybackSourceKind.PROGRESSIVE
        }
        hlsFallbackGate.begin(binding.generation, activeSourceKind)
        val playbackSource = sampleHandoff?.source ?: if (hlsSession != null) {
            createHlsMediaSource(hlsSession, requestSession)
        } else {
            createProgressiveMediaSource(
                video = video,
                rangeSession = requestSession,
                acceptsRangeCallback = { callbackGate.accepts(binding) },
            )
        }
        activeRangeSession = requestSession
        playerLifecycle.bind(
            media = playbackSource,
            bindingGeneration = binding.generation,
            onPrepare = {
                if (BuildConfig.DEBUG) transitionMetrics.onPrepare(video.key)
            },
        )
        startProgressUpdates()
    }

    override fun retry() {
        boundVideo?.let(::bind)
    }

    override fun showFailure(video: IndexedVideo, failure: VideoPlaybackFailure) {
        restoreNormalPlaybackSpeed(TemporaryPlaybackSpeedTermination.FAILURE)
        callbackGate.invalidate()
        detachActivePlayerListener()
        clearCurrentBinding()
        currentProtection?.close()
        currentProtection = null
        boundVideo = video
        updatePlaybackState(VideoPlaybackState.Failed(video, failure), isPaused = false)
        adaptivePreloadController.onPlaybackFailure()
        if (BuildConfig.DEBUG) {
            transitionMetrics.onFailure(video.key)?.let(::traceTransitionSummary)
        }
    }

    override fun finishFileRecoveryFailure(key: com.qixuan.channelvideoflow.model.video.VideoKey) {
        if (BuildConfig.DEBUG) {
            transitionMetrics.onFailure(key)?.let(::traceTransitionSummary)
        }
    }

    override fun pause() {
        restoreNormalPlaybackSpeed(TemporaryPlaybackSpeedTermination.PAUSE)
        playbackMetrics.markPaused()
        currentPlayer()?.pause()
        mutableSnapshot.value = mutableSnapshot.value.copy(
            isPaused = true,
            isPlaying = false,
        )
        stopProgressUpdates()
    }

    override fun resume() {
        if (boundVideo == null || mutableSnapshot.value.playbackState is VideoPlaybackState.Unsupported) {
            return
        }
        currentPlayer()?.play()
        mutableSnapshot.value = mutableSnapshot.value.copy(isPaused = false)
        startProgressUpdates()
    }

    override fun seekTo(positionMillis: Long) {
        restoreNormalPlaybackSpeed(TemporaryPlaybackSpeedTermination.SEEK)
        val exoPlayer = currentPlayer() ?: return
        if (boundVideo == null || !exoPlayer.isCurrentMediaItemSeekable) return
        val duration = normalizedDuration(exoPlayer.duration)
        if (duration <= 0L) return
        val target = positionMillis.coerceIn(0L, duration)
        playbackMetrics.markSeek()
        mutableSnapshot.value = mutableSnapshot.value.copy(hasRenderedFirstFrame = false)
        activeRangeSession?.onUserSeek()
        seekRiskActive = true
        exoPlayer.seekTo(target)
        refreshPlaybackProgress()
    }

    override fun pauseForPageTransition() {
        restoreNormalPlaybackSpeed(TemporaryPlaybackSpeedTermination.PAGE_UNSTABLE)
        playbackMetrics.markPaused()
        currentPlayer()?.pause()
        mutableSnapshot.value = mutableSnapshot.value.copy(isPlaying = false)
        stopProgressUpdates()
    }

    override fun setMuted(muted: Boolean) {
        currentPlayer()?.volume = if (muted) MUTED_VOLUME else NORMAL_VOLUME
        mutableSnapshot.value = mutableSnapshot.value.copy(isMuted = muted)
    }

    override fun setTemporaryPlaybackSpeed(active: Boolean) {
        val current = mutableSnapshot.value
        val ready = current.playbackState as? VideoPlaybackState.Ready
        val canActivate = active &&
            ready != null &&
            ready.video.key == boundVideo?.key &&
            current.hasRenderedFirstFrame &&
            current.isPlaying &&
            !current.isPaused
        val applied = if (active && canActivate) {
            playerLifecycle.setTemporaryPlaybackSpeed(active = true)
        } else {
            playerLifecycle.terminateTemporaryPlaybackSpeed(
                TemporaryPlaybackSpeedTermination.USER_RELEASE,
            )
        }
        mutableSnapshot.value = mutableSnapshot.value.copy(playbackSpeed = applied)
    }

    override fun onAppBackgrounded() {
        trace("background pause")
        restoreNormalPlaybackSpeed(TemporaryPlaybackSpeedTermination.BACKGROUND)
        pause()
    }

    override fun releaseBinding() {
        trace("release binding")
        restoreNormalPlaybackSpeed(TemporaryPlaybackSpeedTermination.UNBIND)
        if (BuildConfig.DEBUG) {
            transitionMetrics.onRelease()?.let(::traceTransitionSummary)
        }
        callbackGate.invalidate()
        detachActivePlayerListener()
        clearCurrentBinding()
        currentProtection?.close()
        currentProtection = null
        boundVideo = null
        adaptivePreloadController.onCurrentReleased()
        updatePlaybackState(VideoPlaybackState.Idle, isPaused = false)
    }

    override fun release() {
        releaseBinding()
        stopProgressUpdates()
        if (playerViewBinding.detachActive()) surfaceDetachCount += 1
        playerLifecycle.release()
        samplePreloadController.release()
        progressHandler.removeCallbacks(progressTicker)
    }

    private fun ensurePlayer(): ExoPlayer = ensurePlayerEngine().player

    private fun ensurePlayerEngine(): ExoPlayerReusableEngine =
        playerLifecycle.ensureEngine() as ExoPlayerReusableEngine

    private fun createPlayerEngine(): ReusablePlayerEngine<MediaSource> {
        val builder = ExoPlayer.Builder(appContext)
            .setAudioAttributes(
                VideoAudioPolicy.attributes,
                VideoAudioPolicy.handleAudioFocus,
            )
            .setHandleAudioBecomingNoisy(true)
        if (BuildConfig.HYBRID_ABR_ENABLED) {
            builder.setBandwidthMeter(tdLibBandwidthMeter)
        }
        val player = samplePreloadController.buildPlayer(builder, buildLoadControl())
            .also { created ->
                created.volume = if (mutableSnapshot.value.isMuted) MUTED_VOLUME else NORMAL_VOLUME
            }

        tracePlayerConfiguration()
        return ExoPlayerReusableEngine(player)
    }

    private fun tracePlayerConfiguration() {
        trace(
                "config candidate=${bufferPolicy.candidateId} " +
                "minBufferMs=${bufferPolicy.minBufferMillis} " +
                "maxBufferMs=${bufferPolicy.maxBufferMillis} " +
                "startupMs=${bufferPolicy.bufferForPlaybackMillis} " +
                "rebufferMs=${bufferPolicy.bufferForPlaybackAfterRebufferMillis} " +
                "prioritizeTime=${bufferPolicy.prioritizeTimeOverSizeThresholds} " +
                "backBufferMs=${bufferPolicy.backBufferMillis} " +
                "targetBufferBytes=${bufferPolicy.targetBufferBytes} " +
                "startOrder=${bufferPolicy.startOrder}",
        )
    }

    private fun createProgressiveMediaSource(
        video: IndexedVideo,
        rangeSession: PlaybackRangeRequestSession,
        acceptsRangeCallback: () -> Boolean,
    ): MediaSource = ProgressiveMediaSource.Factory(
            TelegramMediaDataSource.Factory(
                gateway = gateway,
                requestSession = rangeSession,
                onCurrentRangeLeaseAcquired = { acquired ->
                    if (acceptsRangeCallback()) {
                        if (acquired) {
                            videoPreloadController.onCurrentPlaybackRangeAcquired(video)
                        } else {
                            videoPreloadController.onCurrentPlaybackRangeAcquireFailed(video)
                        }
                    }
                },
            ),
        )
            .setLoadErrorHandlingPolicy(
                DefaultLoadErrorHandlingPolicy(MAX_PLAYER_LOAD_RETRY_COUNT),
            )
            .createMediaSource(
                MediaItem.Builder()
                    .setUri(TelegramMediaDataSource.uriForFile(video.playbackFileId))
                    .build(),
            )

    private fun createHlsMediaSource(
        session: TelegramHlsPlaybackSession,
        rangeSession: PlaybackRangeRequestSession,
    ): MediaSource = HlsMediaSource.Factory(
        TelegramHlsDataSource.Factory(
            gateway = gateway,
            session = session,
            rangeSession = rangeSession,
        ),
    )
        .setLoadErrorHandlingPolicy(
            DefaultLoadErrorHandlingPolicy(MAX_PLAYER_LOAD_RETRY_COUNT),
        )
        .createMediaSource(MediaItem.fromUri(session.masterUri))

    private fun buildLoadControl(): DefaultLoadControl {
        val builder = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                bufferPolicy.minBufferMillis,
                bufferPolicy.maxBufferMillis,
                bufferPolicy.bufferForPlaybackMillis,
                bufferPolicy.bufferForPlaybackAfterRebufferMillis,
            )
            .setPrioritizeTimeOverSizeThresholds(
                bufferPolicy.prioritizeTimeOverSizeThresholds,
            )
            .setBackBuffer(
                bufferPolicy.backBufferMillis,
                false,
            )
        if (bufferPolicy.targetBufferBytes > 0) {
            builder.setTargetBufferBytes(bufferPolicy.targetBufferBytes)
        }
        return builder.build()
    }

    private fun createPlayerListener(
        exoPlayer: ExoPlayer,
        video: IndexedVideo,
        binding: PlaybackBindingToken,
    ): Player.Listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (!callbackGate.accepts(binding)) return
            trace("isPlaying=$isPlaying")
            mutableSnapshot.value = mutableSnapshot.value.copy(isPlaying = isPlaying)
            reconcileTemporaryPlaybackSpeed(exoPlayer, binding)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (!callbackGate.accepts(binding)) return
            reconcileTemporaryPlaybackSpeed(exoPlayer, binding)
            if (!playbackMetrics.isActive) return
            val metrics = playbackMetrics.onPlaybackStateChanged(
                newPlaybackState = playbackState,
                playbackExpected = exoPlayer.playWhenReady &&
                    !mutableSnapshot.value.isPaused,
            )
            if (metrics.rebufferCount > reportedAdaptiveRebufferCount) {
                reportedAdaptiveRebufferCount = metrics.rebufferCount
                adaptiveRebufferActive = true
                adaptivePreloadController.onRebufferStarted()
                networkMetrics.onRebuffer()
            }
            if (playbackState == Player.STATE_READY && adaptiveRebufferActive) {
                adaptiveRebufferActive = false
                adaptivePreloadController.onRebufferRecovered()
            }
            refreshPlaybackProgress()
            tracePlaybackMetrics("state", metrics)
            if (playbackState == Player.STATE_READY) {
                if (BuildConfig.DEBUG) transitionMetrics.onReady(video.key)
                val firstReadyWait = firstReadyWaitMillis ?: (
                    (System.nanoTime() - bindStartedAtNanos) / 1_000_000L
                ).also { firstReadyWaitMillis = it }
                val ready = VideoPlaybackState.Ready(
                    video = video,
                    firstReadyWaitMillis = firstReadyWait,
                    observedLocalBytes =
                        gateway.currentSnapshot(video.playbackFileId)?.downloadedSize,
                )
                updatePlaybackState(ready)
                trace(
                    "ready waitMillis=${ready.firstReadyWaitMillis} " +
                        "localBytes=${ready.observedLocalBytes}",
                )
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (!callbackGate.accepts(binding)) return
            reconcileTemporaryPlaybackSpeed(exoPlayer, binding)
        }

        override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) {
            if (!callbackGate.accepts(binding)) return
            reconcileTemporaryPlaybackSpeed(exoPlayer, binding)
        }

        override fun onRenderedFirstFrame() {
            if (!callbackGate.accepts(binding)) return
            if (mutableSnapshot.value.playbackState.videoKeyOrNull() != video.key) return
            val firstFrameObservedAtMillis = System.nanoTime() / NANOS_PER_MILLISECOND
            val firstFrameBufferedDurationMillis =
                (exoPlayer.bufferedPosition - exoPlayer.currentPosition).coerceAtLeast(0L)
            val firstRangeReady = if (BuildConfig.DEBUG && !firstByteMetricLogged) {
                activeRangeSession?.firstRangeReady()
            } else {
                null
            }
            val transitionSnapshot = if (BuildConfig.DEBUG) {
                // Capture the physical callback boundary before StateFlow observers,
                // priority demotion, and next-preload restoration do synchronous work.
                firstRangeReady?.let { firstRange ->
                    firstByteMetricLogged = true
                    transitionMetrics.onFirstByte(
                        key = video.key,
                        observedAtMillis = firstRange.atNanos / NANOS_PER_MILLISECOND,
                    )
                }
                transitionMetrics.onReady(video.key, firstFrameObservedAtMillis)
                transitionMetrics.onFirstFrame(
                    key = video.key,
                    observedAtMillis = firstFrameObservedAtMillis,
                    bufferedDurationMillis = firstFrameBufferedDurationMillis,
                )
            } else {
                null
            }
            mutableSnapshot.value = mutableSnapshot.value.copy(
                hasRenderedFirstFrame = true,
            )
            seekRiskActive = false
            playbackMetrics.markFirstFrame()
            activeRangeSession?.onFirstFrame()
            adaptivePreloadController.onFirstFrame(
                bindToFirstFrameMillis = (
                    firstFrameObservedAtMillis - bindStartedAtNanos / NANOS_PER_MILLISECOND
                    ).coerceAtLeast(0L),
            )
            if (BuildConfig.DEBUG) {
                firstRangeReady?.let { firstRange ->
                    val bindToFirstByteMillis =
                        (firstRange.atNanos - bindStartedAtNanos)
                            .coerceAtLeast(0L) / NANOS_PER_MILLISECOND
                    trace(
                        "range first-byte fileId=${firstRange.fileId} " +
                            "priority=${firstRange.priority} " +
                            "bindToFirstByteMs=$bindToFirstByteMillis",
                    )
                }
                // Some Media3/device combinations dispatch the first-frame listener
                // before the queued READY listener. Preserve an earlier READY timestamp
                // when available and otherwise use callback entry as its upper bound.
                traceStartupRangeSummary(
                    requestSession = activeRangeSession,
                    transitionSnapshot = transitionSnapshot,
                )
                transitionSnapshot?.let(::traceTransitionSummary)
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            if (!callbackGate.accepts(binding)) return
            if (fallbackFromHls(video, binding)) {
                trace("hls error code=${error.errorCode} result=MP4_FALLBACK")
                return
            }
            restoreNormalPlaybackSpeed(TemporaryPlaybackSpeedTermination.FAILURE)
            stopProgressUpdates()
            activeRangeSession?.close()
            activeRangeSession = null
            currentProtection?.close()
            currentProtection = null
            val failure = mapVideoPlaybackFailure(error.errorCode, error.cause)
            val diagnostic = mapPlaybackFailureDiagnostic(error.errorCode, error.cause)
            updatePlaybackState(
                VideoPlaybackState.Failed(
                    video = video,
                    reason = failure,
                ),
            )
            adaptivePreloadController.onPlaybackFailure()
            trace(
                "error category=$failure diagnostic=$diagnostic code=${error.errorCode}",
            )
            if (BuildConfig.DEBUG && failure != VideoPlaybackFailure.FILE_UNAVAILABLE) {
                transitionMetrics.onFailure(video.key)?.let(::traceTransitionSummary)
            }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            if (callbackGate.accepts(binding)) refreshPlaybackProgress()
        }

        override fun onTimelineChanged(
            timeline: androidx.media3.common.Timeline,
            reason: Int,
        ) {
            if (callbackGate.accepts(binding)) refreshPlaybackProgress()
        }
    }

    private fun detachActivePlayerListener() {
        val listener = activePlayerListener ?: return
        currentPlayer()?.removeListener(listener)
        activePlayerListener = null
    }

    private fun retireCurrentBindingForReplacement() {
        restoreNormalPlaybackSpeed(TemporaryPlaybackSpeedTermination.NEW_BINDING)
        activeRangeSession?.close()
        activeRangeSession = null
        activeHlsSession?.close()
        activeHlsSession = null
        activeSourceKind = PlaybackSourceKind.PROGRESSIVE
        hlsFallbackGate.clear()
        tracePlaybackSummary()
        playbackMetrics.reset()
        stopProgressUpdates()
        resetPlaybackProgress()
    }

    private fun clearCurrentBinding() {
        retireCurrentBindingForReplacement()
        playerLifecycle.releaseBinding()
    }

    private fun fallbackFromHls(
        video: IndexedVideo,
        binding: PlaybackBindingToken,
    ): Boolean {
        if (!hlsFallbackGate.tryFallback(binding.generation, activeSourceKind)) return false
        activeRangeSession?.close()
        activeHlsSession?.close()
        activeHlsSession = null
        activeSourceKind = PlaybackSourceKind.PROGRESSIVE
        val rangeSession = PlaybackRangeRequestSession()
        activeRangeSession = rangeSession
        updatePlaybackState(VideoPlaybackState.Loading(video), isPaused = false)
        playerLifecycle.bind(
            media = createProgressiveMediaSource(
                video = video,
                rangeSession = rangeSession,
                acceptsRangeCallback = { callbackGate.accepts(binding) },
            ),
            bindingGeneration = binding.generation,
            onPrepare = {
                if (BuildConfig.DEBUG) transitionMetrics.onPrepare(video.key)
            },
        )
        return true
    }

    private fun resetPlaybackProgress() {
        mutableSnapshot.value = mutableSnapshot.value.copy(
            positionMillis = 0L,
            durationMillis = 0L,
            bufferedPositionMillis = 0L,
            isSeekable = false,
        )
    }

    private fun updatePlaybackState(
        state: VideoPlaybackState,
        isPaused: Boolean = mutableSnapshot.value.isPaused,
    ) {
        mutableState.value = state
        mutableSnapshot.value = mutableSnapshot.value.copy(
            playbackState = state,
            isPaused = isPaused,
            isPlaying = when (state) {
                is VideoPlaybackState.Ready -> mutableSnapshot.value.isPlaying
                VideoPlaybackState.Idle,
                is VideoPlaybackState.Loading,
                is VideoPlaybackState.Unsupported,
                is VideoPlaybackState.Failed,
                -> false
            },
            playbackSpeed = when (state) {
                is VideoPlaybackState.Ready -> mutableSnapshot.value.playbackSpeed
                VideoPlaybackState.Idle,
                is VideoPlaybackState.Loading,
                is VideoPlaybackState.Unsupported,
                is VideoPlaybackState.Failed,
                -> VideoPlaybackSpeeds.NORMAL
            },
            hasRenderedFirstFrame = when (state) {
                is VideoPlaybackState.Ready ->
                    mutableSnapshot.value.playbackState.videoKeyOrNull() == state.video.key &&
                        mutableSnapshot.value.hasRenderedFirstFrame
                VideoPlaybackState.Idle,
                is VideoPlaybackState.Loading,
                is VideoPlaybackState.Unsupported,
                is VideoPlaybackState.Failed,
                -> false
            },
        )
    }

    private fun restoreNormalPlaybackSpeed(reason: TemporaryPlaybackSpeedTermination) {
        val applied = playerLifecycle.terminateTemporaryPlaybackSpeed(reason)
        if (mutableSnapshot.value.playbackSpeed != applied) {
            mutableSnapshot.value = mutableSnapshot.value.copy(playbackSpeed = applied)
        }
    }

    private fun reconcileTemporaryPlaybackSpeed(
        exoPlayer: ExoPlayer,
        binding: PlaybackBindingToken,
    ) {
        val applied = playerLifecycle.reconcileTemporaryPlaybackSpeed(
            bindingGeneration = binding.generation,
            playbackState = when (exoPlayer.playbackState) {
                Player.STATE_BUFFERING -> ReusablePlaybackState.BUFFERING
                Player.STATE_READY -> ReusablePlaybackState.READY
                Player.STATE_ENDED -> ReusablePlaybackState.ENDED
                else -> ReusablePlaybackState.IDLE
            },
            isPlaying = exoPlayer.isPlaying,
            playWhenReady = exoPlayer.playWhenReady,
            isSuppressed = exoPlayer.playbackSuppressionReason !=
                Player.PLAYBACK_SUPPRESSION_REASON_NONE,
        )
        if (mutableSnapshot.value.playbackSpeed != applied) {
            mutableSnapshot.value = mutableSnapshot.value.copy(playbackSpeed = applied)
        }
    }

    private fun trace(message: String) {
        if (BuildConfig.DEBUG) Log.i(LOG_TAG, message)
    }

    private fun startProgressUpdates() {
        if (progressTickerRunning) return
        progressTickerRunning = true
        progressHandler.removeCallbacks(progressTicker)
        progressHandler.post(progressTicker)
    }

    private fun stopProgressUpdates() {
        progressTickerRunning = false
        progressHandler.removeCallbacks(progressTicker)
    }

    private fun refreshPlaybackProgress() {
        val exoPlayer = currentPlayer() ?: return
        val duration = normalizedDuration(exoPlayer.duration)
        val position = exoPlayer.currentPosition
            .coerceAtLeast(0L)
            .let { current -> if (duration > 0L) current.coerceAtMost(duration) else current }
        val buffered = exoPlayer.bufferedPosition
            .coerceAtLeast(position)
            .let { current -> if (duration > 0L) current.coerceAtMost(duration) else current }
        mutableSnapshot.value = mutableSnapshot.value.copy(
            positionMillis = position,
            durationMillis = duration,
            bufferedPositionMillis = buffered,
            isSeekable = duration > 0L && exoPlayer.isCurrentMediaItemSeekable,
        )
        applyPlaybackRisk(exoPlayer, bufferedAheadMillis = (buffered - position).coerceAtLeast(0L))
        maybeTracePlaybackSample()
    }

    private fun applyPlaybackRisk(exoPlayer: ExoPlayer, bufferedAheadMillis: Long) {
        val estimate = networkMetrics.estimate.value
        val now = System.nanoTime() / NANOS_PER_MILLISECOND
        val elapsed = (now - lastAbrEvaluationMillis).coerceAtLeast(1L)
        val slope = if (lastAbrEvaluationMillis == 0L) {
            0.0
        } else {
            (bufferedAheadMillis - lastAbrBufferedAheadMillis).toDouble() / elapsed.toDouble()
        }
        lastAbrEvaluationMillis = now
        lastAbrBufferedAheadMillis = bufferedAheadMillis
        val preloadDecision = adaptivePreloadController.decision.value
        val riskState = when {
            adaptiveRebufferActive -> PlaybackRiskState.REBUFFER
            seekRiskActive -> PlaybackRiskState.SEEK
            !mutableSnapshot.value.hasRenderedFirstFrame -> PlaybackRiskState.STARTUP
            else -> PlaybackRiskState.PLAYING
        }
        videoPreloadController.updateCurrentPlaybackSafety(
            NextPreloadSafetySnapshot(
                playbackState = riskState,
                currentBufferedSeconds = bufferedAheadMillis / 1_000.0,
                bufferSlopeSecondsPerSecond = slope,
                fastThroughputBitsPerSecond = estimate?.fastBitsPerSecond,
                slowThroughputBitsPerSecond = estimate?.slowBitsPerSecond,
                timeToFirstByteP90Millis = estimate?.timeToFirstByteP90Millis,
                isMetered = !preloadDecision.isUnmeteredWifi,
                isMobileNetwork = estimate?.network == NetworkTransport.MOBILE,
                isPowerSaver = preloadDecision.reason == AdaptivePreloadReason.POWER_SAVE,
                hasThermalPressure = preloadDecision.reason == AdaptivePreloadReason.THERMAL,
                hasStoragePressure = preloadDecision.reason == AdaptivePreloadReason.STORAGE_LOW,
                networkGeneration = estimate?.networkGeneration ?: networkMetrics.contextRevision,
            ),
        )
        if (!BuildConfig.HYBRID_ABR_ENABLED || activeSourceKind != PlaybackSourceKind.HLS) return
        val video = boundVideo ?: return
        val bitrates = video.hlsCapableVariants
            .map { variant ->
                variant.fileSize
                    ?.takeIf { it > 0L && video.durationSeconds > 0 }
                    ?.let { it * 8L / video.durationSeconds }
                    ?: when {
                        variant.height <= 360 -> 450_000L
                        variant.height <= 480 -> 800_000L
                        variant.height <= 720 -> 1_500_000L
                        else -> 3_000_000L
                    }
            }
            .sorted()
        if (bitrates.isEmpty()) return
        val format = exoPlayer.videoFormat
        val currentBitrate = listOfNotNull(
            format?.peakBitrate?.takeIf { it > 0 }?.toLong(),
            format?.averageBitrate?.takeIf { it > 0 }?.toLong(),
            format?.bitrate?.takeIf { it > 0 }?.toLong(),
        ).maxOrNull() ?: bitrates.first()
        val candidate = bitrates.firstOrNull { it > currentBitrate } ?: currentBitrate
        val request = gateway.currentNetworkRequest()
            ?.takeIf { it.ownerKind == TelegramFileOwnerKind.CURRENT_PLAYBACK }
        val decision = playbackRiskController.evaluate(
            PlaybackRiskInput(
                fastThroughputBitsPerSecond = estimate?.fastBitsPerSecond,
                slowThroughputBitsPerSecond = estimate?.slowBitsPerSecond,
                timeToFirstByteP50Millis = estimate?.timeToFirstByteP50Millis,
                timeToFirstByteP90Millis = estimate?.timeToFirstByteP90Millis,
                currentBufferedDurationMillis = bufferedAheadMillis,
                bufferSlopeSecondsPerSecond = slope,
                currentRepresentationBitrate = currentBitrate,
                candidatePeakBitrate = candidate,
                minimumRepresentationBitrate = bitrates.first(),
                currentRequestDownloadedBytes = request?.downloadedBytes ?: 0L,
                currentRequestRemainingBytes = request?.remainingBytes ?: 0L,
                nextPlayableSeconds = 0.0,
                playbackState = riskState,
                isMetered = !preloadDecision.isUnmeteredWifi,
                isPowerSaver = preloadDecision.reason == AdaptivePreloadReason.POWER_SAVE,
                hasThermalPressure = preloadDecision.reason == AdaptivePreloadReason.THERMAL,
                hasStoragePressure = preloadDecision.reason == AdaptivePreloadReason.STORAGE_LOW,
                networkGeneration = estimate?.networkGeneration ?: networkMetrics.contextRevision,
                nowMillis = now,
            ),
        )
        val nextMax = when (decision.action) {
            PlaybackRiskAction.DOWNGRADE,
            PlaybackRiskAction.ABANDON_REQUEST,
            PlaybackRiskAction.ACCUMULATE_RESERVOIR,
            -> decision.maximumSafeBitrate.coerceAtMost(currentBitrate)
            PlaybackRiskAction.UPGRADE -> candidate
            PlaybackRiskAction.KEEP -> null
        }?.coerceIn(bitrates.first(), Int.MAX_VALUE.toLong())?.toInt()
        if (nextMax != null && nextMax != lastAbrMaxBitrate) {
            if (decision.action == PlaybackRiskAction.ABANDON_REQUEST) {
                // Cancels the bounded TDLib lease. Media3's HLS loader retries under the new
                // track ceiling; the reusable player and playback binding stay intact.
                activeRangeSession?.onUserSeek()
            }
            (exoPlayer.trackSelector as? DefaultTrackSelector)?.let { selector ->
                selector.setParameters(
                    selector.buildUponParameters().setMaxVideoBitrate(nextMax),
                )
                lastAbrMaxBitrate = nextMax
            }
        }
        if (decision.action != PlaybackRiskAction.KEEP || decision.reason != lastAbrReason) {
            trace(
                "abr action=${decision.action} reason=${decision.reason} " +
                    "bufferMs=$bufferedAheadMillis slope=$slope " +
                    "completeMs=${decision.predictedCompletionMillis} " +
                    "deadlineMs=${decision.starvationDeadlineMillis} maxBitrate=${decision.maximumSafeBitrate}",
            )
            lastAbrReason = decision.reason
        }
    }

    private fun maybeTracePlaybackSample() {
        if (!BuildConfig.DEBUG || !playbackMetrics.isActive) return
        val now = System.nanoTime()
        if (
            lastPlaybackSampleAtNanos != 0L &&
            now - lastPlaybackSampleAtNanos < PLAYBACK_SAMPLE_INTERVAL_NANOS
        ) {
            return
        }
        lastPlaybackSampleAtNanos = now
        tracePlaybackMetrics("sample", playbackMetrics.snapshot())
    }

    private fun tracePlaybackSummary() {
        if (!BuildConfig.DEBUG || !playbackMetrics.isActive) return
        val snapshot = mutableSnapshot.value
        val metrics = playbackMetrics.snapshot()
        trace(
                "summary firstReadyWaitMs=$firstReadyWaitMillis " +
                "state=${metrics.stateName} positionMs=${snapshot.positionMillis} " +
                "bufferedMs=${snapshot.bufferedPositionMillis} " +
                "aheadMs=${(snapshot.bufferedPositionMillis - snapshot.positionMillis).coerceAtLeast(0L)} " +
                "rebufferCount=${metrics.rebufferCount} " +
                "totalRebufferMs=${metrics.totalRebufferDurationMillis}" +
                metrics.windowLogFields(),
        )
    }

    private fun tracePlaybackMetrics(
        event: String,
        metrics: PlaybackBufferingMetrics,
    ) {
        val snapshot = mutableSnapshot.value
        trace(
            "$event state=${metrics.stateName} positionMs=${snapshot.positionMillis} " +
                "bufferedMs=${snapshot.bufferedPositionMillis} " +
                "aheadMs=${(snapshot.bufferedPositionMillis - snapshot.positionMillis).coerceAtLeast(0L)} " +
                "rebufferCount=${metrics.rebufferCount} " +
                "activeRebufferMs=${metrics.activeRebufferDurationMillis} " +
                "totalRebufferMs=${metrics.totalRebufferDurationMillis}" +
                metrics.windowLogFields() +
                (
                    metrics.recoveredRebufferDurationMillis?.let { recovered ->
                        " recoveredRebufferMs=$recovered"
                    } ?: ""
                    ),
        )
    }

    private fun traceTransitionSummary(snapshot: PlaybackTransitionSnapshot) {
        val key = snapshot.key
        Log.i(
            TRANSITION_LOG_TAG,
            "summary outcome=${snapshot.outcome} " +
                "candidate=${bufferPolicy.candidateId} " +
                "order=${snapshot.order} direction=${snapshot.direction} " +
                "randomRoundBoundary=${snapshot.randomRoundBoundary} " +
                "chatId=${key?.chatId} messageId=${key?.messageId} " +
                "refreshOutcome=${snapshot.refreshOutcome} " +
                "transparentRecoveryAttempts=${snapshot.transparentRecoveryAttemptCount} " +
                "transparentRecoveryOutcome=${snapshot.transparentRecoveryOutcome} " +
                "promoted=${snapshot.promoted} planAgeMs=${snapshot.planAgeMillis} " +
                "gestureToSettleMs=${snapshot.gestureToSettledMillis} " +
                "settleToPlanMs=${snapshot.settledToPlanMillis} " +
                "refreshMs=${snapshot.refreshMillis} " +
                "planToBindMs=${snapshot.planToBindMillis} " +
                "bindToPrepareMs=${snapshot.bindToPrepareMillis} " +
                "prepareToReadyMs=${snapshot.prepareToReadyMillis} " +
                "bindToFirstByteMs=${snapshot.bindToFirstByteMillis} " +
                "bindToReadyMs=${snapshot.bindToReadyMillis} " +
                "readyToFirstFrameMs=${snapshot.readyToFirstFrameMillis} " +
                "firstFrameBufferedMs=${snapshot.firstFrameBufferedDurationMillis} " +
                "surfaceAttachCount=$surfaceAttachCount " +
                "surfaceDetachCount=$surfaceDetachCount " +
                "playerInstances=${playerLifecycle.instanceCount} " +
                "gestureToReleaseMs=${snapshot.gestureToReleaseMillis} " +
                "gestureToTargetKnownMs=${snapshot.gestureToTargetKnownMillis} " +
                "releaseToSettleMs=${snapshot.releaseToSettledMillis} " +
                "targetKnownToSettleMs=${snapshot.targetKnownToSettledMillis} " +
                "targetKnownToPlanReadyMs=${snapshot.targetKnownToPlanPreparedMillis} " +
                "planReadyToSettleMs=${snapshot.planPreparedToSettledMillis} " +
                "bindToTerminalMs=${snapshot.bindToTerminalMillis} " +
                "settleToTerminalMs=${snapshot.settledToTerminalMillis} " +
                "releaseToTerminalMs=${snapshot.releaseToTerminalMillis} " +
                "targetKnownToTerminalMs=${snapshot.targetKnownToTerminalMillis} " +
                "gestureToTerminalMs=${snapshot.gestureToTerminalMillis}",
        )
    }

    private fun traceStartupRangeSummary(
        requestSession: PlaybackRangeRequestSession?,
        transitionSnapshot: PlaybackTransitionSnapshot?,
    ) {
        val observation = requestSession?.startupRangeObservation() ?: return
        val firstByteToReadyMillis = transitionSnapshot?.let { transition ->
            val firstByte = transition.bindToFirstByteMillis ?: return@let null
            val ready = transition.bindToReadyMillis ?: return@let null
            (ready - firstByte).coerceAtLeast(0L)
        }
        val handoff = videoPreloadController.ownerHandoff.value
        val reusedNextOwner = handoff.promotionMatched && handoff.reusedActiveRequest == true
        Log.i(
            STARTUP_RANGE_LOG_TAG,
            "summary candidate=${BuildConfig.STARTUP_RANGE_CANDIDATE} " +
                "firstMissCategory=${observation.firstMissCategory ?: "NONE"} " +
                "coveredBeforeCurrentBytes=${observation.coveredBeforeCurrentBytes} " +
                "dataSpecOpenCount=${observation.dataSpecOpenCount} " +
                "extractorRangeSwitchCount=${observation.extractorRangeSwitchCount} " +
                "currentReusedNextOwner=$reusedNextOwner " +
                "firstByteToReadyMs=$firstByteToReadyMillis",
        )
    }

    private fun normalizedDuration(durationMillis: Long): Long =
        durationMillis.takeUnless { it == C.TIME_UNSET || it < 0L } ?: 0L

    private fun currentPlayer(): ExoPlayer? =
        (playerLifecycle.currentEngine as? ExoPlayerReusableEngine)?.player

    private fun PlaybackBufferingMetrics.windowLogFields(): String =
        " firstFrameElapsedMs=$firstFrameElapsedMillis" +
            " rebuffer30Count=${rebufferAt30Seconds?.count}" +
            " rebuffer30Ms=${rebufferAt30Seconds?.durationMillis}" +
            " rebuffer60Count=${rebufferAt60Seconds?.count}" +
            " rebuffer60Ms=${rebufferAt60Seconds?.durationMillis}"

    private companion object {
        const val MIN_BUFFER_MILLIS = 50_000
        const val MAX_BUFFER_MILLIS = 60_000
        const val BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MILLIS = 12_000
        const val PROGRESS_UPDATE_INTERVAL_MILLIS = 250L
        const val PLAYBACK_SAMPLE_INTERVAL_MILLIS = 5_000L
        const val PLAYBACK_SAMPLE_INTERVAL_NANOS =
            PLAYBACK_SAMPLE_INTERVAL_MILLIS * 1_000_000L
        const val NANOS_PER_MILLISECOND = 1_000_000L
        const val MAX_PLAYER_LOAD_RETRY_COUNT = 3
        const val NORMAL_VOLUME = 1f
        const val MUTED_VOLUME = 0f
        const val LOG_TAG = "CVF-Player"
        const val TRANSITION_LOG_TAG = "CVF-Transition"
        const val STARTUP_RANGE_LOG_TAG = "CVF-StartupRange"
        val PROTECTION_COUNTER = AtomicLong()
    }

    private fun VideoPlaybackState.videoKeyOrNull() = when (this) {
        VideoPlaybackState.Idle -> null
        is VideoPlaybackState.Loading -> video.key
        is VideoPlaybackState.Ready -> video.key
        is VideoPlaybackState.Unsupported -> video.key
        is VideoPlaybackState.Failed -> video.key
    }
}

@UnstableApi
private class ExoPlayerReusableEngine(
    val player: ExoPlayer,
) : ReusablePlayerEngine<MediaSource> {
    override val playbackSpeed: Float
        get() = player.playbackParameters.speed

    override fun setMedia(media: MediaSource) {
        player.setMediaSource(media)
    }

    override fun prepare() {
        player.prepare()
    }

    override fun setPlayWhenReady(playWhenReady: Boolean) {
        player.playWhenReady = playWhenReady
    }

    override fun pause() {
        player.pause()
    }

    override fun setPlaybackSpeed(speed: Float) {
        player.setPlaybackSpeed(speed)
    }

    override fun clearMedia() {
        player.clearMediaItems()
    }

    override fun release() {
        player.release()
    }
}

internal data class PlaybackBindingToken(
    val generation: Long,
    val key: com.qixuan.channelvideoflow.model.video.VideoKey,
)

internal enum class PlaybackSourceKind {
    PROGRESSIVE,
    HLS,
}

/** Allows one HLS-to-MP4 fallback for the active binding without rebuilding the player. */
internal class HlsFallbackGate {
    private var generation: Long? = null
    private var fallbackUsed = false

    @Synchronized
    fun begin(bindingGeneration: Long, sourceKind: PlaybackSourceKind) {
        generation = bindingGeneration
        fallbackUsed = sourceKind != PlaybackSourceKind.HLS
    }

    @Synchronized
    fun tryFallback(bindingGeneration: Long, sourceKind: PlaybackSourceKind): Boolean {
        if (
            generation != bindingGeneration ||
            sourceKind != PlaybackSourceKind.HLS ||
            fallbackUsed
        ) {
            return false
        }
        fallbackUsed = true
        return true
    }

    @Synchronized
    fun clear() {
        generation = null
        fallbackUsed = false
    }
}

/** Rejects READY, first-frame, and error callbacks from superseded Media3 bindings. */
internal class PlaybackCallbackGate {
    private var generation = 0L
    private var active: PlaybackBindingToken? = null

    @Synchronized
    fun begin(key: com.qixuan.channelvideoflow.model.video.VideoKey): PlaybackBindingToken {
        generation += 1L
        return PlaybackBindingToken(generation, key).also { active = it }
    }

    @Synchronized
    fun accepts(token: PlaybackBindingToken): Boolean = active == token

    @Synchronized
    fun invalidate() {
        generation += 1L
        active = null
    }
}

internal fun mapVideoPlaybackFailure(
    errorCode: Int,
    cause: Throwable?,
): VideoPlaybackFailure = when {
    cause is TelegramMediaTimeoutException -> VideoPlaybackFailure.TIMEOUT
    cause is TelegramMediaUnavailableException -> VideoPlaybackFailure.FILE_UNAVAILABLE
    cause is TelegramMediaReadException -> VideoPlaybackFailure.FILE_UNAVAILABLE
    errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ->
        VideoPlaybackFailure.NETWORK
    errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
        VideoPlaybackFailure.TIMEOUT
    errorCode == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ->
        VideoPlaybackFailure.FILE_UNAVAILABLE
    errorCode in setOf(
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODING_RESOURCES_RECLAIMED,
    ) -> VideoPlaybackFailure.DECODER_UNSUPPORTED
    else -> VideoPlaybackFailure.PLAYER
}

internal enum class PlaybackFailureDiagnostic {
    DECODER_INITIALIZATION,
    DECODER_QUERY,
    DECODING,
    FORMAT,
    NETWORK,
    TIMEOUT,
    FILE,
    PLAYER,
}

internal fun mapPlaybackFailureDiagnostic(
    errorCode: Int,
    cause: Throwable?,
): PlaybackFailureDiagnostic = when {
    cause is TelegramMediaTimeoutException -> PlaybackFailureDiagnostic.TIMEOUT
    cause is TelegramMediaUnavailableException || cause is TelegramMediaReadException ->
        PlaybackFailureDiagnostic.FILE
    errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ->
        PlaybackFailureDiagnostic.NETWORK
    errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
        PlaybackFailureDiagnostic.TIMEOUT
    errorCode == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ->
        PlaybackFailureDiagnostic.FILE
    errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ->
        PlaybackFailureDiagnostic.DECODER_INITIALIZATION
    errorCode == PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED ->
        PlaybackFailureDiagnostic.DECODER_QUERY
    errorCode in setOf(
        PlaybackException.ERROR_CODE_DECODING_FAILED,
        PlaybackException.ERROR_CODE_DECODING_RESOURCES_RECLAIMED,
    ) -> PlaybackFailureDiagnostic.DECODING
    errorCode in setOf(
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
    ) -> PlaybackFailureDiagnostic.FORMAT
    else -> PlaybackFailureDiagnostic.PLAYER
}

internal object VideoAudioPolicy {
    val attributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
        .build()
    const val handleAudioFocus: Boolean = true
}
