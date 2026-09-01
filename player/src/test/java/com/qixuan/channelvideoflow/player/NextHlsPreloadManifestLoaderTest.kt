package com.qixuan.channelvideoflow.player

import com.qixuan.channelvideoflow.domain.media.TelegramFileDeleteResult
import com.qixuan.channelvideoflow.domain.media.TelegramFileGateway
import com.qixuan.channelvideoflow.domain.media.TelegramFileOwnerKind
import com.qixuan.channelvideoflow.domain.media.TelegramFileProtectionLease
import com.qixuan.channelvideoflow.domain.media.TelegramFileRangeLease
import com.qixuan.channelvideoflow.domain.media.TelegramFileRequestPriority
import com.qixuan.channelvideoflow.domain.media.TelegramFileSnapshot
import com.qixuan.channelvideoflow.domain.media.TelegramInternalResourceHandle
import com.qixuan.channelvideoflow.domain.media.TelegramInternalResourceKind
import com.qixuan.channelvideoflow.domain.media.TelegramInternalResourceResolution
import com.qixuan.channelvideoflow.model.video.IndexedVideo
import com.qixuan.channelvideoflow.model.video.TelegramMediaFileReference
import com.qixuan.channelvideoflow.model.video.VideoKey
import com.qixuan.channelvideoflow.model.video.VideoPlaybackVariant
import java.nio.file.Files
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NextHlsPreloadManifestLoaderTest {
    @Test
    fun lowestManifestUsesNextPriorityAndProducesCompleteSegmentBoundaries() {
        val bytes = manifest()
        val path = Files.createTempFile("cvf-next-hls", ".m3u8")
        try {
            Files.write(path, bytes)
            val gateway = FakeGateway(path.toString(), bytes.size.toLong())

            val plan = requireNotNull(
                NextHlsPreloadManifestLoader.load(video(bytes.size.toLong()), gateway, "next", 1_000L),
            )

            assertEquals(201, plan.mediaFileId)
            assertEquals(2, plan.boundaries.size)
            assertEquals(3.0, plan.boundaries[0].playableSeconds, 0.0)
            assertEquals(1_152L, plan.boundaries[0].requiredEndOffsetBytes)
            assertEquals(2_176L, plan.boundaries[1].requiredEndOffsetBytes)
            assertEquals(TelegramFileRequestPriority.NEXT_PRELOAD, gateway.priority)
            assertEquals(TelegramFileOwnerKind.NEXT_PRELOAD, gateway.ownerKind)
            assertFalse(plan.manifestWasCached)

            plan.manifestLease.close()
            plan.session.close()
            assertTrue(gateway.resolutions.isEmpty())
        } finally {
            Files.deleteIfExists(path)
        }
    }

    private fun manifest() = """
        #EXTM3U
        #EXT-X-TARGETDURATION:4
        #EXT-X-MAP:URI="mtproto:201",BYTERANGE="128@0"
        #EXTINF:3,
        #EXT-X-BYTERANGE:1024@128
        mtproto:201
        #EXTINF:4,
        #EXT-X-BYTERANGE:1024@1152
        mtproto:201
        #EXT-X-ENDLIST
    """.trimIndent().toByteArray()

    private fun video(manifestSize: Long) = IndexedVideo(
        key = VideoKey(1L, 2L),
        fileId = 101,
        remoteUniqueId = "direct",
        caption = "",
        supportsStreaming = true,
        fileSize = 4_096L,
        durationSeconds = 7,
        width = 640,
        height = 360,
        publishTime = 0L,
        editTime = null,
        canBeSaved = false,
        tags = emptyList(),
        alternativeVariants = listOf(
            VideoPlaybackVariant(
                fileId = 201,
                remoteUniqueId = "media",
                fileSize = 4_096L,
                width = 640,
                height = 360,
                codec = "h264",
                hlsManifestFile = TelegramMediaFileReference(301, "manifest", manifestSize),
            ),
        ),
    )

    private class FakeGateway(
        private val manifestPath: String,
        private val manifestSize: Long,
    ) : TelegramFileGateway {
        private var counter = 0
        private val owners = mutableMapOf<String, String>()
        val resolutions = mutableMapOf<String, TelegramInternalResourceResolution>()
        var priority: TelegramFileRequestPriority? = null
        var ownerKind: TelegramFileOwnerKind? = null

        override fun currentAccountGeneration(): Long = 3L
        override fun registerInternalResource(
            fileId: Int,
            ownerToken: String,
            kind: TelegramInternalResourceKind,
            expectedSize: Long?,
            referencedResources: Map<Int, TelegramInternalResourceHandle>,
            timeToLiveMillis: Long,
        ): TelegramInternalResourceHandle {
            val token = (++counter).toString(16).padStart(32, '0')
            owners[token] = ownerToken
            resolutions[token] = TelegramInternalResourceResolution(
                fileId,
                kind,
                expectedSize,
                referencedResources,
            )
            return TelegramInternalResourceHandle(3L, token, kind)
        }

        override fun resolveInternalResource(
            accountGeneration: Long,
            opaqueToken: String,
        ) = resolutions[opaqueToken].takeIf { accountGeneration == 3L }

        override fun revokeInternalResources(ownerToken: String) {
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
            this.priority = priority
            this.ownerKind = ownerKind
            return object : TelegramFileRangeLease {
                override val fileId = fileId
                override val offset = offset
                override val length = length
                override fun awaitAvailable(timeoutMillis: Long) = snapshot()
                override fun updatePriority(priority: TelegramFileRequestPriority) = Unit
                override fun close() = Unit
            }
        }

        override fun currentSnapshot(fileId: Int): TelegramFileSnapshot? = null
        override fun observeFile(fileId: Int): Flow<TelegramFileSnapshot> = emptyFlow()
        override fun pinFile(fileId: Int, ownerToken: String, ownerKind: TelegramFileOwnerKind) =
            object : TelegramFileProtectionLease {
                override val fileId = fileId
                override val ownerKind = ownerKind
                override fun close() = Unit
            }
        override fun protectedFileIds(): Set<Int> = resolutions.values.map { it.fileId }.toSet()
        override suspend fun deleteCachedFile(fileId: Int) = TelegramFileDeleteResult.DELETED
        override fun release(ownerToken: String) = Unit

        private fun snapshot() = TelegramFileSnapshot(
            fileId = 301,
            size = manifestSize,
            expectedSize = manifestSize,
            localPath = manifestPath,
            canBeDownloaded = true,
            isDownloadingActive = false,
            isDownloadingCompleted = true,
            downloadOffset = 0L,
            downloadedPrefixSize = manifestSize,
            downloadedSize = manifestSize,
        )
    }
}
