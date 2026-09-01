package com.qixuan.channelvideoflow.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "video_tags",
    primaryKeys = ["chat_id", "message_id", "normalized_tag_name"],
    foreignKeys = [
        ForeignKey(
            entity = VideoEntity::class,
            parentColumns = ["chat_id", "message_id"],
            childColumns = ["chat_id", "message_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["normalized_name"],
            childColumns = ["normalized_tag_name"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["chat_id", "message_id"]),
        Index(value = ["normalized_tag_name"]),
    ],
)
data class VideoTagCrossRef(
    @ColumnInfo(name = "chat_id")
    val chatId: Long,
    @ColumnInfo(name = "message_id")
    val messageId: Long,
    @ColumnInfo(name = "normalized_tag_name")
    val normalizedTagName: String,
    @ColumnInfo(name = "display_name")
    val displayName: String,
)
