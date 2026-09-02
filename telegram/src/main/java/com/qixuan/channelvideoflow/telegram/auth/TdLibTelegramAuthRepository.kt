package com.qixuan.channelvideoflow.telegram.auth

import com.qixuan.channelvideoflow.domain.auth.TelegramAuthRepository
import com.qixuan.channelvideoflow.model.auth.TelegramAuthFailure
import com.qixuan.channelvideoflow.model.auth.TelegramAuthState
import com.qixuan.channelvideoflow.telegram.client.FatalCategory
import com.qixuan.channelvideoflow.telegram.client.TelegramAuthClient
import com.qixuan.channelvideoflow.telegram.client.TelegramAuthRequest
import com.qixuan.channelvideoflow.telegram.client.TelegramClientAuthorizationState
import com.qixuan.channelvideoflow.telegram.client.TelegramClientEvent
import com.qixuan.channelvideoflow.telegram.config.TelegramCredentialsResult
import com.qixuan.channelvideoflow.telegram.config.TelegramCredentialsStore
import com.qixuan.channelvideoflow.telegram.config.TelegramCredentialsUnavailableReason
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

internal class TdLibTelegramAuthRepository(
    private val client: TelegramAuthClient,
    private val credentialsStore: TelegramCredentialsStore,
    private val scope: CoroutineScope,
) : TelegramAuthRepository {
    private val mutableAuthState =
        MutableStateFlow<TelegramAuthState>(TelegramAuthState.Initializing)
    override val authState: StateFlow<TelegramAuthState> = mutableAuthState.asStateFlow()

    private val started = AtomicBoolean(false)
    private val logoutRequested = AtomicBoolean(false)
    private var collectionJob: Job? = null

    override suspend fun start() {
        if (!started.compareAndSet(false, true)) return

        installEventCollector()
        try {
            client.start()
        } catch (throwable: Throwable) {
            started.set(false)
            throw throwable
        }
    }

    override suspend fun configureCredentials(apiId: String, apiHash: String) {
        when (val result = credentialsStore.save(apiId, apiHash)) {
            is TelegramCredentialsResult.Available -> {
                mutableAuthState.value = TelegramAuthState.Initializing
                client.restartAfterCredentialsChanged()
            }
            is TelegramCredentialsResult.Unavailable -> {
                mutableAuthState.value = TelegramAuthState.UnconfiguredCredentials(
                    invalidKeys = result.invalidKeys.toSet(),
                    failure = result.reason.toStorageFailureOrNull(),
                )
            }
        }
    }

    override suspend fun submitPhoneNumber(phoneNumber: String) {
        client.submitPhoneNumber(phoneNumber)
    }

    override suspend fun submitCode(code: String) {
        client.submitCode(code)
    }

    override suspend fun resendCode() {
        client.resendCode()
    }

    override suspend fun submitPassword(password: String) {
        client.submitPassword(password)
    }

    override suspend fun logout() {
        logoutRequested.set(true)
        try {
            client.logout()
        } catch (throwable: Throwable) {
            logoutRequested.set(false)
            throw throwable
        }
    }

    private fun installEventCollector() {
        if (collectionJob?.isActive == true) return
        collectionJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            client.events.collect(::handleEvent)
        }
    }

    private suspend fun handleEvent(event: TelegramClientEvent) {
        when (event) {
            is TelegramClientEvent.CredentialsUnavailable -> {
                mutableAuthState.value = TelegramAuthState.UnconfiguredCredentials(
                    invalidKeys = event.invalidKeys.toSet(),
                    failure = event.reason.toStorageFailureOrNull(),
                )
            }
            is TelegramClientEvent.AuthorizationStateChanged -> {
                handleAuthorizationState(event.state)
            }
            is TelegramClientEvent.RequestFailed -> handleRequestFailure(event)
            is TelegramClientEvent.FatalFailure -> {
                mutableAuthState.value = TelegramAuthState.FatalError(
                    failure = event.category.toFailure(),
                )
            }
        }
    }

    private suspend fun handleAuthorizationState(state: TelegramClientAuthorizationState) {
        when (state) {
            TelegramClientAuthorizationState.WaitTdlibParameters -> {
                mutableAuthState.value = TelegramAuthState.Initializing
            }
            TelegramClientAuthorizationState.WaitPhoneNumber -> {
                mutableAuthState.value = TelegramAuthState.WaitingPhoneNumber()
            }
            is TelegramClientAuthorizationState.WaitCode -> {
                mutableAuthState.value = TelegramAuthState.WaitingCode(codeInfo = state.codeInfo)
            }
            TelegramClientAuthorizationState.WaitPassword -> {
                mutableAuthState.value = TelegramAuthState.WaitingPassword()
            }
            TelegramClientAuthorizationState.Ready -> {
                logoutRequested.set(false)
                mutableAuthState.value = TelegramAuthState.Authorized()
            }
            TelegramClientAuthorizationState.LoggingOut -> {
                mutableAuthState.value = TelegramAuthState.LoggingOut
            }
            TelegramClientAuthorizationState.Closing -> {
                mutableAuthState.value = TelegramAuthState.Closing
            }
            TelegramClientAuthorizationState.Closed -> {
                mutableAuthState.value = TelegramAuthState.Closed
                started.set(false)
                if (logoutRequested.getAndSet(false)) {
                    start()
                }
            }
            is TelegramClientAuthorizationState.Unsupported -> {
                mutableAuthState.value = TelegramAuthState.Unsupported(state.step)
            }
        }
    }

    private fun handleRequestFailure(event: TelegramClientEvent.RequestFailed) {
        val failure = TdLibAuthErrorMapper.map(
            request = event.request,
            code = event.code,
            rawMessage = event.rawMessage,
        )
        val currentState = mutableAuthState.value

        mutableAuthState.value = when (event.request) {
            TelegramAuthRequest.PHONE_NUMBER -> when (currentState) {
                is TelegramAuthState.WaitingPhoneNumber -> currentState.copy(failure = failure)
                else -> currentState
            }
            TelegramAuthRequest.CODE -> when (currentState) {
                is TelegramAuthState.WaitingCode -> currentState.copy(failure = failure)
                else -> currentState
            }
            TelegramAuthRequest.RESEND_CODE -> when (currentState) {
                is TelegramAuthState.WaitingCode -> currentState.copy(failure = failure)
                else -> currentState
            }
            TelegramAuthRequest.PASSWORD -> when (currentState) {
                is TelegramAuthState.WaitingPassword -> currentState.copy(failure = failure)
                else -> currentState
            }
            TelegramAuthRequest.LOG_OUT -> {
                logoutRequested.set(false)
                TelegramAuthState.Authorized(failure)
            }
            TelegramAuthRequest.PARAMETERS -> TelegramAuthState.UnconfiguredCredentials(
                invalidKeys = setOf("TELEGRAM_API_ID", "TELEGRAM_API_HASH"),
                failure = TelegramAuthFailure.InvalidApiCredentials,
            )
            TelegramAuthRequest.CLOSE -> currentState
        }
    }

    private fun FatalCategory.toFailure(): TelegramAuthFailure = when (this) {
        FatalCategory.NATIVE_LIBRARY -> TelegramAuthFailure.NativeLibraryLoadFailed
        FatalCategory.INITIALIZATION -> TelegramAuthFailure.TdLibInitializationFailed
        FatalCategory.DATABASE -> TelegramAuthFailure.DatabaseFailed
    }

    private fun TelegramCredentialsUnavailableReason.toStorageFailureOrNull():
        TelegramAuthFailure? = when (this) {
        TelegramCredentialsUnavailableReason.MISSING_OR_INVALID -> null
        TelegramCredentialsUnavailableReason.SECURE_STORAGE ->
            TelegramAuthFailure.CredentialStorageFailed
    }
}
