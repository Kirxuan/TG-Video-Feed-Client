package com.qixuan.channelvideoflow.config

enum class TelegramCredentialKey(val configurationName: String) {
    ApiId("TELEGRAM_API_ID"),
    ApiHash("TELEGRAM_API_HASH"),
}

sealed interface TelegramCredentialStatus {
    data object Configured : TelegramCredentialStatus

    data class Unconfigured(
        val invalidKeys: Set<TelegramCredentialKey>,
    ) : TelegramCredentialStatus
}

object TelegramCredentialEvaluator {
    private val apiHashPattern = Regex("^[0-9a-fA-F]{32}$")

    fun evaluate(apiId: String, apiHash: String): TelegramCredentialStatus {
        val invalidKeys = buildSet {
            val parsedApiId = apiId.trim().toIntOrNull()
            if (parsedApiId == null || parsedApiId <= 0) {
                add(TelegramCredentialKey.ApiId)
            }

            if (!apiHashPattern.matches(apiHash.trim())) {
                add(TelegramCredentialKey.ApiHash)
            }
        }

        return if (invalidKeys.isEmpty()) {
            TelegramCredentialStatus.Configured
        } else {
            TelegramCredentialStatus.Unconfigured(invalidKeys)
        }
    }
}
