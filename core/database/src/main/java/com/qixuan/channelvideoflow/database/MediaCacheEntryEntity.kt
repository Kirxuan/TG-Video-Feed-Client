package com.qixuan.channelvideoflow.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * LRU metadata only. Media bytes and runtime protection pins never enter Room.
 */
@Entity(tableName = "media_cache_entries")
data class MediaCacheEntryEntity(
    @PrimaryKey
    @ColumnInfo(name = "file_id")
    val fileId: Int,
    @ColumnInfo(name = "cached_bytes")
    val cachedBytes: Long,
    @ColumnInfo(name = "last_accessed_at")
    val lastAccessedAtMillis: Long,
)
