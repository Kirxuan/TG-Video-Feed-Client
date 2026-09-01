package com.qixuan.channelvideoflow.player

import com.qixuan.channelvideoflow.model.video.IndexedVideo
import com.qixuan.channelvideoflow.model.video.VideoKey

/** Pure target gate used to reject late/cancelled sample-preload handoffs. */
internal class SamplePreloadHandoffGate {
    private var target: Target? = null

    @Synchronized
    fun register(video: IndexedVideo) {
        target = Target(video.key, video.playbackFileId, committed = false, consumed = false)
    }

    @Synchronized
    fun commit(video: IndexedVideo): Boolean {
        val current = target ?: return false
        if (!current.matches(video) || current.consumed) return false
        target = current.copy(committed = true)
        return true
    }

    @Synchronized
    fun take(video: IndexedVideo): Boolean {
        val current = target ?: return false
        if (!current.matches(video) || !current.committed || current.consumed) return false
        target = current.copy(consumed = true)
        return true
    }

    @Synchronized
    fun cancelUnless(video: IndexedVideo?): Boolean {
        val current = target ?: return false
        if (video != null && current.matches(video)) return false
        target = null
        return true
    }

    private data class Target(
        val key: VideoKey,
        val fileId: Int,
        val committed: Boolean,
        val consumed: Boolean,
    ) {
        fun matches(video: IndexedVideo): Boolean = key == video.key && fileId == video.playbackFileId
    }
}
