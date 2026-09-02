package com.qixuan.channelvideoflow.telegram.di

import android.content.Context
import com.qixuan.channelvideoflow.database.ChannelDao
import com.qixuan.channelvideoflow.database.VideoIndexDao
import com.qixuan.channelvideoflow.database.MediaCacheEntryDao
import com.qixuan.channelvideoflow.domain.auth.TelegramAuthRepository
import com.qixuan.channelvideoflow.domain.cache.MediaCacheController
import com.qixuan.channelvideoflow.domain.channel.TelegramChatRepository
import com.qixuan.channelvideoflow.domain.message.TelegramMessageRepository
import com.qixuan.channelvideoflow.telegram.auth.TdLibTelegramAuthRepository
import com.qixuan.channelvideoflow.telegram.chat.TdLibTelegramChatRepository
import com.qixuan.channelvideoflow.telegram.client.OfficialTdLibBridge
import com.qixuan.channelvideoflow.telegram.client.TdLibBridge
import com.qixuan.channelvideoflow.telegram.client.TelegramAuthClient
import com.qixuan.channelvideoflow.telegram.client.TelegramChatClient
import com.qixuan.channelvideoflow.telegram.client.TelegramMessageClient
import com.qixuan.channelvideoflow.telegram.client.TelegramFileClient
import com.qixuan.channelvideoflow.domain.media.TelegramFileGateway
import com.qixuan.channelvideoflow.domain.media.StreamingNetworkMetricsEstimator
import com.qixuan.channelvideoflow.domain.media.StreamingNetworkMetricsRepository
import com.qixuan.channelvideoflow.telegram.media.TelegramFileManager
import com.qixuan.channelvideoflow.telegram.media.TdLibMediaCacheManager
import com.qixuan.channelvideoflow.telegram.di.TelegramApplicationScope
import com.qixuan.channelvideoflow.telegram.client.TelegramClientManager
import com.qixuan.channelvideoflow.telegram.config.TelegramCredentialsProvider
import com.qixuan.channelvideoflow.telegram.config.TelegramCredentialsStore
import com.qixuan.channelvideoflow.telegram.logging.AndroidAuthEventLogger
import com.qixuan.channelvideoflow.telegram.logging.AuthEventLogger
import com.qixuan.channelvideoflow.telegram.message.RoomMessageIndexStore
import com.qixuan.channelvideoflow.telegram.message.TdLibTelegramMessageRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope

@Module
@InstallIn(SingletonComponent::class)
internal object TelegramModule {
    @Provides
    @Singleton
    internal fun provideTelegramClientManager(
        @ApplicationContext context: Context,
        credentialsProvider: TelegramCredentialsProvider,
        bridge: TdLibBridge,
        logger: AuthEventLogger,
    ): TelegramClientManager = TelegramClientManager(
        context = context,
        credentialsProvider = credentialsProvider,
        bridge = bridge,
        logger = logger,
    )

    @Provides
    internal fun provideTelegramAuthClient(
        manager: TelegramClientManager,
    ): TelegramAuthClient = manager

    @Provides
    internal fun provideTelegramChatClient(
        manager: TelegramClientManager,
    ): TelegramChatClient = manager

    @Provides
    internal fun provideTelegramMessageClient(
        manager: TelegramClientManager,
    ): TelegramMessageClient = manager

    @Provides
    internal fun provideTelegramFileClient(
        manager: TelegramClientManager,
    ): TelegramFileClient = manager

    @Provides
    @Singleton
    internal fun provideStreamingNetworkMetricsEstimator(): StreamingNetworkMetricsEstimator =
        StreamingNetworkMetricsEstimator()

    @Provides
    @Singleton
    internal fun provideStreamingNetworkMetricsRepository(
        estimator: StreamingNetworkMetricsEstimator,
    ): StreamingNetworkMetricsRepository = estimator

    @Provides
    @Singleton
    internal fun provideTelegramFileManager(
        client: TelegramFileClient,
        @TelegramApplicationScope scope: CoroutineScope,
        cacheEntryDao: MediaCacheEntryDao,
        networkMetrics: StreamingNetworkMetricsRepository,
    ): TelegramFileManager = TelegramFileManager(
        client = client,
        scope = scope,
        cacheEntryDao = cacheEntryDao,
        networkMetrics = networkMetrics,
    )

    @Provides
    internal fun provideTelegramFileGateway(
        manager: TelegramFileManager,
    ): TelegramFileGateway = manager

    @Provides
    @Singleton
    internal fun provideMediaCacheController(
        manager: TdLibMediaCacheManager,
    ): MediaCacheController = manager

    @Provides
    @Singleton
    internal fun provideTelegramAuthRepository(
        client: TelegramAuthClient,
        credentialsStore: TelegramCredentialsStore,
        @TelegramApplicationScope scope: CoroutineScope,
    ): TelegramAuthRepository = TdLibTelegramAuthRepository(client, credentialsStore, scope)

    @Provides
    @Singleton
    internal fun provideTelegramChatRepository(
        client: TelegramChatClient,
        channelDao: ChannelDao,
        @TelegramApplicationScope scope: CoroutineScope,
    ): TelegramChatRepository = TdLibTelegramChatRepository(client, channelDao, scope)

    @Provides
    @Singleton
    internal fun provideTelegramMessageRepository(
        client: TelegramMessageClient,
        videoIndexDao: VideoIndexDao,
        @TelegramApplicationScope scope: CoroutineScope,
    ): TelegramMessageRepository = TdLibTelegramMessageRepository(
        client = client,
        store = RoomMessageIndexStore(videoIndexDao),
        scope = scope,
    )

    @Provides
    @Singleton
    internal fun provideTdLibBridge(): TdLibBridge = OfficialTdLibBridge()

    @Provides
    @Singleton
    internal fun provideAuthEventLogger(): AuthEventLogger = AndroidAuthEventLogger()
}
