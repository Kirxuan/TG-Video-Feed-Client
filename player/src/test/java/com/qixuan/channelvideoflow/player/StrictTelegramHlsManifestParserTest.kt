package com.qixuan.channelvideoflow.player

import com.qixuan.channelvideoflow.domain.media.TelegramInternalResourceHandle
import com.qixuan.channelvideoflow.domain.media.TelegramInternalResourceKind
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class StrictTelegramHlsManifestParserTest {
    private val media = handle('a', TelegramInternalResourceKind.HLS_MEDIA)
    private val manifest = handle('b', TelegramInternalResourceKind.HLS_MANIFEST)

    @Test
    fun mediaPlaylistRewritesOnlyRegisteredTelegramResourcesAndPreservesRanges() {
        val parsed = StrictTelegramHlsManifestParser.parseAndRewrite(
            bytes = fixture(
                """
                #EXTM3U
                #EXT-X-VERSION:7
                #EXT-X-TARGETDURATION:4
                #EXT-X-MEDIA-SEQUENCE:0
                #EXT-X-MAP:URI="mtproto:601",BYTERANGE="128@0"
                #EXTINF:3.0,
                #EXT-X-BYTERANGE:1024@128
                mtproto:601
                #EXTINF:4.0,
                #EXT-X-BYTERANGE:2048@1152
                mtproto:601
                #EXT-X-ENDLIST
                """,
            ),
            expectedKind = TelegramHlsPlaylistKind.MEDIA,
            allowedResources = mapOf(601 to media),
        )

        val sanitized = parsed.sanitizedBytes.toString(StandardCharsets.UTF_8)
        assertFalse("mtproto:" in sanitized)
        assertTrue(TelegramHlsUriCodec.uriFor(media) in sanitized)
        assertEquals(2, parsed.segments.size)
        assertEquals(128L, parsed.initializations.single().byteRange?.length)
        assertEquals(128L, parsed.segments.first().byteRange?.offset)
        assertEquals(7.0, StrictTelegramHlsManifestParser.playableSecondsForPrefix(parsed.segments, 3_200L), 0.0)
        assertEquals(3_200L, StrictTelegramHlsManifestParser.bytesForPlayableSeconds(parsed.segments, 5.0))
        assertEquals(4, parsed.targetDurationSeconds)
        assertTrue(parsed.hasEndList)
    }

    @Test
    fun masterPlaylistAcceptsOnlyRegisteredManifestHandles() {
        val parsed = StrictTelegramHlsManifestParser.parseAndRewrite(
            fixture(
                """
                #EXTM3U
                #EXT-X-STREAM-INF:BANDWIDTH=500000,AVERAGE-BANDWIDTH=420000,RESOLUTION=640x360
                mtproto:701
                """,
            ),
            TelegramHlsPlaylistKind.MASTER,
            mapOf(701 to manifest),
        )

        assertEquals(TelegramHlsPlaylistKind.MASTER, parsed.kind)
        assertTrue(TelegramHlsUriCodec.uriFor(manifest) in parsed.sanitizedBytes.toString(StandardCharsets.UTF_8))
    }

    @Test
    fun externalSchemesTraversalUnknownFilesAndRecursivePlaylistsAreRejected() {
        val forbidden = listOf(
            "http://evil.invalid/segment.ts",
            "https://evil.invalid/segment.ts",
            "file:///private/segment.ts",
            "content://provider/segment",
            "ftp://evil.invalid/segment.ts",
            "../segment.ts",
            "mtproto:999",
        )
        forbidden.forEach { uri ->
            assertThrows(TelegramHlsManifestException::class.java) {
                StrictTelegramHlsManifestParser.parseAndRewrite(
                    fixture("#EXTM3U\n#EXTINF:3.0,\n$uri\n#EXT-X-ENDLIST"),
                    TelegramHlsPlaylistKind.MEDIA,
                    mapOf(601 to media),
                )
            }
        }
        assertThrows(TelegramHlsManifestException::class.java) {
            StrictTelegramHlsManifestParser.parseAndRewrite(
                fixture("#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1\nmtproto:601"),
                TelegramHlsPlaylistKind.MEDIA,
                mapOf(601 to media),
            )
        }
    }

    @Test
    fun malformedUnknownAndOversizedManifestsFailClosed() {
        listOf(
            "not-hls",
            "#EXTM3U\n#EXT-X-KEY:METHOD=AES-128,URI=\"mtproto:601\"",
            "#EXTM3U\n#EXTINF:nope,\nmtproto:601",
            "#EXTM3U\n#EXTINF:3,\nmtproto:601\n#EXT-X-BYTERANGE:10",
        ).forEach { value ->
            assertThrows(TelegramHlsManifestException::class.java) {
                StrictTelegramHlsManifestParser.parseAndRewrite(
                    fixture(value),
                    TelegramHlsPlaylistKind.MEDIA,
                    mapOf(601 to media),
                )
            }
        }
        assertThrows(TelegramHlsManifestException::class.java) {
            StrictTelegramHlsManifestParser.parseAndRewrite(
                ByteArray(StrictTelegramHlsManifestParser.MAX_MANIFEST_BYTES + 1),
                TelegramHlsPlaylistKind.MEDIA,
                emptyMap(),
            )
        }
    }

    @Test
    fun internalUriCodecRejectsForgeryAndGenerationMutation() {
        val valid = TelegramHlsUriCodec.uriFor(media)
        assertEquals(7L, TelegramHlsUriCodec.parse(valid)?.accountGeneration)
        listOf(
            valid.replace("/7/", "/-1/"),
            "$valid?fileId=999",
            valid.replace("resource", "evil.invalid"),
            valid.replace("/hls_media/", "/../"),
            valid.replace("telegram-hls", "https"),
            valid.dropLast(1) + "Z",
        ).forEach { forged -> assertEquals(null, TelegramHlsUriCodec.parse(forged)) }
    }

    private fun fixture(value: String): ByteArray =
        value.trimIndent().trim().toByteArray(StandardCharsets.UTF_8)

    private fun handle(
        token: Char,
        kind: TelegramInternalResourceKind,
    ) = TelegramInternalResourceHandle(
        accountGeneration = 7L,
        opaqueToken = token.toString().repeat(32),
        kind = kind,
    )
}
