package com.qixuan.channelvideoflow.model.channel

data class TelegramChannel(
    val chatId: Long,
    val title: String,
    val username: String?,
    val isSelected: Boolean,
    val isPinned: Boolean = false,
)

enum class ChannelAccessState {
    AVAILABLE,
    UNAVAILABLE,
    UNKNOWN,
}

enum class ChannelScanState {
    NOT_STARTED,
    SCANNING,
    PAUSED,
    COMPLETED,
    ERROR,
}

sealed interface TelegramChatFailure {
    data object NetworkUnavailable : TelegramChatFailure

    data class FloodWait(
        val retryAfterSeconds: Int,
    ) : TelegramChatFailure

    data class RequestRejected(
        val code: Int,
    ) : TelegramChatFailure

    data object Timeout : TelegramChatFailure
    data object Database : TelegramChatFailure
    data object Unknown : TelegramChatFailure
}

sealed interface TelegramChatSyncState {
    data object Loading : TelegramChatSyncState
    data object Ready : TelegramChatSyncState

    data class Failed(
        val failure: TelegramChatFailure,
    ) : TelegramChatSyncState
}
