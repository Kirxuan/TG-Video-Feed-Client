package com.qixuan.channelvideoflow.player

import android.net.TestUri
import androidx.media3.common.C
import androidx.media3.datasource.DataSpec
import com.qixuan.channelvideoflow.domain.media.TelegramFileDeleteResult
import com.qixuan.channelvideoflow.domain.media.TelegramFileGateway
import com.qixuan.channelvideoflow.domain.media.TelegramFileOwnerKind
import com.qixuan.channelvideoflow.domain.media.TelegramFileProtectionLease
import com.qixuan.channelvideoflow.domain.media.TelegramFileRangeLease
import com.qixuan.channelvideoflow.domain.media.TelegramFileRequestPriority
import com.qixuan.channelvideoflow.domain.media.TelegramFileSnapshot
import com.qixuan.channelvideoflow.domain.media.TelegramFileTimeoutException
import com.qixuan.channelvideoflow.domain.media.TelegramInternalResourceHandle
import com.qixuan.channelvideoflow.domain.media.TelegramInternalResourceKind
import com.qixuan.channelvideoflow.domain.media.TelegramInternalResourceResolution
import com.qixuan.channelvideoflow.model.video.IndexedVideo
import com.qixuan.channelvideoflow.model.video.TelegramMediaFileReference
import com.qixuan.channelvideoflow.model.video.VideoKey
import com.qixuan.channelvideoflow.model.video.VideoPlaybackVariant
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TelegramHlsDataSourceTest {
    @Test
    fun masterManifestMapAndMediaRangesStayInsideRegisteredTdlibFiles() {
        val mediaBytes = ByteArray(4_096) { index -> (index % 251).toByte() }
        val manifestBytes = manifestFixture()
        withGateway(mediaBytes, manifestBytes) { gateway ->
            val session = requireNotNull(TelegramHlsPlaybackSession.create(video(), gateway))
            val master = readAll(source(gateway, session), session.masterUri)
                .toString(StandardCharsets.UTF_8)
            val manifestUri = master.lineSequence().first { it.startsWith("telegram-hls://") }

            val sanitized = readAll(source(gateway, session), manifestUri)
                .toString(StandardCharsets.UTF_8)
            assertTrue("mtproto:" !in sanitized)
            val mediaUri = Regex("telegram-hls://[^\"\\s,]+").find(sanitized)?.value
                ?: error("missing rewritten media URI")
            val mediaSource = source(gateway, session)
            val selected = readAll(mediaSource, mediaUri, position = 128L, length = 1_024L)

            assertArrayEquals(mediaBytes.copyOfRange(128, 1_152), selected)
            assertTrue(gateway.requests.any { it.fileId == 301 && it.offset == 0L })
            assertTrue(gateway.requests.any { it.fileId == 201 && it.offset == 128L && it.length == 1_024L })
            assertTrue(gateway.requests.all { it.ownerKind == TelegramFileOwnerKind.CURRENT_PLAYBACK })
            assertTrue(gateway.requests.all { it.priority == TelegramFileRequestPriority.CURRENT_STARTUP })

            session.close()
            assertTrue(gateway.resolutions.isEmpty())
            assertTrue(gateway.revokedOwners.isNotEmpty())
        }
    }

    @Test
    fun accountGenerationChangeInvalidatesEverySessionUri() {
        withGateway(ByteArray(2_048), manifestFixture()) { gateway ->
            val session = requireNotNull(TelegramHlsPlaybackSession.create(video(), gateway))
            gateway.generation += 1L

            assertThrows(TelegramMediaUnavailableException::class.java) {
                source(gateway, session).open(DataSpec(TestUri(rawValue = session.masterUri)))
            }
        }
    }

    @Test
    fun manifestTimeoutIsTranslatedAndLeaseIsCancelled() {
        withGateway(ByteArray(2_048), manifestFixture()) { gateway ->
            val session = requireNotNull(TelegramHlsPlaybackSession.create(video(), gateway))
            val master = readAll(source(gateway, session), session.masterUri)
                .toString(StandardCharsets.UTF_8)
            val manifestUri = master.lineSequence().first { it.startsWith("telegram-hls://") }
            gateway.timeoutFileIds += 301

            assertThrows(TelegramMediaTimeoutException::class.java) {
                source(gateway, session).open(DataSpec(TestUri(rawValue = manifestUri)))
            }
            assertTrue(gateway.closedLeases > 0)
        }
    }

    @Test
    fun explicitCloseCancelsTheActiveMediaRange() {
        withGateway(ByteArray(2_048), manifestFixture()) { gateway ->
            val session = requireNotNull(TelegramHlsPlaybackSession.create(video(), gateway))
            val master = readAll(source(gateway, session), session.masterUri)
                .toString(StandardCharsets.UTF_8)
            val manifestUri = master.lineSequence().first { it.startsWith("telegram-hls://") }
            val sanitized = readAll(source(gateway, session), manifestUri)
                .toString(StandardCharsets.UTF_8)
            val mediaUri = Regex("telegram-hls://[^\"\\s,]+").find(sanitized)?.value
                ?: error("missing media URI")
            val dataSource = source(gateway, session)
            dataSource.open(DataSpec(TestUri(rawValue = mediaUri), 0L, 128L))
            val before = gateway.closedLeases

            dataSource.close()

            assertEquals(before + 1, gateway.closedLeases)
        }
    }

    private fun source(
        gateway: FakeGateway,
        session: TelegramHlsPlaybackSession,
    ) = TelegramHlsDataSource(
        gateway = gateway,
        session = session,
        rangeSession = PlaybackRangeRequestSession(),
        isMainThread = { false },
    )

    private fun readAll(
        source: TelegramHlsDataSource,
        uri: String,
        position: Long = 0L,
        length: Long = C.LENGTH_UNSET.toLong(),
    ): ByteArray {
        source.open(DataSpec(TestUri(rawValue = uri), position, length))
        return try {
            val output = ArrayList<Byte>()
            val buffer = ByteArray(257)
            while (true) {
                val read = source.read(buffer, 0, buffer.size)
                if (read == C.RESULT_END_OF_INPUT) break
                repeat(read) { output += buffer[it] }
            }
            output.toByteArray()
        } finally {
            source.close()
        }
    }

    private fun manifestFixture(): ByteArray = """
        #EXTM3U
        #EXT-X-VERSION:7
        #EXT-X-TARGETDURATION:4
        #EXT-X-MEDIA-SEQUENCE:0
        #EXT-X-MAP:URI="mtproto:201",BYTERANGE="128@0"
        #EXTINF:3.0,
        #EXT-X-BYTERANGE:1024@128
        mtproto:201
        #EXTINF:4.0,
        #EXT-X-BYTERANGE:1024@1152
        mtproto:201
        #EXT-X-ENDLIST
    """.trimIndent().toByteArray(StandardCharsets.UTF_8)

    private fun video() = IndexedVideo(
        key = VideoKey(10L, 20L),
        fileId = 101,
        remoteUniqueId = "direct",
        caption = "not logged",
        supportsStreaming = true,
        fileSize = 4_096L,
        durationSeconds = 7,
        width = 640,
        height = 360,
        publishTime = 1L,
        editTime = null,
        canBeSaved = false,
        tags = emptyList(),
        alternativeVariants = listOf(
            VideoPlaybackVariant(
                fileId = 201,
                remoteUniqueId = "variant",
                fileSize = 4_096L,
                width = 640,
                height = 360,
                codec = "h264",
                alternativeId = 1L,
                hlsManifestFile = TelegramMediaFileReference(
                    fileId = 301,
                    remoteUniqueId = "manifest",
                    fileSize = manifestFixture().size.toLong(),
                ),
            ),
        ),
    )

    private fun withGateway(
        mediaBytes: ByteArray,
        manifestBytes: ByteArray,
        block: (FakeGateway) -> Unit,
    ) {
        val media = Files.createTempFile("cvf-stage18-media", ".bin")
        val manifest = Files.createTempFile("cvf-stage18-manifest", ".m3u8")
        try {
            Files.write(media, mediaBytes)
            Files.write(manifest, manifestBytes)
            block(FakeGateway(mapOf(201 to media.toString(), 301 to manifest.toString())))
        } finally {
            Files.deleteIfExists(media)
            Files.deleteIfExists(manifest)
        }
    }

    private data class RangeRequest(
        val fileId: Int,
        val offset: Long,
        val length: Long,
        val priority: TelegramFileRequestPriority,
        val ownerKind: TelegramFileOwnerKind,
    )

    private class FakeGateway(
        private val paths: Map<Int, String>,
    ) : TelegramFileGateway {
        var generation = 9L
        private var tokenCounter = 0
        private val owners = mutableMapOf<String, String>()
        val resolutions = mutableMapOf<String, TelegramInternalResourceResolution>()
        val revokedOwners = CopyOnWriteArrayList<String>()
        val requests = CopyOnWriteArrayList<RangeRequest>()
        val timeoutFileIds = mutableSetOf<Int>()
        var closedLeases = 0

        override fun currentAccountGeneration(): Long = generation

        override fun registerInternalResource(
            fileId: Int,
            ownerToken: String,
            kind: TelegramInternalResourceKind,
            expectedSize: Long?,
            referencedResources: Map<Int, TelegramInternalResourceHandle>,
            timeToLiveMillis: Long,
        ): TelegramInternalResourceHandle {
            val token = (++tokenCounter).toString(16).padStart(32, '0')
            owners[token] = ownerToken
            resolutions[token] = TelegramInternalResourceResolution(
                fileId,
                kind,
                expectedSize,
                referencedResources,
            )
            return TelegramInternalResourceHandle(generation, token, kind)
        }

        override fun resolveInternalResource(
            accountGeneration: Long,
            opaqueToken: String,
        ): TelegramInternalResourceResolution? =
            if (accountGeneration == generation) resolutions[opaqueToken] else null

        override fun revokeInternalResources(ownerToken: String) {
            revokedOwners += ownerToken
            owners.filterValues { it == ownerToken }.keys.toList().forEach { token ->
                owners.remove(token)
                resolutions.remove(token)
            }
        }

        override fun acquireRange(
            fileId: Int,
            offset: Long,
            length: Long,
            priority: TelegramFileRequestPriority,
            ownerToken: String,
            ownerKind: TelegramFileOwnerKind,
            readAheadBytes: Long,
        ): TelegramFileRangeLease {
            requests += RangeRequest(fileId, offset, length, priority, ownerKind)
            val path = paths.getValue(fileId)
            return object : TelegramFileRangeLease {
                override val fileId: Int = fileId
                override val offset: Long = offset
                override val length: Long = length
                override fun awaitAvailable(timeoutMillis: Long): TelegramFileSnapshot {
                    if (fileId in timeoutFileIds) throw TelegramFileTimeoutException("fixture timeout")
                    return snapshot(fileId, path)
                }
                override fun updatePriority(priority: TelegramFileRequestPriority) = Unit
                override fun close() {
                    closedLeases += 1
                }
            }
        }

        override fun pinFile(
            fileId: Int,
            ownerToken: String,
            ownerKind: TelegramFileOwnerKind,
        ): TelegramFileProtectionLease = object : TelegramFileProtectionLease {
            override val fileId: Int = fileId
            override val ownerKind: TelegramFileOwnerKind = ownerKind
            override fun close() = Unit
        }

        override fun observeFile(fileId: Int): Flow<TelegramFileSnapshot> = emptyFlow()
        override fun currentSnapshot(fileId: Int): TelegramFileSnapshot? =
            paths[fileId]?.let { snapshot(fileId, it) }
        override fun protectedFileIds(): Set<Int> = resolutions.values.map { it.fileId }.toSet()
        override suspend fun deleteCachedFile(fileId: Int) = TelegramFileDeleteResult.DELETED
        override fun release(ownerToken: String) = Unit

        private fun snapshot(fileId: Int, path: String): TelegramFileSnapshot {
            val size = Files.size(java.nio.file.Path.of(path))
            return TelegramFileSnapshot(
                fileId = fileId,
                size = size,
                expectedSize = size,
                localPath = path,
                canBeDownloaded = true,
                isDownloadingActive = false,
                isDownloadingCompleted = true,
                downloadOffset = 0L,
                downloadedPrefixSize = size,
                downloadedSize = size,
            )
        }
    }
}
