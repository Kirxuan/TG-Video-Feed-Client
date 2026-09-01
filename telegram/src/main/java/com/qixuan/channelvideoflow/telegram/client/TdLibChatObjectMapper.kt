package com.qixuan.channelvideoflow.telegram.client

import org.drinkless.tdlib.TdApi

internal object TdLibChatObjectMapper {
    fun mapChat(chat: TdApi.Chat): TelegramClientChat = TelegramClientChat(
        chatId = chat.id,
        title = chat.title,
        type = when (val type = chat.type) {
            is TdApi.ChatTypePrivate -> TelegramClientChatType.Private
            is TdApi.ChatTypeBasicGroup -> TelegramClientChatType.BasicGroup
            is TdApi.ChatTypeSecret -> TelegramClientChatType.Secret
            is TdApi.ChatTypeSupergroup -> TelegramClientChatType.Supergroup(
                supergroupId = type.supergroupId,
                isChannel = type.isChannel,
            )
            else -> TelegramClientChatType.Unknown
        },
    )

    fun mapSupergroup(supergroup: TdApi.Supergroup): TelegramClientSupergroup =
        TelegramClientSupergroup(
            supergroupId = supergroup.id,
            isChannel = supergroup.isChannel,
            username = supergroup.usernames
                ?.activeUsernames
                ?.firstOrNull()
                ?.takeIf(String::isNotBlank),
            memberStatus = when (val status = supergroup.status) {
                is TdApi.ChatMemberStatusCreator ->
                    TelegramClientMemberStatus.Creator(status.isMember)
                is TdApi.ChatMemberStatusAdministrator ->
                    TelegramClientMemberStatus.Administrator
                is TdApi.ChatMemberStatusMember -> TelegramClientMemberStatus.Member
                is TdApi.ChatMemberStatusRestricted ->
                    TelegramClientMemberStatus.Restricted(status.isMember)
                is TdApi.ChatMemberStatusLeft -> TelegramClientMemberStatus.Left
                is TdApi.ChatMemberStatusBanned -> TelegramClientMemberStatus.Banned
                else -> TelegramClientMemberStatus.Unknown
            },
        )
}
