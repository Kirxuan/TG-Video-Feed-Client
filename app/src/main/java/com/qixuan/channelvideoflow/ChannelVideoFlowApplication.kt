package com.qixuan.channelvideoflow

import android.app.Application
import com.qixuan.channelvideoflow.domain.cache.MediaCacheController
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class ChannelVideoFlowApplication : Application() {
    @Inject
    lateinit var mediaCacheController: MediaCacheController

    override fun onCreate() {
        super.onCreate()
        mediaCacheController.start()
    }
}
