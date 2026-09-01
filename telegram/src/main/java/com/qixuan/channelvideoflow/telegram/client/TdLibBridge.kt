package com.qixuan.channelvideoflow.telegram.client

import com.qixuan.channelvideoflow.telegram.logging.AuthEventLogger
import org.drinkless.tdlib.TdApi

internal interface TdLibSession {
    fun send(function: TdApi.Function<*>, result: (TdApi.Object) -> Unit)
}

internal interface TdLibBridge {
    fun load()
    fun configureLogHandler(logger: AuthEventLogger)
    fun create(onUpdate: (TdApi.Object) -> Unit, onException: () -> Unit): TdLibSession
}
