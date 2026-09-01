package com.qixuan.channelvideoflow.telegram.auth

import com.qixuan.channelvideoflow.model.auth.TelegramAuthFailure
import com.qixuan.channelvideoflow.telegram.client.TelegramAuthRequest

internal object TdLibAuthErrorMapper {
    private val floodWaitPattern = Regex("^FLOOD_WAIT_([0-9]+)$")

    fun map(
        request: TelegramAuthRequest,
        code: Int,
        rawMessage: String,
    ): TelegramAuthFailure {
        if (code == 0 || rawMessage == "NETWORK_ERROR" || rawMessage == "REQUEST_ABORTED") {
            return TelegramAuthFailure.NetworkUnavailable
        }

        parseFloodWaitSeconds(rawMessage)?.let { seconds ->
            return TelegramAuthFailure.FloodWait(seconds)
        }

        return when {
            request == TelegramAuthRequest.PHONE_NUMBER && rawMessage == "PHONE_NUMBER_INVALID" ->
                TelegramAuthFailure.InvalidPhoneNumber
            request == TelegramAuthRequest.CODE && rawMessage == "PHONE_CODE_INVALID" ->
                TelegramAuthFailure.InvalidCode
            request == TelegramAuthRequest.PASSWORD && rawMessage == "PASSWORD_HASH_INVALID" ->
                TelegramAuthFailure.InvalidPassword
            else -> TelegramAuthFailure.RequestRejected(code)
        }
    }

    private fun parseFloodWaitSeconds(rawMessage: String): Int? {
        val digits = floodWaitPattern.matchEntire(rawMessage)?.groupValues?.get(1) ?: return null
        var seconds = 0
        digits.forEach { character ->
            val digit = character.digitToInt()
            if (seconds > (Int.MAX_VALUE - digit) / 10) {
                return Int.MAX_VALUE
            }
            seconds = seconds * 10 + digit
        }
        return seconds.takeIf { it > 0 }
    }
}
