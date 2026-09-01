package com.qixuan.channelvideoflow.onboarding

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DataStoreVideoFeedOnboardingPreferencesTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun newInstallDefaultsToSwipeHintNotSeen() = runTest {
        val preferences = preferences(fileName = "default.preferences_pb")

        assertFalse(preferences.hasSeenSwipeHint.first())
    }

    @Test
    fun markingSeenPersistsForANewPreferencesInstance() = runTest {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { File(temporaryFolder.root, "persisted.preferences_pb") },
        )
        val firstInstance = DataStoreVideoFeedOnboardingPreferences(dataStore)

        firstInstance.markSwipeHintSeen()

        val newInstance = DataStoreVideoFeedOnboardingPreferences(dataStore)
        assertTrue(newInstance.hasSeenSwipeHint.first())
    }

    @Test
    fun markingOnboardingSeenDoesNotPolluteMediaCacheSettings() = runTest {
        val mediaCacheFile = File(temporaryFolder.root, "media_cache_settings.preferences_pb")
        val mediaCacheDataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { mediaCacheFile },
        )
        val onboardingPreferences = preferences(fileName = "video_feed_onboarding.preferences_pb")
        mediaCacheDataStore.edit { values ->
            values[booleanPreferencesKey("existing_media_setting")] = true
        }
        val mediaCacheBytesBefore = mediaCacheFile.readBytes()

        onboardingPreferences.markSwipeHintSeen()

        assertNotEquals("media_cache_settings", VIDEO_FEED_ONBOARDING_DATASTORE_NAME)
        assertArrayEquals(mediaCacheBytesBefore, mediaCacheFile.readBytes())
        assertTrue(onboardingPreferences.hasSeenSwipeHint.first())
    }

    private fun TestScope.preferences(fileName: String): DataStoreVideoFeedOnboardingPreferences =
        DataStoreVideoFeedOnboardingPreferences(
            PreferenceDataStoreFactory.create(
                scope = backgroundScope,
                produceFile = { File(temporaryFolder.root, fileName) },
            ),
        )
}
