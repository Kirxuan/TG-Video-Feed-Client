package com.qixuan.channelvideoflow.domain.media

import com.qixuan.channelvideoflow.model.video.VideoQualityPreference
import org.junit.Assert.assertEquals
import org.junit.Test

class AdaptivePreloadPolicyTest {
    @Test
    fun currentItemAlwaysYieldsUntilFirstFrameThenRecoversWithHysteresis() {
        val policy = AdaptivePreloadPolicyStateMachine(wifiEnvironment())

        assertDecision(policy.onCurrentBind(cacheHit = true), AdaptivePreloadState.OFF, 0L)
        assertEquals(true, policy.decision.isUnmeteredWifi)
        assertEquals(
            AdaptivePreloadReason.CURRENT_NOT_STABLE,
            policy.decision.reason,
        )

        assertDecision(
            policy.onFirstFrame(bindToFirstFrameMillis = 400L),
            AdaptivePreloadState.CONSERVATIVE,
            AdaptivePreloadPolicyStateMachine.CONSERVATIVE_PRELOAD_BYTES,
        )
        assertEquals(AdaptivePreloadReason.RECOVERING, policy.decision.reason)

        policy.onCurrentBind(cacheHit = true)
        assertDecision(
            policy.onFirstFrame(bindToFirstFrameMillis = 420L),
            AdaptivePreloadState.NORMAL,
            AdaptivePreloadPolicyStateMachine.NORMAL_PRELOAD_BYTES,
        )
    }

    @Test
    fun cacheMissNeedsEnoughFastEvidenceBeforeNormal() {
        val policy = AdaptivePreloadPolicyStateMachine(wifiEnvironment())

        repeat(3) {
            policy.onCurrentBind(cacheHit = false)
            policy.onFirstFrame(bindToFirstFrameMillis = 500L)
            assertEquals(AdaptivePreloadState.CONSERVATIVE, policy.decision.state)
        }
        policy.onCurrentBind(cacheHit = false)
        assertEquals(
            AdaptivePreloadState.NORMAL,
            policy.onFirstFrame(bindToFirstFrameMillis = 500L).state,
        )
    }

    @Test
    fun recentLongTailDegradesImmediatelyAndFastSamplesMustAgeItOut() {
        val policy = AdaptivePreloadPolicyStateMachine(wifiEnvironment())
        repeat(2) {
            policy.onCurrentBind(cacheHit = true)
            policy.onFirstFrame(bindToFirstFrameMillis = 300L)
        }
        assertEquals(AdaptivePreloadState.NORMAL, policy.decision.state)

        policy.onCurrentBind(cacheHit = true)
        val degraded = policy.onFirstFrame(bindToFirstFrameMillis = 900L)
        assertEquals(AdaptivePreloadState.CONSERVATIVE, degraded.state)
        assertEquals(AdaptivePreloadReason.RECENT_FIRST_FRAME_TAIL, degraded.reason)

        repeat(4) {
            policy.onCurrentBind(cacheHit = true)
            policy.onFirstFrame(bindToFirstFrameMillis = 300L)
            assertEquals(AdaptivePreloadState.CONSERVATIVE, policy.decision.state)
        }
        policy.onCurrentBind(cacheHit = true)
        policy.onFirstFrame(bindToFirstFrameMillis = 300L)
        assertEquals(AdaptivePreloadState.CONSERVATIVE, policy.decision.state)
        policy.onCurrentBind(cacheHit = true)
        assertEquals(
            AdaptivePreloadState.NORMAL,
            policy.onFirstFrame(bindToFirstFrameMillis = 300L).state,
        )
    }

