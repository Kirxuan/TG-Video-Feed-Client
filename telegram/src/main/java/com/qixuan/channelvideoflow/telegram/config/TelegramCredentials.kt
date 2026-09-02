package com.qixuan.channelvideoflow.telegram.config

class TelegramCredentials(
    val apiId: Int,
    val apiHash: String,
) {
    override fun toString(): String = "TelegramCredentials(REDACTED)"
}

enum class TelegramCredentialsUnavailableReason {
    MISSING_OR_INVALID,
    SECURE_STORAGE,
}

sealed interface TelegramCredentialsResult {
    data class Available(val credentials: TelegramCredentials) : TelegramCredentialsResult
    data class Unavailable(
        val invalidKeys: Set<String>,
        val reason: TelegramCredentialsUnavailableReason =
            TelegramCredentialsUnavailableReason.MISSING_OR_INVALID,
    ) : TelegramCredentialsResult
}

fun interface TelegramCredentialsProvider {
    fun get(): TelegramCredentialsResult
}

fun interface PackagedTelegramCredentialsProvider {
    fun get(): TelegramCredentialsResult
}

interface TelegramCredentialsStore {
    suspend fun save(apiId: String, apiHash: String): TelegramCredentialsResult
}

fun buildTelegramCredentialsResult(
    apiId: String,
    apiHash: String,
): TelegramCredentialsResult {
    val trimmedApiId = apiId.trim()
    val trimmedApiHash = apiHash.trim()
    val invalidKeys = buildSet {
        if (trimmedApiId.toIntOrNull()?.let { it > 0 } != true) {
            add(TELEGRAM_API_ID_KEY)
        }
        if (!API_HASH_PATTERN.matches(trimmedApiHash)) {
            add(TELEGRAM_API_HASH_KEY)
        }
    }
    if (invalidKeys.isNotEmpty()) {
        return TelegramCredentialsResult.Unavailable(invalidKeys)
    }

    return TelegramCredentialsResult.Available(
        TelegramCredentials(
            apiId = checkNotNull(trimmedApiId.toIntOrNull()),
            apiHash = trimmedApiHash,
        ),
    )
}

const val TELEGRAM_API_ID_KEY = "TELEGRAM_API_ID"
const val TELEGRAM_API_HASH_KEY = "TELEGRAM_API_HASH"

private val API_HASH_PATTERN = Regex("^[0-9a-fA-F]{32}$")
