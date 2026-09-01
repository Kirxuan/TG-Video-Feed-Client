package com.qixuan.channelvideoflow.telegram.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class TelegramCredentialsTest {
    @Test
    fun toStringUsesFixedRedactedTextWithoutTheSyntheticHash() {
        val credentials = TelegramCredentials(12345, "synthetic-hash")

        assertEquals("TelegramCredentials(REDACTED)", credentials.toString())
        assertFalse(credentials.toString().contains("synthetic-hash"))
    }
}
