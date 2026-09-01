package com.qixuan.channelvideoflow.telegram.client

import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TdLibChatObjectMapperTest {
    @Test
    fun mapsEveryChatTypeWithoutTreatingBasicGroupsAsChannels() {
        assertEquals(
            TelegramClientChatType.Private,
            TdLibChatObjectMapper.mapChat(chat(TdApi.ChatTypePrivate(10))).type,
        )
        assertEquals(
            TelegramClientChatType.BasicGroup,
            TdLibChatObjectMapper.mapChat(chat(TdApi.ChatTypeBasicGroup(20))).type,
        )
        assertEquals(
            TelegramClientChatType.Secret,
            TdLibChatObjectMapper.mapChat(chat(TdApi.ChatTypeSecret(30, 10))).type,
        )
        assertEquals(
            TelegramClientChatType.Supergroup(supergroupId = 40, isChannel = false),
            TdLibChatObjectMapper.mapChat(chat(TdApi.ChatTypeSupergroup(40, false))).type,
        )
        assertEquals(
            TelegramClientChatType.Supergroup(supergroupId = 50, isChannel = true),
            TdLibChatObjectMapper.mapChat(chat(TdApi.ChatTypeSupergroup(50, true))).type,
        )
    }

    @Test
    fun mapsPrimaryActiveUsernameAndCurrentAccountStatus() {
        val supergroup = TdApi.Supergroup().apply {
            id = 50
            isChannel = true
            usernames = TdApi.Usernames(
                arrayOf("primary", "secondary"),
                emptyArray(),
                "",
                emptyArray(),
            )
            status = TdApi.ChatMemberStatusCreator(false, true)
        }

        assertEquals(
            TelegramClientSupergroup(
                supergroupId = 50,
                isChannel = true,
                username = "primary",
                memberStatus = TelegramClientMemberStatus.Creator(isMember = true),
            ),
            TdLibChatObjectMapper.mapSupergroup(supergroup),
        )

        supergroup.usernames = TdApi.Usernames(emptyArray(), emptyArray(), "", emptyArray())
        assertNull(TdLibChatObjectMapper.mapSupergroup(supergroup).username)
    }

    private fun chat(type: TdApi.ChatType): TdApi.Chat = TdApi.Chat().apply {
        id = 1
        title = "测试聊天"
        this.type = type
    }
}
