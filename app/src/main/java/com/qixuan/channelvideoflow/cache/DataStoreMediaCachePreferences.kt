package com.qixuan.channelvideoflow.cache

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.qixuan.channelvideoflow.domain.cache.MediaCacheLimits
import com.qixuan.channelvideoflow.domain.cache.MediaCachePreferences
import com.qixuan.channelvideoflow.domain.cache.MediaCachePreferencesState
import com.qixuan.channelvideoflow.model.video.VideoQualityPreference
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.mediaCacheDataStore by preferencesDataStore(name = "media_cache_settings")

@Singleton
class DataStoreMediaCachePreferences @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : MediaCachePreferences {
    override val preferences: Flow<MediaCachePreferencesState> =
        context.mediaCacheDataStore.data
            .catch { failure ->
                if (failure is IOException) emit(emptyPreferences()) else throw failure
            }
            .map { values ->
                val savedLimit = values[CACHE_LIMIT_BYTES]
                    ?.takeIf { it in MediaCacheLimits.allowedBytes }
                    ?: MediaCacheLimits.DEFAULT_BYTES
                MediaCachePreferencesState(
                    limitBytes = savedLimit,
                    mobileDataPreloadEnabled = values[MOBILE_PRELOAD_ENABLED] ?: false,
                    videoQualityPreference = values[VIDEO_QUALITY]
                        ?.let(::parseVideoQuality)
                        ?: VideoQualityPreference.AUTO,
                )
            }

    override suspend fun setLimitBytes(bytes: Long) {
        context.mediaCacheDataStore.edit { values ->
            values[CACHE_LIMIT_BYTES] = MediaCacheLimits.requireAllowed(bytes)
        }
    }

    override suspend fun setMobileDataPreloadEnabled(enabled: Boolean) {
        context.mediaCacheDataStore.edit { values ->
            values[MOBILE_PRELOAD_ENABLED] = enabled
        }
    }

    override suspend fun setVideoQualityPreference(preference: VideoQualityPreference) {
        context.mediaCacheDataStore.edit { values ->
            values[VIDEO_QUALITY] = preference.name
        }
    }

    private fun parseVideoQuality(saved: String): VideoQualityPreference? =
        VideoQualityPreference.entries.firstOrNull { preference -> preference.name == saved }

    private companion object {
        val CACHE_LIMIT_BYTES = longPreferencesKey("media_cache_limit_bytes")
        val MOBILE_PRELOAD_ENABLED = booleanPreferencesKey("mobile_preload_enabled")
        val VIDEO_QUALITY = stringPreferencesKey("video_quality")
    }
}
