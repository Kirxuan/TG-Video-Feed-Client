package com.qixuan.channelvideoflow.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TelegramCredentialEvaluatorTest {

    @Test
    fun `blank values report both configuration keys without exposing values`() {
        val status = TelegramCredentialEvaluator.evaluate(apiId = "", apiHash = "")

        assertEquals(
            TelegramCredentialStatus.Unconfigured(
                invalidKeys = setOf(
                    TelegramCredentialKey.ApiId,
                    TelegramCredentialKey.ApiHash,
                ),
            ),
            status,
        )
        assertTrue(status.toString().contains("TELEGRAM_API_ID").not())
    }

    @Test
    fun `non numeric api id reports only api id`() {
        val status = TelegramCredentialEvaluator.evaluate(
            apiId = "not-a-number",
            apiHash = SYNTHETIC_VALID_HASH,
        )

        assertEquals(
            TelegramCredentialStatus.Unconfigured(
                invalidKeys = setOf(TelegramCredentialKey.ApiId),
            ),
            status,
        )
    }

    @Test
    fun `non hexadecimal api hash reports only api hash`() {
        val status = TelegramCredentialEvaluator.evaluate(
            apiId = "12345",
            apiHash = "this-is-not-a-telegram-api-hash",
        )

        assertEquals(
            TelegramCredentialStatus.Unconfigured(
                invalidKeys = setOf(TelegramCredentialKey.ApiHash),
            ),
            status,
        )
    }

    @Test
    fun `synthetic valid values report configured without retaining credentials`() {
        val status = TelegramCredentialEvaluator.evaluate(
            apiId = "12345",
            apiHash = SYNTHETIC_VALID_HASH,
        )

        assertEquals(TelegramCredentialStatus.Configured, status)
        assertTrue(status.toString().contains(SYNTHETIC_VALID_HASH).not())
    }

    private companion object {
        const val SYNTHETIC_VALID_HASH = "0123456789abcdef0123456789abcdef"
    }
}
