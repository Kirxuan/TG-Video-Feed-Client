package com.qixuan.channelvideoflow.feature.auth

import com.qixuan.channelvideoflow.model.auth.TelegramAuthFailure
import com.qixuan.channelvideoflow.model.auth.TelegramCodeInfo
import com.qixuan.channelvideoflow.model.auth.TelegramUnsupportedAuthStep

enum class LoginStep {
    UNCONFIGURED,
    INITIALIZING,
    PHONE_NUMBER,
    CODE,
    PASSWORD,
    AUTHORIZED,
    LOGGING_OUT,
    CLOSING,
    UNSUPPORTED,
    FATAL_ERROR,
}

data class LoginUiState(
    val step: LoginStep,
    val input: String = "",
    val invalidKeys: Set<String> = emptySet(),
    val failure: TelegramAuthFailure? = null,
    val unsupportedStep: TelegramUnsupportedAuthStep? = null,
    val retrySecondsRemaining: Int = 0,
    val codeInfo: TelegramCodeInfo? = null,
    val resendSecondsRemaining: Int = 0,
    val isSubmitting: Boolean = false,
) {
    val canSubmit: Boolean
        get() = input.isNotBlank() &&
            retrySecondsRemaining == 0 &&
            !isSubmitting &&
            step in setOf(LoginStep.PHONE_NUMBER, LoginStep.CODE, LoginStep.PASSWORD)

    val canLogout: Boolean
        get() = step == LoginStep.AUTHORIZED && retrySecondsRemaining == 0 && !isSubmitting

    val canResendCode: Boolean
        get() = step == LoginStep.CODE &&
            codeInfo?.nextDeliveryType != null &&
            resendSecondsRemaining == 0 &&
            retrySecondsRemaining == 0 &&
            !isSubmitting

}
