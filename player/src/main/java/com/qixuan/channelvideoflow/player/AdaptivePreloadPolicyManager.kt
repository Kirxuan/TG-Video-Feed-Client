package com.qixuan.channelvideoflow.player

import android.util.Log
import com.qixuan.channelvideoflow.domain.cache.MediaCacheController
import com.qixuan.channelvideoflow.domain.media.AdaptivePreloadController
import com.qixuan.channelvideoflow.domain.media.AdaptivePreloadDecision
import com.qixuan.channelvideoflow.domain.media.AdaptivePreloadEnvironment
import com.qixuan.channelvideoflow.domain.media.AdaptivePreloadPolicyStateMachine
import com.qixuan.channelvideoflow.domain.media.DevicePreloadPolicySource
import com.qixuan.channelvideoflow.domain.media.TelegramFileSnapshot
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

@Singleton
class AdaptivePreloadPolicyManager @Inject constructor(
    devicePolicySource: DevicePreloadPolicySource,
    cacheController: MediaCacheController,
) : AdaptivePreloadController {
    private val lock = Any()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val stateMachine = AdaptivePreloadPolicyStateMachine(
        AdaptivePreloadEnvironment(
            signals = devicePolicySource.signals.value,
            mobileDataEnabled = cacheController.state.value.mobileDataPreloadEnabled,
            qualityPreference = cacheController.state.value.videoQualityPreference,
        ),
    )
    private val mutableDecision = MutableStateFlow(stateMachine.decision)
    override val decision: StateFlow<AdaptivePreloadDecision> = mutableDecision.asStateFlow()

    init {
        scope.launch {
            combine(devicePolicySource.signals, cacheController.state) { signals, cache ->
                AdaptivePreloadEnvironment(
                    signals = signals,
                    mobileDataEnabled = cache.mobileDataPreloadEnabled,
                    qualityPreference = cache.videoQualityPreference,
                )
            }.collect { environment ->
                update { stateMachine.updateEnvironment(environment) }
            }
        }
    }

    override fun onCurrentBind(cacheHit: Boolean) {
        update { stateMachine.onCurrentBind(cacheHit) }
    }

    override fun onFirstFrame(bindToFirstFrameMillis: Long) {
        update { stateMachine.onFirstFrame(bindToFirstFrameMillis) }
    }

    override fun onPlaybackFailure() {
        update(stateMachine::onPlaybackFailure)
    }

    override fun onRebufferStarted() {
        update(stateMachine::onRebufferStarted)
    }

    override fun onRebufferRecovered() {
        update(stateMachine::onRebufferRecovered)
    }

    override fun onCurrentReleased() {
        update(stateMachine::onCurrentReleased)
    }

    private fun update(block: () -> AdaptivePreloadDecision) {
        synchronized(lock) {
            val previous = mutableDecision.value
            val next = block()
            mutableDecision.value = next
            if (next != previous) trace(next)
        }
    }

    private fun trace(decision: AdaptivePreloadDecision) {
        if (!BuildConfig.DEBUG) return
        runCatching {
            Log.i(
                LOG_TAG,
                "state=${decision.state} reason=${decision.reason} " +
                    "bytes=${decision.maxPreloadBytes} samples=${decision.recentSampleCount} " +
                    "recentP90Ms=${decision.recentP90Millis}",
            )
        }
    }

    private companion object {
        const val LOG_TAG = "CVF-Adaptive"
    }
}

internal fun hasStartupCacheHit(
    snapshot: TelegramFileSnapshot?,
    fileSize: Long?,
): Boolean {
    val requiredBytes = fileSize
        ?.takeIf { it > 0L }
        ?.coerceAtMost(STARTUP_CACHE_HIT_BYTES)
        ?: STARTUP_CACHE_HIT_BYTES
    return snapshot?.covers(start = 0L, length = requiredBytes) == true
}

private const val STARTUP_CACHE_HIT_BYTES = 64L * 1024L
