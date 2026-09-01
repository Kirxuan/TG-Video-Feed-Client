package com.qixuan.channelvideoflow.telegram.client

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelegramClientModelsTest {
    @Test
    fun requestFailureStringNeverRendersRawMessage() {
        val syntheticSecret = "synthetic-code-246810"
        val event = TelegramClientEvent.RequestFailed(
            request = TelegramAuthRequest.CODE,
            code = 400,
            rawMessage = syntheticSecret,
        )

        assertEquals(TelegramAuthRequest.CODE, event.request)
        assertEquals(400, event.code)
        assertFalse(event.toString().contains(syntheticSecret))
        assertTrue(event.toString().contains("CODE"))
        assertTrue(event.toString().contains("400"))
    }
}
