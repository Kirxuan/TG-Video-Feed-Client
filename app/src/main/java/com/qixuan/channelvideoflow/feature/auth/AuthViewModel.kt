package com.qixuan.channelvideoflow.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qixuan.channelvideoflow.domain.auth.TelegramAuthRepository
import com.qixuan.channelvideoflow.model.auth.TelegramAuthFailure
import com.qixuan.channelvideoflow.model.auth.TelegramAuthState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repository: TelegramAuthRepository,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(LoginUiState(LoginStep.INITIALIZING))
    val uiState: StateFlow<LoginUiState> = mutableUiState.asStateFlow()

    private var floodWaitJob: Job? = null
    private var resendCountdownJob: Job? = null
    private var startJob: Job? = null
    private var stateEpoch = 0L

    init {
        viewModelScope.launch {
            repository.authState.collect(::applyAuthState)
        }
        startRepositoryIfIdle()
    }

    fun onInputChanged(value: String) {
        if (mutableUiState.value.step in editableSteps) {
            mutableUiState.update { it.copy(input = value) }
        }
    }

    fun submit() {
        val state = mutableUiState.value
        if (!state.canSubmit) return

        val submittedInput = state.input
        val actionEpoch = stateEpoch
        mutableUiState.value = state.copy(
            input = if (state.step in sensitiveInputSteps) "" else state.input,
            isSubmitting = true,
        )
        viewModelScope.launch {
            try {
                when (state.step) {
                    LoginStep.PHONE_NUMBER -> repository.submitPhoneNumber(submittedInput)
                    LoginStep.CODE -> repository.submitCode(submittedInput)
                    LoginStep.PASSWORD -> repository.submitPassword(submittedInput)
                    else -> Unit
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                reportFailureIfActionStillOwnsUi(actionEpoch, state.step, requireSubmitting = true)
            }
        }
    }

    fun resendCode() {
        val state = mutableUiState.value
        if (!state.canResendCode) return

        val actionEpoch = stateEpoch
        mutableUiState.value = state.copy(isSubmitting = true)
        viewModelScope.launch {
            try {
                repository.resendCode()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                reportFailureIfActionStillOwnsUi(
                    actionEpoch,
                    LoginStep.CODE,
                    requireSubmitting = true,
                )
            }
        }
    }

    fun retryStart() {
        val state = mutableUiState.value
        if (state.step != LoginStep.INITIALIZING || state.failure == null || state.isSubmitting) return

        mutableUiState.value = state.copy(isSubmitting = true)
        startRepositoryIfIdle()
    }

    fun logout() {
        val state = mutableUiState.value
        if (!state.canLogout) return

        val actionEpoch = stateEpoch
        mutableUiState.value = state.copy(isSubmitting = true)
        viewModelScope.launch {
            try {
                repository.logout()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                reportFailureIfActionStillOwnsUi(actionEpoch, state.step, requireSubmitting = true)
            }
        }
    }

    private fun startRepositoryIfIdle() {
        if (startJob?.isActive == true) return

        startJob = viewModelScope.launch {
            val actionEpoch = stateEpoch
            val actionStep = mutableUiState.value.step
            try {
                repository.start()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                reportFailureIfActionStillOwnsUi(actionEpoch, actionStep, requireSubmitting = false)
            }
        }
    }

    private fun applyAuthState(authState: TelegramAuthState) {
        stateEpoch += 1
        floodWaitJob?.cancel()
        floodWaitJob = null
        resendCountdownJob?.cancel()
        resendCountdownJob = null

        val current = mutableUiState.value
        val next = authState.toLoginUiState(
            retainedInput = current.input.takeIf {
                authState.canRetainCurrentInput(current)
            }.orEmpty(),
        )
        mutableUiState.value = next

        startResendCountdown(next)

        val floodWait = authState.inputFloodWaitOrNull() ?: return
        val seconds = floodWait.retryAfterSeconds.coerceAtLeast(0)
        mutableUiState.update { it.copy(retrySecondsRemaining = seconds) }
        if (seconds == 0) return

        floodWaitJob = viewModelScope.launch {
            repeat(seconds) {
                delay(1_000)
                mutableUiState.update {
                    val remaining = (it.retrySecondsRemaining - 1).coerceAtLeast(0)
                    if (remaining == 0 && it.failure is TelegramAuthFailure.FloodWait) {
                        it.copy(retrySecondsRemaining = 0, failure = null)
                    } else {
                        it.copy(retrySecondsRemaining = remaining)
                    }
                }
            }
        }
    }

    private fun startResendCountdown(state: LoginUiState) {
        val seconds = state.resendSecondsRemaining
        if (state.step != LoginStep.CODE || seconds == 0) return

        resendCountdownJob = viewModelScope.launch {
            repeat(seconds) {
                delay(1_000)
                mutableUiState.update {
                    it.copy(
                        resendSecondsRemaining =
                            (it.resendSecondsRemaining - 1).coerceAtLeast(0),
                    )
                }
            }
        }
    }

    private fun TelegramAuthState.toLoginUiState(retainedInput: String): LoginUiState = when (this) {
        is TelegramAuthState.UnconfiguredCredentials -> LoginUiState(
            step = LoginStep.UNCONFIGURED,
            invalidKeys = invalidKeys,
        )
        TelegramAuthState.Initializing,
        TelegramAuthState.Closed,
        -> LoginUiState(LoginStep.INITIALIZING)
        is TelegramAuthState.WaitingPhoneNumber -> LoginUiState(
            step = LoginStep.PHONE_NUMBER,
            input = retainedInput,
            failure = failure,
        )
        is TelegramAuthState.WaitingCode -> LoginUiState(
            step = LoginStep.CODE,
            input = retainedInput,
            failure = failure,
            codeInfo = codeInfo,
            resendSecondsRemaining = codeInfo.resendTimeoutSeconds,
        )
        is TelegramAuthState.WaitingPassword -> LoginUiState(
            step = LoginStep.PASSWORD,
            input = retainedInput,
            failure = failure,
        )
        is TelegramAuthState.Authorized -> LoginUiState(
            step = LoginStep.AUTHORIZED,
            failure = failure,
        )
        TelegramAuthState.LoggingOut -> LoginUiState(LoginStep.LOGGING_OUT)
        TelegramAuthState.Closing -> LoginUiState(LoginStep.CLOSING)
        is TelegramAuthState.Unsupported -> LoginUiState(
            step = LoginStep.UNSUPPORTED,
            unsupportedStep = step,
        )
        is TelegramAuthState.FatalError -> LoginUiState(
            step = LoginStep.FATAL_ERROR,
            failure = failure,
        )
    }

    private fun reportFailureIfActionStillOwnsUi(
        actionEpoch: Long,
        actionStep: LoginStep,
        requireSubmitting: Boolean,
    ) {
        if (stateEpoch != actionEpoch) return

        mutableUiState.update { current ->
            if (current.step != actionStep || (requireSubmitting && !current.isSubmitting)) {
                current
            } else {
                current.copy(
                    failure = TelegramAuthFailure.Unknown,
                    isSubmitting = false,
                )
            }
        }
    }

    private fun TelegramAuthState.inputFloodWaitOrNull(): TelegramAuthFailure.FloodWait? = when (this) {
        is TelegramAuthState.WaitingPhoneNumber -> failure as? TelegramAuthFailure.FloodWait
        is TelegramAuthState.WaitingCode -> failure as? TelegramAuthFailure.FloodWait
        is TelegramAuthState.WaitingPassword -> failure as? TelegramAuthFailure.FloodWait
        is TelegramAuthState.Authorized -> failure as? TelegramAuthFailure.FloodWait
        else -> null
    }

    private fun TelegramAuthState.canRetainCurrentInput(current: LoginUiState): Boolean = when (this) {
        is TelegramAuthState.WaitingPhoneNumber -> current.step == LoginStep.PHONE_NUMBER
        is TelegramAuthState.WaitingCode -> current.step == LoginStep.CODE && !current.isSubmitting
        is TelegramAuthState.WaitingPassword -> current.step == LoginStep.PASSWORD && !current.isSubmitting
        else -> false
    }

    private companion object {
        val editableSteps = setOf(LoginStep.PHONE_NUMBER, LoginStep.CODE, LoginStep.PASSWORD)
        val sensitiveInputSteps = setOf(LoginStep.CODE, LoginStep.PASSWORD)
    }
}
