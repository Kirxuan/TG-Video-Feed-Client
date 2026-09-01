package com.qixuan.channelvideoflow.telegram.client

import com.qixuan.channelvideoflow.telegram.logging.AuthEventLogger
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi

internal class OfficialTdLibBridge(
    private val runtime: TdLibRuntime = NativeTdLibRuntime,
) : TdLibBridge {
    override fun load() = runtime.load()

    override fun configureLogHandler(logger: AuthEventLogger) {
        runtime.setLogVerbosityLevel(0)
        runtime.setLogMessageHandler(0, logger::nativeLevel)
    }

    override fun create(
        onUpdate: (TdApi.Object) -> Unit,
        onException: () -> Unit,
    ): TdLibSession {
        return runtime.create(onUpdate, onException)
    }
}

internal interface TdLibRuntime {
    fun load()
    fun setLogVerbosityLevel(level: Int)
    fun setLogMessageHandler(maxVerbosityLevel: Int, handler: (Int) -> Unit)

    fun create(
        onUpdate: (TdApi.Object) -> Unit,
        onException: () -> Unit,
    ): TdLibSession
}

private object NativeTdLibRuntime : TdLibRuntime {
    override fun load() {
        System.loadLibrary("tdjni")
    }

    override fun setLogVerbosityLevel(level: Int) {
        Client.execute(TdApi.SetLogVerbosityLevel(level))
    }

    override fun setLogMessageHandler(
        maxVerbosityLevel: Int,
        handler: (Int) -> Unit,
    ) {
        Client.setLogMessageHandler(maxVerbosityLevel) { level, _ -> handler(level) }
    }

    override fun create(
        onUpdate: (TdApi.Object) -> Unit,
        onException: () -> Unit,
    ): TdLibSession {
        val client = Client.create(
            { update -> onUpdate(update) },
            { _ -> onException() },
            { _ -> onException() },
        )
        return object : TdLibSession {
            override fun send(function: TdApi.Function<*>, result: (TdApi.Object) -> Unit) {
                client.send(function) { response -> result(response) }
            }
        }
    }
}
