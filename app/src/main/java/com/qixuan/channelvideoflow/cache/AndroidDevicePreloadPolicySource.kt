package com.qixuan.channelvideoflow.cache

import android.content.BroadcastReceiver
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.app.ActivityManager
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.PowerManager
import android.os.StatFs
import androidx.core.content.ContextCompat
import com.qixuan.channelvideoflow.domain.media.DevicePreloadPolicySource
import com.qixuan.channelvideoflow.domain.media.DevicePreloadSignals
import com.qixuan.channelvideoflow.domain.media.DeviceThermalState
import com.qixuan.channelvideoflow.domain.media.NetworkTransport
import com.qixuan.channelvideoflow.telegram.di.TelegramApplicationScope
import com.qixuan.channelvideoflow.telegram.di.TelegramIoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Singleton
class AndroidDevicePreloadPolicySource @Inject constructor(
    @ApplicationContext context: Context,
    @param:TelegramApplicationScope private val scope: CoroutineScope,
    @param:TelegramIoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : DevicePreloadPolicySource {
    private val appContext = context.applicationContext
    private val connectivityManager =
        appContext.getSystemService(ConnectivityManager::class.java)
    private val activityManager = appContext.getSystemService(ActivityManager::class.java)
    private val powerManager = appContext.getSystemService(PowerManager::class.java)
    private val mutableSignals = MutableStateFlow(DevicePreloadSignals())
    override val signals: StateFlow<DevicePreloadSignals> = mutableSignals.asStateFlow()
    private var observedNetwork: Network? = null
    private var networkGeneration = 0L

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = scheduleUpdate()
        override fun onLost(network: Network) = scheduleUpdate()
        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities,
        ) = scheduleUpdate()
    }

    private val systemReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = scheduleUpdate()
    }

    private val thermalListener =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            PowerManager.OnThermalStatusChangedListener { scheduleUpdate() }
        } else {
            null
        }

    private val memoryCallbacks = object : ComponentCallbacks2 {
        override fun onTrimMemory(level: Int) {
            if (
                level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ||
                level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL
            ) {
                mutableSignals.value = mutableSignals.value.copy(isMemoryLow = true)
                scheduleUpdate()
            }
        }

        override fun onLowMemory() {
            mutableSignals.value = mutableSignals.value.copy(isMemoryLow = true)
            scheduleUpdate()
        }

        override fun onConfigurationChanged(newConfig: Configuration) = Unit
    }

    init {
        runCatching { connectivityManager.registerDefaultNetworkCallback(networkCallback) }
        runCatching {
            ContextCompat.registerReceiver(
                appContext,
                systemReceiver,
                IntentFilter().apply {
                    addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
                    addAction(Intent.ACTION_DEVICE_STORAGE_LOW)
                    addAction(Intent.ACTION_DEVICE_STORAGE_OK)
                },
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            thermalListener?.let { listener ->
                runCatching {
                    powerManager.addThermalStatusListener(appContext.mainExecutor, listener)
                }
            }
        }
        appContext.registerComponentCallbacks(memoryCallbacks)
        scheduleUpdate()
    }

    private fun scheduleUpdate() {
        scope.launch(ioDispatcher) {
            update()
        }
    }

    private fun update() {
        val activeNetwork = connectivityManager.activeNetwork
        if (activeNetwork != observedNetwork) {
            observedNetwork = activeNetwork
            networkGeneration += 1L
        }
        val currentCapabilities = activeNetwork
            ?.let(connectivityManager::getNetworkCapabilities)
        val isMemoryLow = runCatching {
            ActivityManager.MemoryInfo().also(activityManager::getMemoryInfo).lowMemory
        }.getOrDefault(true)
        mutableSignals.value = DevicePreloadSignals(
            network = currentCapabilities.toTransport(),
            isMetered = currentCapabilities?.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_NOT_METERED,
            ) != true,
            isPowerSaveMode = powerManager.isPowerSaveMode,
            isStorageLow = runCatching {
                StatFs(appContext.cacheDir.absolutePath).availableBytes < MIN_FREE_BYTES
            }.getOrDefault(true),
            isMemoryLow = isMemoryLow,
            thermalState = currentThermalState(),
            networkGeneration = networkGeneration,
        )
    }

    private fun NetworkCapabilities?.toTransport(): NetworkTransport {
        if (this == null || !hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
            return NetworkTransport.OFFLINE
        }
        return when {
            hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkTransport.WIFI
            hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkTransport.MOBILE
            else -> NetworkTransport.OTHER
        }
    }

    private fun currentThermalState(): DeviceThermalState {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return DeviceThermalState.UNKNOWN
        return when (powerManager.currentThermalStatus) {
            PowerManager.THERMAL_STATUS_NONE -> DeviceThermalState.NONE
            PowerManager.THERMAL_STATUS_LIGHT -> DeviceThermalState.LIGHT
            PowerManager.THERMAL_STATUS_MODERATE -> DeviceThermalState.MODERATE
            PowerManager.THERMAL_STATUS_SEVERE -> DeviceThermalState.SEVERE
            PowerManager.THERMAL_STATUS_CRITICAL -> DeviceThermalState.CRITICAL
            PowerManager.THERMAL_STATUS_EMERGENCY -> DeviceThermalState.EMERGENCY
            PowerManager.THERMAL_STATUS_SHUTDOWN -> DeviceThermalState.SHUTDOWN
            else -> DeviceThermalState.UNKNOWN
        }
    }

    private companion object {
        const val MIN_FREE_BYTES = 256L * 1024L * 1024L
    }
}
