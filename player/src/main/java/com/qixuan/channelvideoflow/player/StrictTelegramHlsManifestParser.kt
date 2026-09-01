package com.qixuan.channelvideoflow.player

import com.qixuan.channelvideoflow.domain.media.TelegramInternalResourceHandle
import com.qixuan.channelvideoflow.domain.media.TelegramInternalResourceKind
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

internal enum class TelegramHlsPlaylistKind {
    MASTER,
    MEDIA,
}

internal data class TelegramHlsByteRange(
    val length: Long,
    val offset: Long?,
)

internal data class TelegramHlsSegment(
    val durationSeconds: Double,
    val resource: TelegramInternalResourceHandle,
    val byteRange: TelegramHlsByteRange?,
)

internal data class TelegramHlsInitialization(
    val resource: TelegramInternalResourceHandle,
    val byteRange: TelegramHlsByteRange?,
)

internal data class ParsedTelegramHlsManifest(
    val sanitizedBytes: ByteArray,
    val kind: TelegramHlsPlaylistKind,
    val segments: List<TelegramHlsSegment>,
    val initializations: List<TelegramHlsInitialization>,
    val targetDurationSeconds: Int?,
    val hasEndList: Boolean,
)

internal class TelegramHlsManifestException(message: String) : Exception(message)

/** Strictly validates Telegram playlists and rewrites only pre-registered mtproto resources. */
internal object StrictTelegramHlsManifestParser {
    const val MAX_MANIFEST_BYTES = 256 * 1024
    private const val MAX_LINES = 4_096
    private const val MAX_LINE_CHARS = 2_048
    private val allowedTags = setOf(
        "#EXTM3U",
        "#EXT-X-VERSION",
        "#EXT-X-INDEPENDENT-SEGMENTS",
        "#EXT-X-STREAM-INF",
        "#EXTINF",
        "#EXT-X-BYTERANGE",
        "#EXT-X-MAP",
        "#EXT-X-TARGETDURATION",
        "#EXT-X-MEDIA-SEQUENCE",
        "#EXT-X-ENDLIST",
        "#EXT-X-PLAYLIST-TYPE",
        "#EXT-X-DISCONTINUITY",
    )

