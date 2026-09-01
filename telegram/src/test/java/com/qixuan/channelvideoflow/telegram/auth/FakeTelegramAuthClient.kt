package com.qixuan.channelvideoflow.telegram.auth

import com.qixuan.channelvideoflow.telegram.client.TelegramAuthClient
import com.qixuan.channelvideoflow.telegram.client.TelegramClientAuthorizationState
import com.qixuan.channelvideoflow.telegram.client.TelegramClientEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

internal class FakeTelegramAuthClient : TelegramAuthClient {
    private val mutableEvents = MutableSharedFlow<TelegramClientEvent>(
        extraBufferCapacity = 16,
    )

    override val events: Flow<TelegramClientEvent> = mutableEvents

    var eventToEmitOnStart: TelegramClientEvent? = null
    var startCalls = 0
        private set
    var logoutCalls = 0
        private set
    var resendCodeCalls = 0
        private set
    val subscriberCountsAtStart = mutableListOf<Int>()
    val submittedPhoneNumbers = mutableListOf<String>()
    val submittedCodes = mutableListOf<String>()
    val submittedPasswords = mutableListOf<String>()

    override suspend fun start() {
        startCalls += 1
        subscriberCountsAtStart += mutableEvents.subscriptionCount.value
        eventToEmitOnStart?.let { mutableEvents.emit(it) }
    }

    override suspend fun submitPhoneNumber(phoneNumber: String) {
        submittedPhoneNumbers += phoneNumber
    }

    override suspend fun submitCode(code: String) {
        submittedCodes += code
    }

    override suspend fun resendCode() {
        resendCodeCalls += 1
    }

    override suspend fun submitPassword(password: String) {
        submittedPasswords += password
    }

    override suspend fun logout() {
        logoutCalls += 1
    }

    suspend fun emit(event: TelegramClientEvent) {
        mutableEvents.emit(event)
    }

    suspend fun emitAuthorizationState(state: TelegramClientAuthorizationState) {
        emit(TelegramClientEvent.AuthorizationStateChanged(state))
    }
}
