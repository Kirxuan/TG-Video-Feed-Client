package com.qixuan.channelvideoflow.domain.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HashtagParserTest {
    @Test
    fun extractsChineseEntity() {
        assertTags("#学习", ranges(0, 3), "#学习" to "学习")
    }

    @Test
    fun extractsMultipleChineseEntities() {
        val text = "#单片机 #学习 #娱乐"
        assertTags(
            text,
            listOf(
                Utf16TextRange(0, 4),
                Utf16TextRange(5, 3),
                Utf16TextRange(9, 3),
            ),
            "#单片机" to "单片机",
            "#学习" to "学习",
            "#娱乐" to "娱乐",
        )
    }

    @Test
    fun normalizesEnglishWithLocaleRootAndDeduplicates() {
        val text = "#Kotlin #kOtLiN"
        assertTags(
            text,
            listOf(Utf16TextRange(0, 7), Utf16TextRange(8, 7)),
            "#Kotlin" to "kotlin",
        )
    }

    @Test
    fun deduplicatesRepeatedChineseTag() {
        val text = "#学习 #学习"
        assertTags(
            text,
            listOf(Utf16TextRange(0, 3), Utf16TextRange(4, 3)),
            "#学习" to "学习",
        )
    }

    @Test
    fun supportsLettersDigitsUnderscoresAndChinese() {
        assertTags("#ESP32_学习123", ranges(0, 12), "#ESP32_学习123" to "esp32_学习123")
    }

    @Test
    fun usesTdlibUtf16OffsetsAfterEmoji() {
        val text = "😀 #学习"
        assertTags(text, ranges(3, 3), "#学习" to "学习")
    }

    @Test
    fun skipsOutOfBoundsAndSplitSurrogateEntities() {
        val result = HashtagParser.parse(
            text = "😀 #学习",
            hashtagEntityRanges = listOf(
                Utf16TextRange(100, 2),
                Utf16TextRange(1, 3),
            ),
        )

        assertTrue(result.tags.isEmpty())
        assertEquals(2, result.invalidEntityCount)
    }

    @Test
    fun fallsBackWhenHashtagEntitiesAreAbsent() {
        assertTags("内容 #学习", emptyList(), "#学习" to "学习")
    }

    @Test
    fun fallbackRejectsHashEmbeddedInWord() {
        assertTrue(HashtagParser.parse("abc#学习", emptyList()).tags.isEmpty())
    }

    @Test
    fun fallbackRejectsIsolatedOrEmptyHash() {
        assertTrue(HashtagParser.parse("# 说明 #!", emptyList()).tags.isEmpty())
    }

    @Test
    fun nonHashtagEntitiesDoNotDisableFallback() {
        assertTags("加粗内容 #学习", emptyList(), "#学习" to "学习")
    }

    @Test
    fun nfkcNormalizesFullWidthEnglish() {
        assertTags("#Ｋｏｔｌｉｎ", ranges(0, 7), "#Ｋｏｔｌｉｎ" to "kotlin")
    }

    private fun assertTags(
        text: String,
        entityRanges: List<Utf16TextRange>,
        vararg expected: Pair<String, String>,
    ) {
        val result = HashtagParser.parse(text, entityRanges)
        assertEquals(0, result.invalidEntityCount)
        assertEquals(expected.map(Pair<String, String>::second), result.tags.map { it.normalizedName })
        assertEquals(expected.map(Pair<String, String>::first), result.tags.map { it.displayName })
    }

    private fun ranges(offset: Int, length: Int) = listOf(Utf16TextRange(offset, length))
}
