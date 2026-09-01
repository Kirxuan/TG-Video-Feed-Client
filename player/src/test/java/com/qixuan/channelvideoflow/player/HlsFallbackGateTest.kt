package com.qixuan.channelvideoflow.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HlsFallbackGateTest {
    @Test
    fun activeHlsBindingCanFallbackExactlyOnce() {
        val gate = HlsFallbackGate()
        gate.begin(12L, PlaybackSourceKind.HLS)

        assertTrue(gate.tryFallback(12L, PlaybackSourceKind.HLS))
        assertFalse(gate.tryFallback(12L, PlaybackSourceKind.HLS))
        assertFalse(gate.tryFallback(11L, PlaybackSourceKind.HLS))
    }

    @Test
    fun progressiveAndSupersededBindingsCannotEnterHlsFallback() {
        val gate = HlsFallbackGate()
        gate.begin(20L, PlaybackSourceKind.PROGRESSIVE)
        assertFalse(gate.tryFallback(20L, PlaybackSourceKind.HLS))

        gate.begin(21L, PlaybackSourceKind.HLS)
        assertFalse(gate.tryFallback(20L, PlaybackSourceKind.HLS))
        assertFalse(gate.tryFallback(21L, PlaybackSourceKind.PROGRESSIVE))
        gate.clear()
        assertFalse(gate.tryFallback(21L, PlaybackSourceKind.HLS))
    }
}
