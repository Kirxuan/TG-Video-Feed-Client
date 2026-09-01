package com.qixuan.channelvideoflow.telegram.client

import com.qixuan.channelvideoflow.model.auth.TelegramUnsupportedAuthStep
import com.qixuan.channelvideoflow.model.auth.TelegramCodeInfo

enum class TelegramAuthRequest {
    PARAMETERS,
    PHONE_NUMBER,
    CODE,
    RESEND_CODE,
    PASSWORD,
    LOG_OUT,
    CLOSE,
}

sealed interface TelegramClientAuthorizationState {
    data object WaitTdlibParameters : TelegramClientAuthorizationState
    data object WaitPhoneNumber : TelegramClientAuthorizationState
    data class WaitCode(
        val codeInfo: TelegramCodeInfo = TelegramCodeInfo(),
    ) : TelegramClientAuthorizationState
    data object WaitPassword : TelegramClientAuthorizationState
    data object Ready : TelegramClientAuthorizationState
    data object LoggingOut : TelegramClientAuthorizationState
    data object Closing : TelegramClientAuthorizationState
    data object Closed : TelegramClientAuthorizationState
    data class Unsupported(
        val step: TelegramUnsupportedAuthStep,
    ) : TelegramClientAuthorizationState
}

internal sealed interface TelegramClientEvent {
    data class CredentialsUnavailable(
        val invalidKeys: Set<String>,
    ) : TelegramClientEvent

    data class AuthorizationStateChanged(
        val state: TelegramClientAuthorizationState,
    ) : TelegramClientEvent

    class RequestFailed(
        val request: TelegramAuthRequest,
        val code: Int,
        internal val rawMessage: String,
    ) : TelegramClientEvent {
        override fun toString(): String = "RequestFailed(request=$request, code=$code)"
    }

    data class FatalFailure(
        val category: FatalCategory,
    ) : TelegramClientEvent
}

enum class FatalCategory {
    NATIVE_LIBRARY,
    INITIALIZATION,
    DATABASE,
}

internal enum class TelegramClientChatList {
    MAIN,
    ARCHIVE,
}

internal sealed interface TelegramClientChatType {
    data object Private : TelegramClientChatType
    data object BasicGroup : TelegramClientChatType
    data object Secret : TelegramClientChatType

    data class Supergroup(
        val supergroupId: Long,
        val isChannel: Boolean,
    ) : TelegramClientChatType

    data object Unknown : TelegramClientChatType
}

internal data class TelegramClientChat(
    val chatId: Long,
    val title: String,
    val type: TelegramClientChatType,
)

internal sealed interface TelegramClientMemberStatus {
    data class Creator(val isMember: Boolean) : TelegramClientMemberStatus
    data object Administrator : TelegramClientMemberStatus
    data object Member : TelegramClientMemberStatus
    data class Restricted(val isMember: Boolean) : TelegramClientMemberStatus
    data object Left : TelegramClientMemberStatus
    data object Banned : TelegramClientMemberStatus
    data object Unknown : TelegramClientMemberStatus
}

internal data class TelegramClientSupergroup(
    val supergroupId: Long,
    val isChannel: Boolean,
    val username: String?,
    val memberStatus: TelegramClientMemberStatus,
)

internal data class TelegramClientChats(
    val totalCount: Int,
    val chatIds: List<Long>,
)

internal sealed interface TelegramClientFailure {
    data object SessionUnavailable : TelegramClientFailure
    data object NetworkUnavailable : TelegramClientFailure
    data class FloodWait(val retryAfterSeconds: Int) : TelegramClientFailure
    data object AccessLost : TelegramClientFailure
    data object Timeout : TelegramClientFailure
    data object NotFound : TelegramClientFailure
    data class RequestRejected(val code: Int) : TelegramClientFailure
    data object Unknown : TelegramClientFailure
}

internal data class TelegramClientVideoVariant(
    val alternativeId: Long = 0,
    val fileId: Int,
    val remoteUniqueId: String,
    val fileSize: Long?,
    val width: Int,
    val height: Int,
    val codec: String,
    val hlsManifestFile: TelegramClientMediaFile? = null,
)

internal data class TelegramClientMediaFile(
    val fileId: Int,
    val remoteUniqueId: String,
    val fileSize: Long?,
)

internal data class TelegramClientVideoContent(
    val fileId: Int,
    val remoteUniqueId: String,
    val caption: String,
    val hashtagEntityRanges: List<TelegramClientUtf16Range>,
    val durationSeconds: Int,
    val width: Int,
    val height: Int,
    val fileSize: Long?,
    val supportsStreaming: Boolean,
    val alternativeVariants: List<TelegramClientVideoVariant> = emptyList(),
)

internal data class TelegramClientFileSnapshot(
    val fileId: Int,
    val size: Long,
    val expectedSize: Long,
    val localPath: String?,
    val canBeDownloaded: Boolean,
    val isDownloadingActive: Boolean,
    val isDownloadingCompleted: Boolean,
    val downloadOffset: Long,
    val downloadedPrefixSize: Long,
    val downloadedSize: Long,
)

internal data class TelegramClientStorageStatistics(
    val videoBytes: Long,
    val videoFileCount: Int,
)

internal data class TelegramClientUtf16Range(
    val offset: Int,
    val length: Int,
)

internal data class TelegramClientMessage(
    val chatId: Long,
    val messageId: Long,
    val publishTime: Long,
    val editTime: Long?,
    val canBeSaved: Boolean,
    val video: TelegramClientVideoContent?,
)

internal data class TelegramClientVideoSearchPage(
    val messages: List<TelegramClientMessage>,
    val approximateTotalCount: Int?,
    val nextFromMessageId: Long,
)

internal data class TelegramClientMessageProperties(
    val canGetLink: Boolean,
)

internal data class TelegramClientMessageLink(
    val httpsUrl: String,
)

internal sealed interface TelegramMessageClientEvent {
    data class NewMessage(
        val message: TelegramClientMessage,
    ) : TelegramMessageClientEvent

    data class MessageContentChanged(
        val chatId: Long,
        val messageId: Long,
        val video: TelegramClientVideoContent?,
    ) : TelegramMessageClientEvent

    data class MessageEdited(
        val chatId: Long,
        val messageId: Long,
        val editTime: Long?,
    ) : TelegramMessageClientEvent

    data class MessagesDeleted(
        val chatId: Long,
        val messageIds: List<Long>,
        val fromCache: Boolean,
    ) : TelegramMessageClientEvent

    data object AccountLoggingOut : TelegramMessageClientEvent
}

internal sealed interface TelegramClientResult<out T> {
    data class Success<T>(val value: T) : TelegramClientResult<T>
    data class Failure(val failure: TelegramClientFailure) : TelegramClientResult<Nothing>
}

internal sealed interface TelegramLoadChatsResult {
    data object Loaded : TelegramLoadChatsResult
    data object EndReached : TelegramLoadChatsResult
    data class Failed(val failure: TelegramClientFailure) : TelegramLoadChatsResult
}

internal sealed interface TelegramChatClientEvent {
    data class ChatChanged(val chat: TelegramClientChat) : TelegramChatClientEvent
    data class ChatTitleChanged(val chatId: Long, val title: String) : TelegramChatClientEvent
    data class SupergroupChanged(
        val supergroup: TelegramClientSupergroup,
    ) : TelegramChatClientEvent

    data object AccountLoggingOut : TelegramChatClientEvent
}
