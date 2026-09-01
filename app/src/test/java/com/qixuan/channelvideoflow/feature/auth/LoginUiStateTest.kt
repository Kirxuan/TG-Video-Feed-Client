package com.qixuan.channelvideoflow.feature.auth

import com.qixuan.channelvideoflow.model.auth.TelegramCodeDeliveryType
import com.qixuan.channelvideoflow.model.auth.TelegramCodeInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginUiStateTest {

    @Test
    fun `canSubmit permits only editable login steps with nonblank input and no block`() {
        assertTrue(LoginUiState(LoginStep.PHONE_NUMBER, input = "synthetic-phone").canSubmit)
        assertTrue(LoginUiState(LoginStep.CODE, input = "synthetic-code").canSubmit)
        assertTrue(LoginUiState(LoginStep.PASSWORD, input = "synthetic-password").canSubmit)

        LoginStep.entries
            .filterNot { it in setOf(LoginStep.PHONE_NUMBER, LoginStep.CODE, LoginStep.PASSWORD) }
            .forEach { step -> assertFalse(LoginUiState(step, input = "value").canSubmit) }
        assertFalse(LoginUiState(LoginStep.CODE, input = "   ").canSubmit)
        assertFalse(
            LoginUiState(LoginStep.CODE, input = "synthetic-code", retrySecondsRemaining = 1).canSubmit,
        )
        assertFalse(LoginUiState(LoginStep.CODE, input = "synthetic-code", isSubmitting = true).canSubmit)
    }

    @Test
    fun `canLogout permits only an idle authorized state`() {
        assertTrue(LoginUiState(LoginStep.AUTHORIZED).canLogout)
        assertFalse(LoginUiState(LoginStep.AUTHORIZED, isSubmitting = true).canLogout)
        assertFalse(LoginUiState(LoginStep.CODE).canLogout)
    }

    @Test
    fun `canResendCode requires next type and expired server timeout`() {
        val codeInfo = TelegramCodeInfo(
            deliveryType = TelegramCodeDeliveryType.TELEGRAM_MESSAGE,
            nextDeliveryType = TelegramCodeDeliveryType.SMS,
            resendTimeoutSeconds = 10,
        )

        assertTrue(LoginUiState(LoginStep.CODE, codeInfo = codeInfo).canResendCode)
        assertFalse(
            LoginUiState(
                LoginStep.CODE,
                codeInfo = codeInfo,
                resendSecondsRemaining = 1,
            ).canResendCode,
        )
        assertFalse(
            LoginUiState(
                LoginStep.CODE,
                codeInfo = TelegramCodeInfo(),
            ).canResendCode,
        )
    }
}
