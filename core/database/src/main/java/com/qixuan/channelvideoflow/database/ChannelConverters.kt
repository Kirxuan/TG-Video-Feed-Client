package com.qixuan.channelvideoflow.database

import androidx.room.TypeConverter
import com.qixuan.channelvideoflow.model.channel.ChannelAccessState
import com.qixuan.channelvideoflow.model.channel.ChannelScanState

internal class ChannelConverters {
    @TypeConverter
    fun channelAccessStateToString(value: ChannelAccessState): String = value.name

    @TypeConverter
    fun stringToChannelAccessState(value: String): ChannelAccessState =
        ChannelAccessState.valueOf(value)

    @TypeConverter
    fun channelScanStateToString(value: ChannelScanState): String = value.name

    @TypeConverter
    fun stringToChannelScanState(value: String): ChannelScanState =
        ChannelScanState.valueOf(value)
}
