package com.qixuan.channelvideoflow.player

import com.qixuan.channelvideoflow.domain.media.TelegramFileGateway
import com.qixuan.channelvideoflow.domain.media.TelegramInternalResourceHandle
import com.qixuan.channelvideoflow.domain.media.TelegramInternalResourceKind
import com.qixuan.channelvideoflow.model.video.IndexedVideo
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlin.math.roundToLong

internal data class ParsedTelegramHlsUri(
    val accountGeneration: Long,
    val kind: String,
    val opaqueToken: String,
)

internal object TelegramHlsUriCodec {
    const val SCHEME = "telegram-hls"
    private const val AUTHORITY = "resource"

    fun uriFor(handle: TelegramInternalResourceHandle): String =
        "$SCHEME://$AUTHORITY/${handle.accountGeneration}/${handle.kind.name.lowercase()}/${handle.opaqueToken}"

    fun masterUri(accountGeneration: Long, sessionToken: String): String =
        "$SCHEME://$AUTHORITY/$accountGeneration/master/$sessionToken"

    fun parse(value: String): ParsedTelegramHlsUri? {
        if (value.any { it == '\\' || it == '\u0000' } || ".." in value) return null
        val uri = runCatching { java.net.URI(value) }.getOrNull() ?: return null
        if (
            uri.scheme != SCHEME || uri.rawAuthority != AUTHORITY || uri.userInfo != null ||
            uri.port != -1 || uri.rawQuery != null || uri.rawFragment != null
        ) {
            return null
        }
        val parts = uri.rawPath.removePrefix("/").split('/')
        if (parts.size != 3 || parts.any(String::isBlank)) return null
        val generation = parts[0].toLongOrNull()?.takeIf { it >= 0L } ?: return null
        if (!parts[1].matches(Regex("[a-z_]+"))) return null
        if (!parts[2].matches(Regex("[a-f0-9]{32}"))) return null
        return ParsedTelegramHlsUri(generation, parts[1], parts[2])
    }
}

internal class TelegramHlsPlaybackSession private constructor(
    private val gateway: TelegramFileGateway,
    val ownerToken: String,
    val accountGeneration: Long,
    val masterUri: String,
    val masterBytes: ByteArray,
) : AutoCloseable {
    override fun close() {
        gateway.revokeInternalResources(ownerToken)
    }

    fun isMaster(uri: ParsedTelegramHlsUri): Boolean =
        uri.accountGeneration == accountGeneration &&
            uri.kind == "master" &&
            masterUri == TelegramHlsUriCodec.masterUri(accountGeneration, uri.opaqueToken)

    companion object {
        fun create(video: IndexedVideo, gateway: TelegramFileGateway): TelegramHlsPlaybackSession? {
            val variants = video.hlsCapableVariants
                .filter { variant ->
                    variant.fileId > 0 && variant.hlsManifestFile?.fileId?.let { it > 0 } == true
                }
                .distinctBy { variant -> variant.fileId to variant.height }
                .sortedWith(compareBy({ it.height }, { it.fileId }))
            if (variants.isEmpty()) return null
            val ownerToken = "hls-current-${UUID.randomUUID()}"
            return try {
                val mediaHandles = variants.associate { variant ->
                    variant.fileId to gateway.registerInternalResource(
                        fileId = variant.fileId,
                        ownerToken = ownerToken,
                        kind = TelegramInternalResourceKind.HLS_MEDIA,
                        expectedSize = variant.fileSize,
                    )
                }
                val manifestHandles = variants.map { variant ->
                    val manifest = requireNotNull(variant.hlsManifestFile)
                    variant to gateway.registerInternalResource(
                        fileId = manifest.fileId,
                        ownerToken = ownerToken,
                        kind = TelegramInternalResourceKind.HLS_MANIFEST,
                        expectedSize = manifest.fileSize,
                        referencedResources = mapOf(
                            variant.fileId to mediaHandles.getValue(variant.fileId),
                        ),
                    )
                }
                val generation = manifestHandles.first().second.accountGeneration
                if (manifestHandles.any { (_, handle) -> handle.accountGeneration != generation }) {
                    error("account generation changed during HLS registration")
                }
                val sessionToken = UUID.randomUUID().toString().replace("-", "")
                val masterUri = TelegramHlsUriCodec.masterUri(generation, sessionToken)
                val master = buildString {
                    append("#EXTM3U\n#EXT-X-VERSION:7\n#EXT-X-INDEPENDENT-SEGMENTS\n")
                    manifestHandles.forEach { (variant, manifestHandle) ->
                        val average = estimateAverageBitrate(video, variant.fileSize, variant.height)
                        val peak = (average * 1.35).roundToLong().coerceAtLeast(average)
                        append("#EXT-X-STREAM-INF:BANDWIDTH=")
                        append(peak)
                        append(",AVERAGE-BANDWIDTH=")
                        append(average)
                        append(",RESOLUTION=")
                        append(variant.width.coerceAtLeast(1))
                        append('x')
                        append(variant.height.coerceAtLeast(1))
                        append('\n')
                        append(TelegramHlsUriCodec.uriFor(manifestHandle))
                        append('\n')
                    }
                }.toByteArray(StandardCharsets.UTF_8)
                TelegramHlsPlaybackSession(
                    gateway = gateway,
                    ownerToken = ownerToken,
                    accountGeneration = generation,
                    masterUri = masterUri,
                    masterBytes = master,
                )
            } catch (error: Throwable) {
                gateway.revokeInternalResources(ownerToken)
                throw error
            }
        }

        private fun estimateAverageBitrate(
            video: IndexedVideo,
            fileSize: Long?,
            height: Int,
        ): Long {
            val measured = fileSize
                ?.takeIf { size -> size > 0L && video.durationSeconds > 0 }
                ?.let { size -> (size * 8L / video.durationSeconds).coerceAtLeast(1L) }
            return (measured ?: when {
                height <= 360 -> 450_000L
                height <= 480 -> 800_000L
                height <= 720 -> 1_500_000L
                else -> 3_000_000L
            }).coerceIn(64_000L, 50_000_000L)
        }
    }
}
