package com.qixuan.channelvideoflow.onboarding

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import com.qixuan.channelvideoflow.domain.video.VideoFeedOnboardingPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

internal const val VIDEO_FEED_ONBOARDING_DATASTORE_NAME = "video_feed_onboarding"

private val Context.videoFeedOnboardingDataStore by preferencesDataStore(
    name = VIDEO_FEED_ONBOARDING_DATASTORE_NAME,
)

@Singleton
class DataStoreVideoFeedOnboardingPreferences internal constructor(
    private val dataStore: DataStore<Preferences>,
) : VideoFeedOnboardingPreferences {
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : this(context.videoFeedOnboardingDataStore)

    override val hasSeenSwipeHint: Flow<Boolean> = dataStore.data
        .catch { failure ->
            if (failure is IOException) emit(emptyPreferences()) else throw failure
        }
        .map { values -> values[SWIPE_HINT_SEEN] ?: false }

    override suspend fun markSwipeHintSeen() {
        dataStore.edit { values -> values[SWIPE_HINT_SEEN] = true }
    }

    private companion object {
        val SWIPE_HINT_SEEN = booleanPreferencesKey("swipe_hint_seen")
    }
}
