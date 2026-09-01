package com.qixuan.channelvideoflow.telegram.auth

import com.qixuan.channelvideoflow.model.auth.TelegramCodeDeliveryType
import com.qixuan.channelvideoflow.model.auth.TelegramCodeInfo
import com.qixuan.channelvideoflow.model.auth.TelegramUnsupportedAuthStep
import com.qixuan.channelvideoflow.telegram.client.TelegramClientAuthorizationState
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class TdLibAuthorizationStateMapperTest {
    @Test
    fun mapsAllSupportedAuthorizationStates() {
        val cases = listOf(
            TdApi.AuthorizationStateWaitTdlibParameters() to
                TelegramClientAuthorizationState.WaitTdlibParameters,
            TdApi.AuthorizationStateWaitPhoneNumber() to
                TelegramClientAuthorizationState.WaitPhoneNumber,
            TdApi.AuthorizationStateWaitCode() to TelegramClientAuthorizationState.WaitCode(),
            TdApi.AuthorizationStateWaitPassword() to TelegramClientAuthorizationState.WaitPassword,
            TdApi.AuthorizationStateReady() to TelegramClientAuthorizationState.Ready,
            TdApi.AuthorizationStateLoggingOut() to TelegramClientAuthorizationState.LoggingOut,
            TdApi.AuthorizationStateClosing() to TelegramClientAuthorizationState.Closing,
            TdApi.AuthorizationStateClosed() to TelegramClientAuthorizationState.Closed,
        )

        cases.forEach { (tdLibState, expected) ->
            assertEquals(expected, TdLibAuthorizationStateMapper.map(tdLibState))
        }
    }

    @Test
    fun mapsCodeDeliveryAndResendMetadataWithoutPhoneNumberOrTdLibTypes() {
        val tdLibState = TdApi.AuthorizationStateWaitCode(
            TdApi.AuthenticationCodeInfo(
                "synthetic-phone-must-not-cross-boundary",
                TdApi.AuthenticationCodeTypeTelegramMessage(5),
                TdApi.AuthenticationCodeTypeSms(5),
                42,
            ),
        )

        assertEquals(
            TelegramClientAuthorizationState.WaitCode(
                TelegramCodeInfo(
                    deliveryType = TelegramCodeDeliveryType.TELEGRAM_MESSAGE,
                    nextDeliveryType = TelegramCodeDeliveryType.SMS,
                    resendTimeoutSeconds = 42,
                ),
            ),
            TdLibAuthorizationStateMapper.map(tdLibState),
        )
    }

    @Test
    fun mapsEveryUnsupportedAuthorizationStateToItsExactSafeStep() {
        val cases = listOf(
            TdApi.AuthorizationStateWaitPremiumPurchase() to
                TelegramUnsupportedAuthStep.PREMIUM_PURCHASE,
            TdApi.AuthorizationStateWaitEmailAddress() to
                TelegramUnsupportedAuthStep.EMAIL_ADDRESS,
            TdApi.AuthorizationStateWaitEmailCode() to
                TelegramUnsupportedAuthStep.EMAIL_CODE,
            TdApi.AuthorizationStateWaitOtherDeviceConfirmation() to
                TelegramUnsupportedAuthStep.OTHER_DEVICE_CONFIRMATION,
            TdApi.AuthorizationStateWaitRegistration() to
                TelegramUnsupportedAuthStep.REGISTRATION,
        )

        cases.forEach { (tdLibState, expectedStep) ->
            assertEquals(
                TelegramClientAuthorizationState.Unsupported(expectedStep),
                TdLibAuthorizationStateMapper.map(tdLibState),
            )
        }
    }

    @Test
    fun mapsFutureAuthorizationStateToUnknownAndNeverReady() {
        val futureState = object : TdApi.AuthorizationState() {
            override fun getConstructor(): Int = Int.MIN_VALUE
        }

        val mapped = TdLibAuthorizationStateMapper.map(futureState)

        assertEquals(
            TelegramClientAuthorizationState.Unsupported(TelegramUnsupportedAuthStep.UNKNOWN),
            mapped,
        )
        assertNotEquals(TelegramClientAuthorizationState.Ready, mapped)
    }
}
