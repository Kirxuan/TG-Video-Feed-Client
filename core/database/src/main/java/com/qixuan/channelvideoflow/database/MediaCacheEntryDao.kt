package com.qixuan.channelvideoflow.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface MediaCacheEntryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: MediaCacheEntryEntity)

    @Query("SELECT * FROM media_cache_entries WHERE file_id = :fileId LIMIT 1")
    suspend fun get(fileId: Int): MediaCacheEntryEntity?

    @Query(
        """
        SELECT * FROM media_cache_entries
        WHERE cached_bytes > 0
        ORDER BY last_accessed_at ASC, file_id ASC
        """,
    )
    suspend fun getLruEntries(): List<MediaCacheEntryEntity>

    @Query("DELETE FROM media_cache_entries WHERE file_id = :fileId")
    suspend fun delete(fileId: Int)

    @Query("DELETE FROM media_cache_entries")
    suspend fun clear()
}
