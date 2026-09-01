package com.qixuan.channelvideoflow.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.qixuan.channelvideoflow.domain.cache.MediaCacheController
import com.qixuan.channelvideoflow.domain.cache.MediaCacheState
import com.qixuan.channelvideoflow.domain.media.VideoPreloadController
import com.qixuan.channelvideoflow.model.video.VideoQualityPreference
import com.qixuan.channelvideoflow.player.VideoPlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
@UnstableApi
class CacheSettingsViewModel @Inject constructor(
    private val cacheController: MediaCacheController,
    private val preloadController: VideoPreloadController,
    private val playerController: VideoPlaybackController,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(CacheSettingsUiState())
    val uiState: StateFlow<CacheSettingsUiState> = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch {
            cacheController.state.collect { cache ->
                mutableUiState.value = CacheSettingsUiState(cache)
            }
        }
        viewModelScope.launch { cacheController.refresh() }
    }

    fun setLimit(bytes: Long) {
        viewModelScope.launch { cacheController.setLimitBytes(bytes) }
    }

    fun setMobilePreloadEnabled(enabled: Boolean) {
        viewModelScope.launch { cacheController.setMobileDataPreloadEnabled(enabled) }
    }

    fun setVideoQuality(preference: VideoQualityPreference) {
        viewModelScope.launch { cacheController.setVideoQualityPreference(preference) }
    }

    fun refresh() {
        viewModelScope.launch { cacheController.refresh() }
    }

    fun clearCache() {
        preloadController.stop()
        playerController.releaseBinding()
        viewModelScope.launch { cacheController.clearMediaCache() }
    }
}

data class CacheSettingsUiState(
    val cache: MediaCacheState = MediaCacheState(),
)
