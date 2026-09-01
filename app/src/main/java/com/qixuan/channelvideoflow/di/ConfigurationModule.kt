package com.qixuan.channelvideoflow.di

import com.qixuan.channelvideoflow.config.BuildConfigTelegramCredentialStatusProvider
import com.qixuan.channelvideoflow.config.TelegramCredentialStatusProvider
import com.qixuan.channelvideoflow.cache.AndroidDevicePreloadPolicySource
import com.qixuan.channelvideoflow.cache.DataStoreMediaCachePreferences
import com.qixuan.channelvideoflow.domain.cache.MediaCachePreferences
import com.qixuan.channelvideoflow.domain.media.DevicePreloadPolicySource
import com.qixuan.channelvideoflow.domain.video.VideoFeedOnboardingPreferences
import com.qixuan.channelvideoflow.onboarding.DataStoreVideoFeedOnboardingPreferences
import com.qixuan.channelvideoflow.telegram.config.TelegramCredentialsProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class ConfigurationModule {
    @Binds
    abstract fun bindTelegramCredentialStatusProvider(
        implementation: BuildConfigTelegramCredentialStatusProvider,
    ): TelegramCredentialStatusProvider

    @Binds
    abstract fun bindTelegramCredentialsProvider(
        implementation: BuildConfigTelegramCredentialStatusProvider,
    ): TelegramCredentialsProvider

    @Binds
    abstract fun bindMediaCachePreferences(
        implementation: DataStoreMediaCachePreferences,
    ): MediaCachePreferences

    @Binds
    abstract fun bindVideoFeedOnboardingPreferences(
        implementation: DataStoreVideoFeedOnboardingPreferences,
    ): VideoFeedOnboardingPreferences

    @Binds
    abstract fun bindDevicePreloadPolicySource(
        implementation: AndroidDevicePreloadPolicySource,
    ): DevicePreloadPolicySource
}
