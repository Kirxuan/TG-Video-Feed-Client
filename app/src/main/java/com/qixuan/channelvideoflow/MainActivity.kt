package com.qixuan.channelvideoflow

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.media3.common.util.UnstableApi
import com.qixuan.channelvideoflow.navigation.ChannelVideoFlowNavHost
import com.qixuan.channelvideoflow.ui.theme.ChannelVideoFlowTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
@UnstableApi
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ChannelVideoFlowTheme {
                ChannelVideoFlowNavHost()
            }
        }
    }
}
