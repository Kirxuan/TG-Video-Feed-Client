package com.qixuan.channelvideoflow.telegram.client

import com.qixuan.channelvideoflow.telegram.logging.AuthEventLogger
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Test

class OfficialTdLibBridgeTest {
    @Test
    fun configuresZeroNativeVerbosityBeforeRegisteringTheSanitizedHandler() {
        val runtime = RecordingTdLibRuntime()
        val logger = RecordingLogger()

        OfficialTdLibBridge(runtime).configureLogHandler(logger)

        assertEquals(listOf("verbosity=0", "handler=0"), runtime.calls)
        runtime.handler?.invoke(0)
        assertEquals(listOf(0), logger.nativeLevels)
    }

    private class RecordingTdLibRuntime : TdLibRuntime {
        val calls = mutableListOf<String>()
        var handler: ((Int) -> Unit)? = null

        override fun load() = Unit

        override fun setLogVerbosityLevel(level: Int) {
            calls += "verbosity=$level"
        }

        override fun setLogMessageHandler(
            maxVerbosityLevel: Int,
            handler: (Int) -> Unit,
        ) {
            calls += "handler=$maxVerbosityLevel"
            this.handler = handler
        }

        override fun create(
            onUpdate: (TdApi.Object) -> Unit,
            onException: () -> Unit,
        ): TdLibSession = error("not used")
    }

    private class RecordingLogger : AuthEventLogger {
        val nativeLevels = mutableListOf<Int>()

        override fun state(name: String) = Unit
        override fun request(name: String) = Unit
        override fun failure(category: String, code: Int) = Unit

        override fun nativeLevel(level: Int) {
            nativeLevels += level
        }
    }
}
