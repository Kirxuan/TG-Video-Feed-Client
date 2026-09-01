package com.qixuan.channelvideoflow.telegram.chat

import com.qixuan.channelvideoflow.telegram.client.TelegramClientChat
import com.qixuan.channelvideoflow.telegram.client.TelegramClientChatType
import com.qixuan.channelvideoflow.telegram.client.TelegramClientMemberStatus
import com.qixuan.channelvideoflow.telegram.client.TelegramClientSupergroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TdLibChannelMapperTest {
    @Test
    fun onlySupergroupChannelsWithMatchingChannelMetadataAreMapped() {
        assertNull(TdLibChannelMapper.map(chat(TelegramClientChatType.Private), channel()))
        assertNull(TdLibChannelMapper.map(chat(TelegramClientChatType.BasicGroup), channel()))
        assertNull(
            TdLibChannelMapper.map(
                chat(TelegramClientChatType.Supergroup(7, isChannel = false)),
                channel(),
            ),
        )
        assertNull(
            TdLibChannelMapper.map(
                chat(TelegramClientChatType.Supergroup(7, isChannel = true)),
                channel(supergroupId = 8),
            ),
        )
        assertNull(
            TdLibChannelMapper.map(
                chat(TelegramClientChatType.Supergroup(7, isChannel = true)),
                channel(isChannel = false),
            ),
        )

        val mapped = TdLibChannelMapper.map(
            chat(TelegramClientChatType.Supergroup(7, isChannel = true)),
            channel(),
        )

        assertEquals(100L, mapped?.chatId)
        assertEquals("真实频道", mapped?.title)
        assertEquals("channel_name", mapped?.username)
    }

    @Test
    fun leftBannedRestrictedAndNonMemberCreatorAreRejected() {
        val rejected = listOf(
            TelegramClientMemberStatus.Left,
            TelegramClientMemberStatus.Banned,
            TelegramClientMemberStatus.Restricted(isMember = true),
            TelegramClientMemberStatus.Creator(isMember = false),
            TelegramClientMemberStatus.Unknown,
        )

        rejected.forEach { status ->
            assertNull(
                "Status $status must not be exposed as an accessible channel",
                TdLibChannelMapper.map(
                    chat(TelegramClientChatType.Supergroup(7, isChannel = true)),
                    channel(status = status),
                ),
            )
        }
    }

    @Test
    fun memberAdministratorAndMemberCreatorAreAccepted() {
        val accepted = listOf(
            TelegramClientMemberStatus.Member,
            TelegramClientMemberStatus.Administrator,
            TelegramClientMemberStatus.Creator(isMember = true),
        )

        accepted.forEach { status ->
            assertEquals(
                100L,
                TdLibChannelMapper.map(
                    chat(TelegramClientChatType.Supergroup(7, isChannel = true)),
                    channel(status = status),
                )?.chatId,
            )
        }
    }

    private fun chat(type: TelegramClientChatType) = TelegramClientChat(
        chatId = 100,
        title = "真实频道",
        type = type,
    )

    private fun channel(
        supergroupId: Long = 7,
        isChannel: Boolean = true,
        status: TelegramClientMemberStatus = TelegramClientMemberStatus.Member,
    ) = TelegramClientSupergroup(
        supergroupId = supergroupId,
        isChannel = isChannel,
        username = "channel_name",
        memberStatus = status,
    )
}
