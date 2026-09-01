package com.qixuan.channelvideoflow.domain.video

import com.qixuan.channelvideoflow.model.video.VideoTag
import java.text.Normalizer
import java.util.Locale

data class Utf16TextRange(
    val offset: Int,
    val length: Int,
)

data class HashtagParseResult(
    val tags: List<VideoTag>,
    val invalidEntityCount: Int,
)

object HashtagParser {
    fun parse(
        text: String,
        hashtagEntityRanges: List<Utf16TextRange>,
    ): HashtagParseResult {
        var invalidEntityCount = 0
        val displayNames = if (hashtagEntityRanges.isNotEmpty()) {
            hashtagEntityRanges.mapNotNull { range ->
                safeUtf16Slice(text, range).also { slice ->
                    if (slice == null) invalidEntityCount += 1
                }
            }
        } else {
            fallbackDisplayNames(text)
        }

        val unique = linkedMapOf<String, VideoTag>()
        displayNames.forEach { displayName ->
            normalize(displayName)?.let { normalizedName ->
                unique.putIfAbsent(
                    normalizedName,
                    VideoTag(normalizedName = normalizedName, displayName = displayName),
                )
            }
        }
        return HashtagParseResult(unique.values.toList(), invalidEntityCount)
    }

    fun normalize(displayName: String): String? {
        val compatibilityNormalized = Normalizer.normalize(displayName, Normalizer.Form.NFKC)
        val withoutHash = compatibilityNormalized.removePrefix("#")
        if (withoutHash.isEmpty()) return null
        return withoutHash.lowercase(Locale.ROOT)
    }

    private fun safeUtf16Slice(text: String, range: Utf16TextRange): String? {
        if (range.offset < 0 || range.length <= 0) return null
        val end = range.offset.toLong() + range.length.toLong()
        if (end > text.length || end < range.offset) return null
        val endIndex = end.toInt()
        if (range.offset > 0 && range.offset < text.length &&
            text[range.offset].isLowSurrogate() && text[range.offset - 1].isHighSurrogate()
        ) {
            return null
        }
        if (endIndex > 0 && endIndex < text.length &&
            text[endIndex - 1].isHighSurrogate() && text[endIndex].isLowSurrogate()
        ) {
            return null
        }
        return text.substring(range.offset, endIndex)
    }

    private fun fallbackDisplayNames(text: String): List<String> = buildList {
        var index = 0
        while (index < text.length) {
            val codePoint = text.codePointAt(index)
            if (codePoint != HASH_CODE_POINT || !hasValidBoundaryBefore(text, index)) {
                index += Character.charCount(codePoint)
                continue
            }

            val start = index
            index += 1
            val bodyStart = index
            while (index < text.length) {
                val bodyCodePoint = text.codePointAt(index)
                if (!isTagBodyCodePoint(bodyCodePoint)) break
                index += Character.charCount(bodyCodePoint)
            }
            if (index > bodyStart) add(text.substring(start, index))
        }
    }

    private fun hasValidBoundaryBefore(text: String, index: Int): Boolean {
        if (index == 0) return true
        return !isTagBodyCodePoint(text.codePointBefore(index))
    }

    private fun isTagBodyCodePoint(codePoint: Int): Boolean =
        codePoint == UNDERSCORE_CODE_POINT ||
            Character.isLetterOrDigit(codePoint) ||
            when (Character.getType(codePoint)) {
                Character.NON_SPACING_MARK.toInt(),
                Character.COMBINING_SPACING_MARK.toInt(),
                Character.ENCLOSING_MARK.toInt(),
                -> true
                else -> false
            }

    private const val HASH_CODE_POINT = '#'.code
    private const val UNDERSCORE_CODE_POINT = '_'.code
}
