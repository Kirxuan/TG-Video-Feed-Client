package com.qixuan.channelvideoflow.player

import com.qixuan.channelvideoflow.domain.media.HlsPlayableBoundary
import com.qixuan.channelvideoflow.domain.media.TelegramFileGateway
import com.qixuan.channelvideoflow.domain.media.TelegramFileOwnerKind
import com.qixuan.channelvideoflow.domain.media.TelegramFileRangeLease
import com.qixuan.channelvideoflow.domain.media.TelegramFileRequestPriority
import com.qixuan.channelvideoflow.model.video.IndexedVideo
import java.io.RandomAccessFile

internal data class NextHlsPreloadPlan(
    val session: TelegramHlsPlaybackSession,
    val manifestLease: TelegramFileRangeLease,
    val mediaFileId: Int,
    val boundaries: List<HlsPlayableBoundary>,
    val manifestBytes: Long,
    val manifestWasCached: Boolean,
)

/** Loads only the lowest HLS media playlist through a low-priority bounded TDLib request. */
internal object NextHlsPreloadManifestLoader {
    fun load(
        video: IndexedVideo,
        gateway: TelegramFileGateway,
        ownerToken: String,
        timeoutMillis: Long,
    ): NextHlsPreloadPlan? {
        val session = TelegramHlsPlaybackSession.create(video, gateway) ?: return null
        var lease: TelegramFileRangeLease? = null
        return try {
            val manifestUri = session.masterBytes.decodeToString()
                .lineSequence()
                .firstOrNull { it.startsWith("${TelegramHlsUriCodec.SCHEME}://") }
                ?: error("synthetic HLS master has no variant")
            val parsedUri = TelegramHlsUriCodec.parse(manifestUri)
                ?: error("invalid registered manifest URI")
            val resolution = gateway.resolveInternalResource(
                parsedUri.accountGeneration,
                parsedUri.opaqueToken,
            ) ?: error("registered manifest expired")
            val size = resolution.expectedSize
                ?.takeIf { it in 1..StrictTelegramHlsManifestParser.MAX_MANIFEST_BYTES.toLong() }
                ?: error("HLS manifest size is unavailable")
            val cachedBefore = gateway.currentSnapshot(resolution.fileId)?.covers(0L, size) == true
            lease = gateway.acquireRange(
                fileId = resolution.fileId,
                offset = 0L,
                length = size,
                priority = TelegramFileRequestPriority.NEXT_PRELOAD,
                ownerToken = "$ownerToken-manifest",
                ownerKind = TelegramFileOwnerKind.NEXT_PRELOAD,
                readAheadBytes = size,
            )
            val snapshot = lease.awaitAvailable(timeoutMillis)
            if (!snapshot.covers(0L, size)) error("manifest range is incomplete")
            val path = snapshot.localPath ?: error("manifest local path is unavailable")
            val bytes = ByteArray(size.toInt())
            RandomAccessFile(path, "r").use { file ->
                file.seek(0L)
                file.readFully(bytes)
            }
            val parsed = StrictTelegramHlsManifestParser.parseAndRewrite(
                bytes = bytes,
                expectedKind = TelegramHlsPlaylistKind.MEDIA,
                allowedResources = resolution.referencedResources,
            )
            val mediaEntries = resolution.referencedResources.entries
            val mediaFileIds = parsed.segments.map { segment ->
                mediaEntries.firstOrNull { it.value == segment.resource }?.key
                    ?: error("segment resource is outside the registered manifest")
            }.distinct()
            if (mediaFileIds.size != 1) error("one media playlist must resolve to one TDLib file")
            val initEnd = parsed.initializations.maxOfOrNull { initialization ->
                val range = initialization.byteRange ?: return@maxOfOrNull 0L
                (range.offset ?: 0L).saturatedAdd(range.length)
            } ?: 0L
            var previousEnd = 0L
            var seconds = 0.0
            val boundaries = parsed.segments.map { segment ->
                val range = segment.byteRange ?: error("HLS preload requires bounded segments")
                val offset = range.offset ?: previousEnd
                previousEnd = offset.saturatedAdd(range.length)
                seconds += segment.durationSeconds
                HlsPlayableBoundary(
                    playableSeconds = seconds,
                    requiredEndOffsetBytes = maxOf(initEnd, previousEnd),
                )
            }
            NextHlsPreloadPlan(
                session = session,
                manifestLease = lease,
                mediaFileId = mediaFileIds.single(),
                boundaries = boundaries,
                manifestBytes = size,
                manifestWasCached = cachedBefore,
            )
        } catch (_: Exception) {
            lease?.close()
            session.close()
            null
        }
    }

    private fun Long.saturatedAdd(value: Long): Long =
        if (this > Long.MAX_VALUE - value) Long.MAX_VALUE else this + value
}
