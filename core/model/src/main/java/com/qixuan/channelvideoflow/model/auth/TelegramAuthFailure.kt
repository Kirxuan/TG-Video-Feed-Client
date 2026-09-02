package com.qixuan.channelvideoflow.model.auth

sealed interface TelegramAuthFailure {
    data object InvalidApiCredentials : TelegramAuthFailure
    data object InvalidPhoneNumber : TelegramAuthFailure
    data object InvalidCode : TelegramAuthFailure
    data object InvalidPassword : TelegramAuthFailure
    data class FloodWait(val retryAfterSeconds: Int) : TelegramAuthFailure
    data object NetworkUnavailable : TelegramAuthFailure
    data object NativeLibraryLoadFailed : TelegramAuthFailure
    data object TdLibInitializationFailed : TelegramAuthFailure
    data object DatabaseFailed : TelegramAuthFailure
    data object CredentialStorageFailed : TelegramAuthFailure
    data class RequestRejected(val code: Int) : TelegramAuthFailure
    data object Unknown : TelegramAuthFailure
}
