package com.qixuan.channelvideoflow.telegram.logging

import android.util.Log
import com.qixuan.channelvideoflow.telegram.BuildConfig

interface AuthEventLogger {
    fun state(name: String)
    fun request(name: String)
    fun failure(category: String, code: Int)
    fun nativeLevel(level: Int)
}

internal class AndroidAuthEventLogger : AuthEventLogger {
    override fun state(name: String) = debug("state=$name")
    override fun request(name: String) = debug("request=$name")
    override fun failure(category: String, code: Int) = debug("failure=$category code=$code")
    override fun nativeLevel(level: Int) = debug("native_level=$level")

    private fun debug(message: String) {
        if (BuildConfig.DEBUG) Log.i(TAG, message)
    }

    private companion object {
        const val TAG = "CVF/Auth"
    }
}
