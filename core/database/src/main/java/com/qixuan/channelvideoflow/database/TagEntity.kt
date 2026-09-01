package com.qixuan.channelvideoflow.database

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(
    tableName = "tags",
    primaryKeys = ["normalized_name"],
)
data class TagEntity(
    @ColumnInfo(name = "normalized_name")
    val normalizedName: String,
    @ColumnInfo(name = "canonical_display_name")
    val canonicalDisplayName: String,
)
