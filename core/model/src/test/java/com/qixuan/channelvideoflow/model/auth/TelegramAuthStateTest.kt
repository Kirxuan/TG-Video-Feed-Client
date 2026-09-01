package com.qixuan.channelvideoflow.model.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TelegramAuthStateTest {
    @Test
    fun waitingCodeCanCarrySanitizedFailure() {
        val state = TelegramAuthState.WaitingCode(
            failure = TelegramAuthFailure.InvalidCode,
        )

        assertEquals(TelegramAuthFailure.InvalidCode, state.failure)
    }

    @Test
    fun waitingCodeCarriesOnlySafeDeliveryMetadata() {
        val info = TelegramCodeInfo(
            deliveryType = TelegramCodeDeliveryType.TELEGRAM_MESSAGE,
            nextDeliveryType = TelegramCodeDeliveryType.SMS,
            resendTimeoutSeconds = 42,
        )

        assertEquals(info, TelegramAuthState.WaitingCode(codeInfo = info).codeInfo)
    }

    @Test
    fun freshWaitingPasswordHasNoFailure() {
        assertNull(TelegramAuthState.WaitingPassword().failure)
    }

    @Test
    fun floodWaitStoresOnlyRelativeSeconds() {
        assertEquals(
            42,
            TelegramAuthFailure.FloodWait(retryAfterSeconds = 42).retryAfterSeconds,
        )
    }
}
