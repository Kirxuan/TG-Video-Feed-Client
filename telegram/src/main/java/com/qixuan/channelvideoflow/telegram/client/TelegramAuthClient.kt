package com.qixuan.channelvideoflow.telegram.client

import kotlinx.coroutines.flow.Flow

internal interface TelegramAuthClient {
    val events: Flow<TelegramClientEvent>

    suspend fun start()
    suspend fun submitPhoneNumber(phoneNumber: String)
    suspend fun submitCode(code: String)
    suspend fun resendCode()
    suspend fun submitPassword(password: String)
    suspend fun logout()
}
