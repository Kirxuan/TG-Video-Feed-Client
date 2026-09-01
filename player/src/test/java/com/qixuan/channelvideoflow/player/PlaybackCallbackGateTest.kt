package com.qixuan.channelvideoflow.player

import com.qixuan.channelvideoflow.model.video.VideoKey
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackCallbackGateTest {
    @Test
    fun rebindingTheSameVideoRejectsCallbacksFromTheOlderGeneration() {
        val gate = PlaybackCallbackGate()
        val key = VideoKey(chatId = 1L, messageId = 10L)
        val oldBinding = gate.begin(key)
        val currentBinding = gate.begin(key)

        assertFalse(gate.accepts(oldBinding))
        assertTrue(gate.accepts(currentBinding))
    }

    @Test
    fun lateCallbacksFromOldBindingCannotAffectCurrentTransition() {
        val gate = PlaybackCallbackGate()
        val oldKey = VideoKey(chatId = 1L, messageId = 10L)
        val currentKey = VideoKey(chatId = 1L, messageId = 11L)
        val oldBinding = gate.begin(oldKey)
        val currentBinding = gate.begin(currentKey)

        assertFalse(gate.accepts(oldBinding))
        assertTrue(gate.accepts(currentBinding))
        assertFalse(gate.accepts(currentBinding.copy(key = oldKey)))

        gate.invalidate()
        assertFalse(gate.accepts(currentBinding))
    }
}
