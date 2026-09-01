package com.qixuan.channelvideoflow.telegram.client

import com.qixuan.channelvideoflow.telegram.logging.AuthEventLogger
import org.drinkless.tdlib.TdApi

internal class FakeTdLibBridge : TdLibBridge {
    var loadCalls = 0
        private set
    var createCalls = 0
        private set
    val sentFunctions: List<TdApi.Function<*>>
        get() = sessions.flatMap { it.sentFunctions }
    private val sessions = mutableListOf<RecordedSession>()

    override fun load() {
        loadCalls += 1
    }

    override fun configureLogHandler(logger: AuthEventLogger) = Unit

    override fun create(
        onUpdate: (TdApi.Object) -> Unit,
        onException: () -> Unit,
    ): TdLibSession {
        createCalls += 1
        return RecordedSession(onUpdate, onException).also(sessions::add)
    }

    fun session(index: Int): RecordedSession = sessions[index]

    fun emitUpdate(sessionIndex: Int, update: TdApi.Object) {
        session(sessionIndex).emitUpdate(update)
    }

    fun complete(sessionIndex: Int, function: TdApi.Function<*>, result: TdApi.Object) {
        session(sessionIndex).complete(function, result)
    }

    fun failCallback(sessionIndex: Int) {
        session(sessionIndex).failCallback()
    }

    internal class RecordedSession(
        private val updateCallback: (TdApi.Object) -> Unit,
        private val exceptionCallback: () -> Unit,
    ) : TdLibSession {
        val sentFunctions = mutableListOf<TdApi.Function<*>>()
        private val callbacks = mutableMapOf<TdApi.Function<*>, (TdApi.Object) -> Unit>()

        override fun send(function: TdApi.Function<*>, result: (TdApi.Object) -> Unit) {
            sentFunctions += function
            callbacks[function] = result
        }

        fun emitUpdate(update: TdApi.Object) {
            updateCallback(update)
        }

        fun complete(function: TdApi.Function<*>, result: TdApi.Object) {
            checkNotNull(callbacks[function]).invoke(result)
        }

        fun failCallback() {
            exceptionCallback()
        }
    }
}
