package com.qixuan.channelvideoflow.config

import com.qixuan.channelvideoflow.BuildConfig
import com.qixuan.channelvideoflow.telegram.config.TelegramCredentials
import com.qixuan.channelvideoflow.telegram.config.TelegramCredentialsProvider
import com.qixuan.channelvideoflow.telegram.config.TelegramCredentialsResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BuildConfigTelegramCredentialStatusProvider @Inject constructor() :
    TelegramCredentialStatusProvider,
    TelegramCredentialsProvider {
    override fun getStatus(): TelegramCredentialStatus = TelegramCredentialEvaluator.evaluate(
        apiId = BuildConfig.TELEGRAM_API_ID,
        apiHash = BuildConfig.TELEGRAM_API_HASH,
    )

    override fun get(): TelegramCredentialsResult = buildTelegramCredentialsResult(
        apiId = BuildConfig.TELEGRAM_API_ID,
        apiHash = BuildConfig.TELEGRAM_API_HASH,
    )
}

fun buildTelegramCredentialsResult(
    apiId: String,
    apiHash: String,
): TelegramCredentialsResult = when (
    val status = TelegramCredentialEvaluator.evaluate(apiId = apiId, apiHash = apiHash)
) {
    TelegramCredentialStatus.Configured -> {
        val parsedApiId = apiId.trim().toIntOrNull()
            ?: return TelegramCredentialsResult.Unavailable(setOf("TELEGRAM_API_ID"))
        TelegramCredentialsResult.Available(
            TelegramCredentials(
                apiId = parsedApiId,
                apiHash = apiHash.trim(),
            ),
        )
    }
    is TelegramCredentialStatus.Unconfigured -> TelegramCredentialsResult.Unavailable(
        invalidKeys = status.invalidKeys.mapTo(linkedSetOf()) { it.configurationName },
    )
}
