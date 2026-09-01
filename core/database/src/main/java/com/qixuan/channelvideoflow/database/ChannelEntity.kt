package com.qixuan.channelvideoflow.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.qixuan.channelvideoflow.model.channel.ChannelAccessState
import com.qixuan.channelvideoflow.model.channel.ChannelScanState
import com.qixuan.channelvideoflow.model.channel.TelegramChannel

@Entity(tableName = "channels")
data class ChannelEntity(
    @PrimaryKey
    @ColumnInfo(name = "chat_id")
    val chatId: Long,
    val title: String,
    val username: String?,
    @ColumnInfo(name = "is_selected")
    val isSelected: Boolean = false,
    @ColumnInfo(name = "is_pinned")
    val isPinned: Boolean = false,
    @ColumnInfo(name = "last_new_message_id")
    val lastNewMessageId: Long? = null,
    @ColumnInfo(name = "oldest_scanned_message_id")
    val oldestScannedMessageId: Long? = null,
    @ColumnInfo(name = "initial_scan_completed")
    val initialScanCompleted: Boolean = false,
    @ColumnInfo(name = "scan_strategy_version", defaultValue = "2")
    val scanStrategyVersion: Int = 2,
    @ColumnInfo(name = "video_search_cursor", defaultValue = "0")
    val videoSearchCursor: Long = 0L,
    @ColumnInfo(name = "video_search_completed", defaultValue = "0")
    val videoSearchCompleted: Boolean = false,
    @ColumnInfo(name = "video_candidate_count", defaultValue = "0")
    val videoCandidateCount: Long = 0L,
    @ColumnInfo(name = "video_search_page_count", defaultValue = "0")
    val videoSearchPageCount: Int = 0,
    @ColumnInfo(name = "approximate_video_count")
    val approximateVideoCount: Int? = null,
    @ColumnInfo(name = "last_sync_time")
    val lastSyncTime: Long? = null,
    @ColumnInfo(name = "access_state")
    val accessState: ChannelAccessState = ChannelAccessState.UNKNOWN,
    @ColumnInfo(name = "scan_state")
    val scanState: ChannelScanState = ChannelScanState.NOT_STARTED,
    @ColumnInfo(name = "scan_paused_by_user")
    val scanPausedByUser: Boolean = false,
    @ColumnInfo(name = "scan_retry_at")
    val scanRetryAt: Long? = null,
    @ColumnInfo(name = "scan_retry_count")
    val scanRetryCount: Int = 0,
    @ColumnInfo(name = "scan_failure_code")
    val scanFailureCode: String? = null,
    @ColumnInfo(name = "scan_failure_detail")
    val scanFailureDetail: Int? = null,
    @ColumnInfo(name = "scanned_message_count")
    val scannedMessageCount: Long = 0,
    @ColumnInfo(name = "scanned_page_count")
    val scannedPageCount: Int = 0,
    @ColumnInfo(name = "duplicate_video_encounter_count")
    val duplicateVideoEncounterCount: Long = 0,
    @ColumnInfo(name = "scan_exception_count")
    val scanExceptionCount: Int = 0,
)

fun ChannelEntity.toModel(): TelegramChannel = TelegramChannel(
    chatId = chatId,
    title = title,
    username = username,
    isSelected = isSelected,
    isPinned = isPinned,
)
