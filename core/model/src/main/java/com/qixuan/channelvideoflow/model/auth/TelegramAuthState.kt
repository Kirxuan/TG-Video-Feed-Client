package com.qixuan.channelvideoflow.model.auth

enum class TelegramUnsupportedAuthStep {
    PREMIUM_PURCHASE,
    EMAIL_ADDRESS,
    EMAIL_CODE,
    OTHER_DEVICE_CONFIRMATION,
    REGISTRATION,
    UNKNOWN,
}

enum class TelegramCodeDeliveryType {
    TELEGRAM_MESSAGE,
    SMS,
    SMS_WORD,
    SMS_PHRASE,
    PHONE_CALL,
    FLASH_CALL,
    MISSED_CALL,
    FRAGMENT,
    FIREBASE_ANDROID,
    FIREBASE_IOS,
    UNKNOWN,
}

data class TelegramCodeInfo(
    val deliveryType: TelegramCodeDeliveryType = TelegramCodeDeliveryType.UNKNOWN,
    val nextDeliveryType: TelegramCodeDeliveryType? = null,
    val resendTimeoutSeconds: Int = 0,
)

sealed interface TelegramAuthState {
    data class UnconfiguredCredentials(
        val invalidKeys: Set<String>,
        val failure: TelegramAuthFailure? = null,
    ) : TelegramAuthState

    data object Initializing : TelegramAuthState
    data class WaitingPhoneNumber(
        val failure: TelegramAuthFailure? = null,
    ) : TelegramAuthState

    data class WaitingCode(
        val failure: TelegramAuthFailure? = null,
        val codeInfo: TelegramCodeInfo = TelegramCodeInfo(),
    ) : TelegramAuthState

    data class WaitingPassword(
        val failure: TelegramAuthFailure? = null,
    ) : TelegramAuthState

    data class Authorized(
        val failure: TelegramAuthFailure? = null,
    ) : TelegramAuthState

    data object LoggingOut : TelegramAuthState
    data object Closing : TelegramAuthState
    data object Closed : TelegramAuthState

    data class Unsupported(
        val step: TelegramUnsupportedAuthStep,
    ) : TelegramAuthState

    data class FatalError(
        val failure: TelegramAuthFailure,
    ) : TelegramAuthState
}
