package com.qixuan.channelvideoflow.domain.auth

import com.qixuan.channelvideoflow.model.auth.TelegramAuthState
import kotlinx.coroutines.flow.StateFlow

interface TelegramAuthRepository {
    val authState: StateFlow<TelegramAuthState>

    suspend fun start()
    suspend fun submitPhoneNumber(phoneNumber: String)
    suspend fun submitCode(code: String)
    suspend fun resendCode()
    suspend fun submitPassword(password: String)
    suspend fun logout()
}