    fun parseAndRewrite(
        bytes: ByteArray,
        expectedKind: TelegramHlsPlaylistKind,
        allowedResources: Map<Int, TelegramInternalResourceHandle>,
    ): ParsedTelegramHlsManifest {
        if (bytes.isEmpty() || bytes.size > MAX_MANIFEST_BYTES) {
            throw TelegramHlsManifestException("manifest size is outside the safe range")
        }
        val text = decodeUtf8(bytes)
        if ('\u0000' in text) throw TelegramHlsManifestException("manifest contains NUL")
        val lines = text.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        if (lines.size > MAX_LINES || lines.any { line -> line.length > MAX_LINE_CHARS }) {
            throw TelegramHlsManifestException("manifest line limits exceeded")
        }
        if (lines.firstOrNull()?.trim() != "#EXTM3U") {
            throw TelegramHlsManifestException("missing EXTM3U header")
        }

        val output = ArrayList<String>(lines.size)
        val segments = mutableListOf<TelegramHlsSegment>()
        val initializations = mutableListOf<TelegramHlsInitialization>()
        var pendingDuration: Double? = null
        var pendingByteRange: TelegramHlsByteRange? = null
        var pendingStream = false
        var targetDuration: Int? = null
        var hasEndList = false

        lines.forEachIndexed { index, original ->
            val line = original.trim()
            if (line.isEmpty()) {
                output += ""
                return@forEachIndexed
            }
            rejectTraversal(line)
            if (line.startsWith('#')) {
                val tag = line.substringBefore(':')
                if (tag.startsWith("#EXT") && tag !in allowedTags) {
                    throw TelegramHlsManifestException("unsupported HLS tag")
                }
                when (tag) {
                    "#EXT-X-STREAM-INF" -> {
                        if (expectedKind != TelegramHlsPlaylistKind.MASTER) {
                            throw TelegramHlsManifestException("recursive playlist is not allowed")
                        }
                        parsePositiveAttribute(line, "BANDWIDTH")
                        pendingStream = true
                    }
                    "#EXTINF" -> {
                        if (expectedKind != TelegramHlsPlaylistKind.MEDIA || pendingDuration != null) {
                            throw TelegramHlsManifestException("invalid EXTINF placement")
                        }
                        pendingDuration = line.substringAfter(':').substringBefore(',').toDoubleOrNull()
                            ?.takeIf { duration -> duration.isFinite() && duration > 0.0 && duration <= 3_600.0 }
                            ?: throw TelegramHlsManifestException("invalid EXTINF duration")
                    }
                    "#EXT-X-BYTERANGE" -> {
                        if (expectedKind != TelegramHlsPlaylistKind.MEDIA) {
                            throw TelegramHlsManifestException("BYTERANGE is only valid in media playlists")
                        }
                        pendingByteRange = parseByteRange(line.substringAfter(':'))
                    }
                    "#EXT-X-MAP" -> {
                        if (expectedKind != TelegramHlsPlaylistKind.MEDIA) {
                            throw TelegramHlsManifestException("MAP is only valid in media playlists")
                        }
                        val rewritten = rewriteMap(line, allowedResources)
                        output += rewritten.first
                        initializations += rewritten.second
                        return@forEachIndexed
                    }
                    "#EXT-X-TARGETDURATION" -> {
                        targetDuration = line.substringAfter(':').toIntOrNull()
                            ?.takeIf { value -> value in 1..3_600 }
                            ?: throw TelegramHlsManifestException("invalid target duration")
                    }
                    "#EXT-X-MEDIA-SEQUENCE" -> if (
                        line.substringAfter(':').toLongOrNull()?.let { it >= 0L } != true
                    ) {
                        throw TelegramHlsManifestException("invalid media sequence")
                    }
                    "#EXT-X-ENDLIST" -> hasEndList = true
                }
                output += line
                return@forEachIndexed
            }

            val requiredKind = when {
                pendingStream -> TelegramInternalResourceKind.HLS_MANIFEST
                pendingDuration != null -> TelegramInternalResourceKind.HLS_MEDIA
                else -> throw TelegramHlsManifestException("unbound URI at line ${index + 1}")
            }
            val resource = resolveTelegramResource(line, allowedResources, requiredKind)
            output += TelegramHlsUriCodec.uriFor(resource)
            pendingDuration?.let { duration ->
                segments += TelegramHlsSegment(duration, resource, pendingByteRange)
            }
            pendingDuration = null
            pendingByteRange = null
            pendingStream = false
        }
        if (pendingStream || pendingDuration != null || pendingByteRange != null) {
            throw TelegramHlsManifestException("incomplete playlist entry")
        }
        if (expectedKind == TelegramHlsPlaylistKind.MEDIA && segments.isEmpty()) {
            throw TelegramHlsManifestException("media playlist contains no segments")
        }
        return ParsedTelegramHlsManifest(
            sanitizedBytes = output.joinToString("\n").toByteArray(StandardCharsets.UTF_8),
            kind = expectedKind,
            segments = segments,
            initializations = initializations,
            targetDurationSeconds = targetDuration,
            hasEndList = hasEndList,
        )
    }

