package com.qixuan.channelvideoflow.config

import com.qixuan.channelvideoflow.BuildConfig
import com.qixuan.channelvideoflow.telegram.config.PackagedTelegramCredentialsProvider
import com.qixuan.channelvideoflow.telegram.config.TelegramCredentialsResult
import com.qixuan.channelvideoflow.telegram.config.buildTelegramCredentialsResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BuildConfigTelegramCredentialStatusProvider @Inject constructor() :
    TelegramCredentialStatusProvider,
    PackagedTelegramCredentialsProvider {
    override fun getStatus(): TelegramCredentialStatus = TelegramCredentialEvaluator.evaluate(
        apiId = BuildConfig.TELEGRAM_API_ID,
        apiHash = BuildConfig.TELEGRAM_API_HASH,
    )

    override fun get(): TelegramCredentialsResult = buildTelegramCredentialsResult(
        apiId = BuildConfig.TELEGRAM_API_ID,
        apiHash = BuildConfig.TELEGRAM_API_HASH,
    )
}
