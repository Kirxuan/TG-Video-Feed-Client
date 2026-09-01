package com.qixuan.channelvideoflow.cache

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qixuan.channelvideoflow.model.video.VideoQualityPreference
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DataStoreMediaCachePreferencesTest {
    @Test
    fun videoQualityPreferenceRoundTripsWithoutSensitiveData() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = DataStoreMediaCachePreferences(context)

        try {
            preferences.setVideoQualityPreference(VideoQualityPreference.DATA_SAVER)

            assertEquals(
                VideoQualityPreference.DATA_SAVER,
                preferences.preferences.first().videoQualityPreference,
            )
        } finally {
            preferences.setVideoQualityPreference(VideoQualityPreference.AUTO)
        }
    }
}
