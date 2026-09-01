package com.qixuan.channelvideoflow.config

fun interface TelegramCredentialStatusProvider {
    fun getStatus(): TelegramCredentialStatus
}
