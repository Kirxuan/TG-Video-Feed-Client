package com.qixuan.channelvideoflow.domain.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoPreloadPolicyTest {
    @Test
    fun wifiAllowsPreloadButMobileIsDisabledByDefault() {
        assertTrue(
            VideoPreloadPolicy.evaluate(
                DevicePreloadSignals(network = NetworkTransport.WIFI),
                mobileDataEnabled = false,
            ).allowed,
        )
        val mobile = VideoPreloadPolicy.evaluate(
            DevicePreloadSignals(network = NetworkTransport.MOBILE),
            mobileDataEnabled = false,
        )
        assertFalse(mobile.allowed)
        assertEquals(PreloadBlockedReason.NETWORK_NOT_ALLOWED, mobile.blockedReason)
    }

    @Test
    fun explicitMobileSettingStillYieldsToPowerStorageAndNetworkSafety() {
        assertTrue(
            VideoPreloadPolicy.evaluate(
                DevicePreloadSignals(network = NetworkTransport.MOBILE),
                mobileDataEnabled = true,
            ).allowed,
        )
        assertEquals(
            PreloadBlockedReason.POWER_SAVE,
            VideoPreloadPolicy.evaluate(
                DevicePreloadSignals(
                    network = NetworkTransport.MOBILE,
                    isPowerSaveMode = true,
                ),
                mobileDataEnabled = true,
            ).blockedReason,
        )
        assertEquals(
            PreloadBlockedReason.STORAGE_LOW,
            VideoPreloadPolicy.evaluate(
                DevicePreloadSignals(
                    network = NetworkTransport.WIFI,
                    isStorageLow = true,
                ),
                mobileDataEnabled = true,
            ).blockedReason,
        )
        assertEquals(
            PreloadBlockedReason.OFFLINE,
            VideoPreloadPolicy.evaluate(
                DevicePreloadSignals(network = NetworkTransport.OFFLINE),
                mobileDataEnabled = true,
            ).blockedReason,
        )
    }

    @Test
    fun moderateAndSevereThermalStatesStopPreloadWhileUnknownDoesNotPretendToBeHot() {
        assertTrue(
            VideoPreloadPolicy.evaluate(
                DevicePreloadSignals(
                    network = NetworkTransport.WIFI,
                    thermalState = DeviceThermalState.UNKNOWN,
                ),
                mobileDataEnabled = false,
            ).allowed,
        )
        listOf(
            DeviceThermalState.MODERATE,
            DeviceThermalState.SEVERE,
            DeviceThermalState.CRITICAL,
        ).forEach { thermal ->
            assertEquals(
                PreloadBlockedReason.THERMAL,
                VideoPreloadPolicy.evaluate(
                    DevicePreloadSignals(
                        network = NetworkTransport.WIFI,
                        thermalState = thermal,
                    ),
                    mobileDataEnabled = false,
                ).blockedReason,
            )
        }
    }
}
