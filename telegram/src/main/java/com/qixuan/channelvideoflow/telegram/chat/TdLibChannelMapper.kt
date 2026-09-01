package com.qixuan.channelvideoflow.telegram.chat

import com.qixuan.channelvideoflow.database.ChannelEntity
import com.qixuan.channelvideoflow.model.channel.ChannelAccessState
import com.qixuan.channelvideoflow.telegram.client.TelegramClientChat
import com.qixuan.channelvideoflow.telegram.client.TelegramClientChatType
import com.qixuan.channelvideoflow.telegram.client.TelegramClientMemberStatus
import com.qixuan.channelvideoflow.telegram.client.TelegramClientSupergroup

internal object TdLibChannelMapper {
    fun map(
        chat: TelegramClientChat,
        supergroup: TelegramClientSupergroup,
    ): ChannelEntity? {
        val chatType = chat.type as? TelegramClientChatType.Supergroup ?: return null
        if (!chatType.isChannel) return null
        if (chatType.supergroupId != supergroup.supergroupId) return null
        if (!supergroup.isChannel || !supergroup.memberStatus.hasChannelAccess()) return null

        return ChannelEntity(
            chatId = chat.chatId,
            title = chat.title,
            username = supergroup.username,
            accessState = ChannelAccessState.AVAILABLE,
        )
    }

    fun TelegramClientMemberStatus.hasChannelAccess(): Boolean = when (this) {
        is TelegramClientMemberStatus.Creator -> isMember
        TelegramClientMemberStatus.Administrator,
        TelegramClientMemberStatus.Member,
        -> true
        is TelegramClientMemberStatus.Restricted,
        TelegramClientMemberStatus.Left,
        TelegramClientMemberStatus.Banned,
        TelegramClientMemberStatus.Unknown,
        -> false
    }
}
