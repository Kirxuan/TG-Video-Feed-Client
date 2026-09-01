package com.qixuan.channelvideoflow.telegram.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class TelegramApplicationScope

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class TelegramIoDispatcher

@Module
@InstallIn(SingletonComponent::class)
object TelegramCoroutineModule {
    @Provides
    @Singleton
    @TelegramApplicationScope
    fun provideTelegramApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    @TelegramIoDispatcher
    fun provideTelegramIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
}