    @Test
    fun networkSwitchLowResourcesFailuresAndRebufferClosePreloadImmediately() {
        val policy = AdaptivePreloadPolicyStateMachine(wifiEnvironment())
        repeat(2) {
            policy.onCurrentBind(cacheHit = true)
            policy.onFirstFrame(bindToFirstFrameMillis = 300L)
        }

        val switched = policy.updateEnvironment(
            wifiEnvironment(networkGeneration = 2L),
        )
        assertEquals(AdaptivePreloadState.OFF, switched.state)
        assertEquals(AdaptivePreloadReason.NETWORK_CHANGED, switched.reason)

        policy.onCurrentBind(cacheHit = true)
        assertEquals(
            AdaptivePreloadState.CONSERVATIVE,
            policy.onFirstFrame(bindToFirstFrameMillis = 300L).state,
        )

        assertEquals(
            AdaptivePreloadReason.MEMORY_LOW,
            policy.updateEnvironment(
                wifiEnvironment(
                    networkGeneration = 2L,
                    isMemoryLow = true,
                ),
            ).reason,
        )
        assertEquals(
            AdaptivePreloadReason.STORAGE_LOW,
            policy.updateEnvironment(
                wifiEnvironment(
                    networkGeneration = 2L,
                    isStorageLow = true,
                ),
            ).reason,
        )

        policy.updateEnvironment(wifiEnvironment(networkGeneration = 2L))
        policy.onPlaybackFailure()
        val consecutiveFailure = policy.onPlaybackFailure()
        assertEquals(AdaptivePreloadState.OFF, consecutiveFailure.state)
        assertEquals(
            AdaptivePreloadReason.CONSECUTIVE_FAILURES,
            consecutiveFailure.reason,
        )

        policy.onCurrentBind(cacheHit = true)
        policy.onFirstFrame(bindToFirstFrameMillis = 300L)
        val rebuffer = policy.onRebufferStarted()
        assertEquals(AdaptivePreloadState.OFF, rebuffer.state)
        assertEquals(AdaptivePreloadReason.REBUFFER, rebuffer.reason)
        assertEquals(
            AdaptivePreloadState.CONSERVATIVE,
            policy.onRebufferRecovered().state,
        )
    }

    @Test
    fun mobileAndMeteredNetworksRemainOffByDefaultAndExplicitOptInIsBounded() {
        val mobileDefault = AdaptivePreloadPolicyStateMachine(
            AdaptivePreloadEnvironment(
                signals = DevicePreloadSignals(
                    network = NetworkTransport.MOBILE,
                    isMetered = true,
                ),
                mobileDataEnabled = false,
                qualityPreference = VideoQualityPreference.AUTO,
            ),
        )
        assertEquals(AdaptivePreloadState.OFF, mobileDefault.decision.state)
        assertEquals(false, mobileDefault.decision.isUnmeteredWifi)
        assertEquals(
            AdaptivePreloadReason.NETWORK_NOT_ALLOWED,
            mobileDefault.decision.reason,
        )

        val explicit = AdaptivePreloadPolicyStateMachine(
            AdaptivePreloadEnvironment(
                signals = DevicePreloadSignals(
                    network = NetworkTransport.MOBILE,
                    isMetered = true,
                ),
                mobileDataEnabled = true,
                qualityPreference = VideoQualityPreference.AUTO,
            ),
        )
        repeat(3) {
            explicit.onCurrentBind(cacheHit = true)
            explicit.onFirstFrame(bindToFirstFrameMillis = 250L)
        }
        assertDecision(
            explicit.decision,
            AdaptivePreloadState.CONSERVATIVE,
            AdaptivePreloadPolicyStateMachine.CONSERVATIVE_PRELOAD_BYTES,
        )
    }

    @Test
    fun qualityPreferenceIsObservedButNeverOverriddenByAdaptivePolicy() {
        val decisions = VideoQualityPreference.entries.map { quality ->
            val policy = AdaptivePreloadPolicyStateMachine(
                wifiEnvironment(qualityPreference = quality),
            )
            repeat(2) {
                policy.onCurrentBind(cacheHit = true)
                policy.onFirstFrame(bindToFirstFrameMillis = 300L)
            }
            policy.decision
        }

        assertEquals(setOf(AdaptivePreloadState.NORMAL), decisions.map { it.state }.toSet())
        assertEquals(
            setOf(AdaptivePreloadPolicyStateMachine.NORMAL_PRELOAD_BYTES),
            decisions.map { it.maxPreloadBytes }.toSet(),
        )
    }

    private fun wifiEnvironment(
        networkGeneration: Long = 1L,
        isMemoryLow: Boolean = false,
        isStorageLow: Boolean = false,
        qualityPreference: VideoQualityPreference = VideoQualityPreference.AUTO,
    ) = AdaptivePreloadEnvironment(
        signals = DevicePreloadSignals(
            network = NetworkTransport.WIFI,
            isMetered = false,
            isMemoryLow = isMemoryLow,
            isStorageLow = isStorageLow,
            networkGeneration = networkGeneration,
        ),
        mobileDataEnabled = false,
        qualityPreference = qualityPreference,
    )

    private fun assertDecision(
        decision: AdaptivePreloadDecision,
        state: AdaptivePreloadState,
        maxBytes: Long,
    ) {
        assertEquals(state, decision.state)
        assertEquals(maxBytes, decision.maxPreloadBytes)
    }
}
