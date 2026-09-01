package com.qixuan.channelvideoflow.player

internal data class PlaybackBufferPolicy(
    val candidateId: String,
    val minBufferMillis: Int,
    val maxBufferMillis: Int,
    val bufferForPlaybackMillis: Int,
    val bufferForPlaybackAfterRebufferMillis: Int,
    val prioritizeTimeOverSizeThresholds: Boolean = true,
    val backBufferMillis: Int = 0,
    val targetBufferBytes: Int = TARGET_BUFFER_BYTES_AUTOMATIC,
    val startOrder: PlaybackStartOrder = PlaybackStartOrder.PREPARE_THEN_PLAY,
) {
    init {
        require(candidateId.isNotBlank())
        require(minBufferMillis >= 0)
        require(maxBufferMillis >= minBufferMillis)
        require(bufferForPlaybackMillis in 0..minBufferMillis)
        require(bufferForPlaybackAfterRebufferMillis in 0..minBufferMillis)
        require(backBufferMillis >= 0)
        require(targetBufferBytes == TARGET_BUFFER_BYTES_AUTOMATIC || targetBufferBytes > 0)
    }

    private companion object {
        const val TARGET_BUFFER_BYTES_AUTOMATIC = -1
    }
}
