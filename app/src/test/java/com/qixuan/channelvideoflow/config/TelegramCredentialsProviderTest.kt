package com.qixuan.channelvideoflow.config

import com.qixuan.channelvideoflow.telegram.config.TelegramCredentialsResult
import com.qixuan.channelvideoflow.telegram.config.buildTelegramCredentialsResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelegramCredentialsProviderTest {

    @Test
    fun `blank credentials expose both configuration key names`() {
        val result = buildTelegramCredentialsResult(apiId = "", apiHash = "")

        assertEquals(
            setOf("TELEGRAM_API_ID", "TELEGRAM_API_HASH"),
            (result as TelegramCredentialsResult.Unavailable).invalidKeys,
        )
    }

    @Test
    fun `invalid api id exposes only the api id configuration key`() {
        val result = buildTelegramCredentialsResult(
            apiId = "not-a-number",
            apiHash = SYNTHETIC_VALID_HASH,
        )

        assertEquals(
            setOf("TELEGRAM_API_ID"),
            (result as TelegramCredentialsResult.Unavailable).invalidKeys,
        )
    }

    @Test
    fun `invalid api hash exposes only the api hash configuration key`() {
        val result = buildTelegramCredentialsResult(
            apiId = "12345",
            apiHash = "not-a-32-character-hexadecimal-hash",
        )

        assertEquals(
            setOf("TELEGRAM_API_HASH"),
            (result as TelegramCredentialsResult.Unavailable).invalidKeys,
        )
    }

    @Test
    fun `valid synthetic credentials expose parsed id and exact hash`() {
        val result = buildTelegramCredentialsResult(
            apiId = "12345",
            apiHash = SYNTHETIC_VALID_HASH,
        )

        val credentials = (result as TelegramCredentialsResult.Available).credentials
        assertEquals(12345, credentials.apiId)
        assertEquals(SYNTHETIC_VALID_HASH, credentials.apiHash)
    }

    @Test
    fun `credential result string never exposes the synthetic hash`() {
        val result = buildTelegramCredentialsResult(
            apiId = "12345",
            apiHash = SYNTHETIC_VALID_HASH,
        )

        assertFalse(result.toString().contains(SYNTHETIC_VALID_HASH))
        assertTrue(result.toString().contains("REDACTED"))
    }

    private companion object {
        const val SYNTHETIC_VALID_HASH = "0123456789abcdef0123456789abcdef"
    }
}
