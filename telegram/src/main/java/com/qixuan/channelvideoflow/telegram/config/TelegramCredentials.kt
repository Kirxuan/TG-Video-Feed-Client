package com.qixuan.channelvideoflow.telegram.config

class TelegramCredentials(
    val apiId: Int,
    val apiHash: String,
) {
    override fun toString(): String = "TelegramCredentials(REDACTED)"
}

sealed interface TelegramCredentialsResult {
    data class Available(val credentials: TelegramCredentials) : TelegramCredentialsResult
    data class Unavailable(val invalidKeys: Set<String>) : TelegramCredentialsResult
}

fun interface TelegramCredentialsProvider {
    fun get(): TelegramCredentialsResult
}
