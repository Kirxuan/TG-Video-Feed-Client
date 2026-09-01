package com.qixuan.channelvideoflow.telegram.auth

import com.qixuan.channelvideoflow.model.auth.TelegramAuthFailure
import com.qixuan.channelvideoflow.telegram.client.TelegramAuthRequest
import org.junit.Assert.assertEquals
import org.junit.Test

class TdLibAuthErrorMapperTest {
    @Test
    fun mapsExactFloodWaitAndClampsOversizedSeconds() {
        assertEquals(
            TelegramAuthFailure.FloodWait(42),
            TdLibAuthErrorMapper.map(TelegramAuthRequest.CODE, 429, "FLOOD_WAIT_42"),
        )
        assertEquals(
            TelegramAuthFailure.FloodWait(Int.MAX_VALUE),
            TdLibAuthErrorMapper.map(
                TelegramAuthRequest.CODE,
                429,
                "FLOOD_WAIT_999999999999999999999999999999999999999",
            ),
        )
    }

    @Test
    fun rejectsNonPositiveOrMalformedFloodWait() {
        assertEquals(
            TelegramAuthFailure.RequestRejected(429),
            TdLibAuthErrorMapper.map(TelegramAuthRequest.CODE, 429, "FLOOD_WAIT_0"),
        )
        assertEquals(
            TelegramAuthFailure.RequestRejected(429),
            TdLibAuthErrorMapper.map(TelegramAuthRequest.CODE, 429, "FLOOD_WAIT_-1"),
        )
        assertEquals(
            TelegramAuthFailure.RequestRejected(429),
            TdLibAuthErrorMapper.map(TelegramAuthRequest.CODE, 429, "prefix_FLOOD_WAIT_42"),
        )
    }

    @Test
    fun mapsExactCredentialFailuresForTheirRequests() {
        assertEquals(
            TelegramAuthFailure.InvalidPhoneNumber,
            TdLibAuthErrorMapper.map(
                TelegramAuthRequest.PHONE_NUMBER,
                400,
                "PHONE_NUMBER_INVALID",
            ),
        )
        assertEquals(
            TelegramAuthFailure.InvalidCode,
            TdLibAuthErrorMapper.map(TelegramAuthRequest.CODE, 400, "PHONE_CODE_INVALID"),
        )
        assertEquals(
            TelegramAuthFailure.InvalidPassword,
            TdLibAuthErrorMapper.map(
                TelegramAuthRequest.PASSWORD,
                400,
                "PASSWORD_HASH_INVALID",
            ),
        )
    }

    @Test
    fun mapsNetworkSignalsWithoutRetainingRawMessage() {
        assertEquals(
            TelegramAuthFailure.NetworkUnavailable,
            TdLibAuthErrorMapper.map(TelegramAuthRequest.CODE, 0, "synthetic sensitive detail"),
        )
        assertEquals(
            TelegramAuthFailure.NetworkUnavailable,
            TdLibAuthErrorMapper.map(TelegramAuthRequest.CODE, 500, "NETWORK_ERROR"),
        )
        assertEquals(
            TelegramAuthFailure.NetworkUnavailable,
            TdLibAuthErrorMapper.map(TelegramAuthRequest.CODE, 500, "REQUEST_ABORTED"),
        )
    }

    @Test
    fun mapsEveryOtherErrorToCodeOnlyRejection() {
        assertEquals(
            TelegramAuthFailure.RequestRejected(418),
            TdLibAuthErrorMapper.map(
                TelegramAuthRequest.CODE,
                418,
                "synthetic sensitive detail",
            ),
        )
        assertEquals(
            TelegramAuthFailure.RequestRejected(400),
            TdLibAuthErrorMapper.map(
                TelegramAuthRequest.CODE,
                400,
                "PHONE_NUMBER_INVALID",
            ),
        )
    }
}
