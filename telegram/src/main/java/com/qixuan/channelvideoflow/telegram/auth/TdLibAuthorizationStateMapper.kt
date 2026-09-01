package com.qixuan.channelvideoflow.telegram.auth

import com.qixuan.channelvideoflow.model.auth.TelegramCodeDeliveryType
import com.qixuan.channelvideoflow.model.auth.TelegramCodeInfo
import com.qixuan.channelvideoflow.model.auth.TelegramUnsupportedAuthStep
import com.qixuan.channelvideoflow.telegram.client.TelegramClientAuthorizationState
import org.drinkless.tdlib.TdApi

internal object TdLibAuthorizationStateMapper {
    fun map(state: TdApi.AuthorizationState): TelegramClientAuthorizationState =
        when (state) {
            is TdApi.AuthorizationStateWaitTdlibParameters ->
                TelegramClientAuthorizationState.WaitTdlibParameters
            is TdApi.AuthorizationStateWaitPhoneNumber ->
                TelegramClientAuthorizationState.WaitPhoneNumber
            is TdApi.AuthorizationStateWaitCode -> TelegramClientAuthorizationState.WaitCode(
                codeInfo = mapCodeInfo(state.codeInfo),
            )
            is TdApi.AuthorizationStateWaitPassword -> TelegramClientAuthorizationState.WaitPassword
            is TdApi.AuthorizationStateReady -> TelegramClientAuthorizationState.Ready
            is TdApi.AuthorizationStateLoggingOut -> TelegramClientAuthorizationState.LoggingOut
            is TdApi.AuthorizationStateClosing -> TelegramClientAuthorizationState.Closing
            is TdApi.AuthorizationStateClosed -> TelegramClientAuthorizationState.Closed
            is TdApi.AuthorizationStateWaitPremiumPurchase -> unsupported(
                TelegramUnsupportedAuthStep.PREMIUM_PURCHASE,
            )
            is TdApi.AuthorizationStateWaitEmailAddress -> unsupported(
                TelegramUnsupportedAuthStep.EMAIL_ADDRESS,
            )
            is TdApi.AuthorizationStateWaitEmailCode -> unsupported(
                TelegramUnsupportedAuthStep.EMAIL_CODE,
            )
            is TdApi.AuthorizationStateWaitOtherDeviceConfirmation -> unsupported(
                TelegramUnsupportedAuthStep.OTHER_DEVICE_CONFIRMATION,
            )
            is TdApi.AuthorizationStateWaitRegistration -> unsupported(
                TelegramUnsupportedAuthStep.REGISTRATION,
            )
            else -> unsupported(TelegramUnsupportedAuthStep.UNKNOWN)
        }

    private fun mapCodeInfo(info: TdApi.AuthenticationCodeInfo?): TelegramCodeInfo =
        TelegramCodeInfo(
            deliveryType = mapCodeType(info?.type),
            nextDeliveryType = info?.nextType?.let(::mapCodeType),
            resendTimeoutSeconds = info?.timeout?.coerceAtLeast(0) ?: 0,
        )

    private fun mapCodeType(type: TdApi.AuthenticationCodeType?): TelegramCodeDeliveryType =
        when (type) {
            is TdApi.AuthenticationCodeTypeTelegramMessage ->
                TelegramCodeDeliveryType.TELEGRAM_MESSAGE
            is TdApi.AuthenticationCodeTypeSms -> TelegramCodeDeliveryType.SMS
            is TdApi.AuthenticationCodeTypeSmsWord -> TelegramCodeDeliveryType.SMS_WORD
            is TdApi.AuthenticationCodeTypeSmsPhrase -> TelegramCodeDeliveryType.SMS_PHRASE
            is TdApi.AuthenticationCodeTypeCall -> TelegramCodeDeliveryType.PHONE_CALL
            is TdApi.AuthenticationCodeTypeFlashCall -> TelegramCodeDeliveryType.FLASH_CALL
            is TdApi.AuthenticationCodeTypeMissedCall -> TelegramCodeDeliveryType.MISSED_CALL
            is TdApi.AuthenticationCodeTypeFragment -> TelegramCodeDeliveryType.FRAGMENT
            is TdApi.AuthenticationCodeTypeFirebaseAndroid ->
                TelegramCodeDeliveryType.FIREBASE_ANDROID
            is TdApi.AuthenticationCodeTypeFirebaseIos -> TelegramCodeDeliveryType.FIREBASE_IOS
            else -> TelegramCodeDeliveryType.UNKNOWN
        }

    private fun unsupported(
        step: TelegramUnsupportedAuthStep,
    ): TelegramClientAuthorizationState = TelegramClientAuthorizationState.Unsupported(step)
}