    private fun decodeUtf8(bytes: ByteArray): String = try {
        StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (_: Exception) {
        throw TelegramHlsManifestException("manifest is not valid UTF-8")
    }

    private fun resolveTelegramResource(
        value: String,
        allowed: Map<Int, TelegramInternalResourceHandle>,
        requiredKind: TelegramInternalResourceKind,
    ): TelegramInternalResourceHandle {
        if (!value.startsWith("mtproto:") || value.count { it == ':' } != 1) {
            throw TelegramHlsManifestException("external or unknown URI is forbidden")
        }
        val fileId = value.removePrefix("mtproto:").toIntOrNull()
            ?.takeIf { it > 0 }
            ?: throw TelegramHlsManifestException("invalid Telegram resource reference")
        val handle = allowed[fileId]
            ?: throw TelegramHlsManifestException("unregistered Telegram resource reference")
        if (handle.kind != requiredKind) {
            throw TelegramHlsManifestException("playlist nesting or resource kind mismatch")
        }
        return handle
    }

    private fun rewriteMap(
        line: String,
        allowed: Map<Int, TelegramInternalResourceHandle>,
    ): Pair<String, TelegramHlsInitialization> {
        val match = Regex("(?:^|,)URI=\\\"([^\\\"]+)\\\"").find(line.substringAfter(':'))
            ?: throw TelegramHlsManifestException("MAP URI is required")
        val rawUri = match.groupValues[1]
        val handle = resolveTelegramResource(
            rawUri,
            allowed,
            TelegramInternalResourceKind.HLS_MEDIA,
        )
        val rewritten = TelegramHlsUriCodec.uriFor(handle)
        val range = Regex("(?:^|,)BYTERANGE=\"([^\"]+)\"")
            .find(line.substringAfter(':'))
            ?.groupValues
            ?.get(1)
            ?.let(::parseByteRange)
        return line.replace("URI=\"$rawUri\"", "URI=\"$rewritten\"") to
            TelegramHlsInitialization(handle, range)
    }

    private fun parseByteRange(value: String): TelegramHlsByteRange {
        val parts = value.split('@')
        if (parts.size !in 1..2) throw TelegramHlsManifestException("invalid byte range")
        val length = parts[0].toLongOrNull()?.takeIf { it > 0L }
            ?: throw TelegramHlsManifestException("invalid byte range length")
        val offset = parts.getOrNull(1)?.toLongOrNull()?.takeIf { it >= 0L }
        if (parts.size == 2 && offset == null) throw TelegramHlsManifestException("invalid byte range offset")
        return TelegramHlsByteRange(length, offset)
    }

    private fun parsePositiveAttribute(line: String, name: String): Long {
        val value = Regex("(?:^|,)$name=([0-9]+)(?:,|$)")
            .find(line.substringAfter(':'))
            ?.groupValues
            ?.get(1)
            ?.toLongOrNull()
        return value?.takeIf { it > 0L }
            ?: throw TelegramHlsManifestException("missing or invalid $name")
    }

    private fun rejectTraversal(value: String) {
        val lower = value.lowercase()
        if (
            ".." in value || '\\' in value || "%2e" in lower || "%2f" in lower ||
            "%5c" in lower
        ) {
            throw TelegramHlsManifestException("path traversal is forbidden")
        }
    }

    fun playableSecondsForPrefix(segments: List<TelegramHlsSegment>, byteCeiling: Long): Double {
        var coveredEnd = 0L
        var seconds = 0.0
        segments.forEach { segment ->
            val range = segment.byteRange ?: return seconds
            val offset = range.offset ?: coveredEnd
            val end = offset.saturatedAdd(range.length)
            if (end > byteCeiling) return seconds
            coveredEnd = end
            seconds += segment.durationSeconds
        }
        return seconds
    }

    fun bytesForPlayableSeconds(segments: List<TelegramHlsSegment>, targetSeconds: Double): Long? {
        if (targetSeconds <= 0.0) return 0L
        var seconds = 0.0
        var coveredEnd = 0L
        segments.forEach { segment ->
            val range = segment.byteRange ?: return null
            val offset = range.offset ?: coveredEnd
            coveredEnd = offset.saturatedAdd(range.length)
            seconds += segment.durationSeconds
            if (seconds >= targetSeconds) return coveredEnd
        }
        return coveredEnd.takeIf { seconds > 0.0 }
    }

    private fun Long.saturatedAdd(value: Long): Long =
        if (this > Long.MAX_VALUE - value) Long.MAX_VALUE else this + value
}
