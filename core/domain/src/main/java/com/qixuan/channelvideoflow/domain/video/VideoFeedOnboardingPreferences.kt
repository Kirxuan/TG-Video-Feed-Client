package com.qixuan.channelvideoflow.domain.video

import kotlinx.coroutines.flow.Flow

interface VideoFeedOnboardingPreferences {
    val hasSeenSwipeHint: Flow<Boolean>

    suspend fun markSwipeHintSeen()
}
