package com.qixuan.channelvideoflow.player

import androidx.media3.common.C
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import androidx.media3.common.PlaybackException

class VideoAudioPolicyTest {
    @Test
    fun mediaAudioRequestsFocusAndUsesMovieContentType() {
        assertEquals(C.USAGE_MEDIA, VideoAudioPolicy.attributes.usage)
        assertEquals(C.AUDIO_CONTENT_TYPE_MOVIE, VideoAudioPolicy.attributes.contentType)
        assertTrue(VideoAudioPolicy.handleAudioFocus)
    }

    @Test
    fun decoderAndNetworkTimeoutFailuresUseExplicitRecoverableCategories() {
        val decoderCodes = listOf(
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
            PlaybackException.ERROR_CODE_DECODING_RESOURCES_RECLAIMED,
        )

        decoderCodes.forEach { code ->
            assertEquals(
                VideoPlaybackFailure.DECODER_UNSUPPORTED,
                mapVideoPlaybackFailure(code, cause = null),
            )
        }
        assertEquals(
            VideoPlaybackFailure.TIMEOUT,
            mapVideoPlaybackFailure(
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
                cause = null,
            ),
        )
    }

    @Test
    fun decoderInitializationNetworkAndTimeoutKeepSanitizedDiagnosticCategories() {
        assertEquals(
            PlaybackFailureDiagnostic.DECODER_INITIALIZATION,
            mapPlaybackFailureDiagnostic(
                PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
                cause = null,
            ),
        )
        assertEquals(
            PlaybackFailureDiagnostic.NETWORK,
            mapPlaybackFailureDiagnostic(
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                cause = null,
            ),
        )
        assertEquals(
            PlaybackFailureDiagnostic.TIMEOUT,
            mapPlaybackFailureDiagnostic(
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
                cause = null,
            ),
        )
    }
}
