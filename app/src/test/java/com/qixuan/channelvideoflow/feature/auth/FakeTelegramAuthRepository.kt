package com.qixuan.channelvideoflow.feature.auth

import com.qixuan.channelvideoflow.domain.auth.TelegramAuthRepository
import com.qixuan.channelvideoflow.model.auth.TelegramAuthState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class FakeTelegramAuthRepository(
    initialState: TelegramAuthState = TelegramAuthState.Initializing,
) : TelegramAuthRepository {
    private val mutableAuthState = MutableStateFlow(initialState)
    override val authState: StateFlow<TelegramAuthState> = mutableAuthState.asStateFlow()

    var startCalls = 0
        private set
    var logoutCalls = 0
        private set
    var resendCodeCalls = 0
        private set
    val submittedPhoneNumbers = mutableListOf<String>()
    val submittedCodes = mutableListOf<String>()
    val submittedPasswords = mutableListOf<String>()

    var startGate: CompletableDeferred<Unit>? = null
    var phoneGate: CompletableDeferred<Unit>? = null
    var codeGate: CompletableDeferred<Unit>? = null
    var passwordGate: CompletableDeferred<Unit>? = null
    var logoutGate: CompletableDeferred<Unit>? = null
    var codeStarted: (() -> Unit)? = null
    var passwordStarted: (() -> Unit)? = null
    var phoneFailure: Throwable? = null
    var startFailureBeforeGate: Throwable? = null
    var startFailureAfterGate: Throwable? = null
    var phoneFailureAfterGate: Throwable? = null
    var logoutFailureAfterGate: Throwable? = null

    fun emit(state: TelegramAuthState) {
        mutableAuthState.value = state
    }

    override suspend fun start() {
        startCalls += 1
        startFailureBeforeGate?.let { throw it }
        startGate?.await()
        startFailureAfterGate?.let { throw it }
    }

    override suspend fun submitPhoneNumber(phoneNumber: String) {
        submittedPhoneNumbers += phoneNumber
        phoneFailure?.let { throw it }
        phoneGate?.await()
        phoneFailureAfterGate?.let { throw it }
    }

    override suspend fun submitCode(code: String) {
        submittedCodes += code
        codeStarted?.invoke()
        codeGate?.await()
    }

    override suspend fun resendCode() {
        resendCodeCalls += 1
    }

    override suspend fun submitPassword(password: String) {
        submittedPasswords += password
        passwordStarted?.invoke()
        passwordGate?.await()
    }

    override suspend fun logout() {
        logoutCalls += 1
        logoutGate?.await()
        logoutFailureAfterGate?.let { throw it }
    }
}
