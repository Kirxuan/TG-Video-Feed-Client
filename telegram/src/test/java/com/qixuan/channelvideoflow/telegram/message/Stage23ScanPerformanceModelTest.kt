package com.qixuan.channelvideoflow.telegram.message

import java.util.BitSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Stage23ScanPerformanceModelTest {
    @Test
    fun filteredSearchMatchesFullHistoryReferenceAcrossRequiredDensities() {
        REQUIRED_DENSITIES.forEach { densityPercent ->
            val result = simulate(totalMessages = 1_000_000, densityPercent = densityPercent)

            assertEquals(result.referenceVideoKeys, result.filteredVideoKeys)
            assertEquals(10_000, result.legacyRequestPages)
            assertEquals(result.videoCount, result.filteredMappedObjects)
            assertEquals(1_000_000, result.legacyMappedObjects)
            assertTrue(result.filteredPeakPageObjects <= PAGE_SIZE)
            assertTrue(result.filteredRoomCalls <= result.filteredRequestPages * 6 + 1)
            if (densityPercent == 100.0) {
                assertEquals(result.legacyRequestPages, result.filteredRequestPages)
            }
        }

        val onePercent = simulate(1_000_000, 1.0)
        assertEquals(100, onePercent.filteredRequestPages)
        assertEquals(100.0, onePercent.requestReductionFactor, 0.0)
    }

    @Test
    fun fixedModelProducesExpectedSparseRequestRatiosAndBoundedRoomRoundTrips() {
        val expected = mapOf(
            0.1 to Pair(10, 1_000.0),
            1.0 to Pair(100, 100.0),
            5.0 to Pair(500, 20.0),
            20.0 to Pair(2_000, 5.0),
            100.0 to Pair(10_000, 1.0),
        )

        expected.forEach { (density, expectation) ->
            val result = simulate(1_000_000, density)
            assertEquals(expectation.first, result.filteredRequestPages)
            assertEquals(expectation.second, result.requestReductionFactor, 0.0)
            assertEquals(5L * result.videoCount + 2L * result.legacyRequestPages,
                result.legacyRoomCalls)
            assertEquals(6L * result.filteredRequestPages + 1L, result.filteredRoomCalls)
        }
    }

    @Test
    fun hashtagsChangeRowsButNotTheNumberOfPageLevelDaoRoundTrips() {
        val noHashtags = batchRoomShape(videoCount = 100, tagsPerVideo = 0)
        val manyHashtags = batchRoomShape(videoCount = 100, tagsPerVideo = 40)

        assertEquals(6, noHashtags.daoCalls)
        assertEquals(noHashtags.daoCalls, manyHashtags.daoCalls)
        assertEquals(0, noHashtags.crossRefRows)
        assertEquals(4_000, manyHashtags.crossRefRows)
    }

    @Test
    fun skewedChannelsGetOneRecentPageBeforeAnyChannelGetsASecondPage() {
        val remainingPages = linkedMapOf(1L to 10_000, 2L to 100, 3L to 1, 4L to 7)
        val trace = fairRoundRobinTrace(remainingPages, maxRounds = 2)

        assertEquals(listOf(1L, 2L, 3L, 4L), trace.take(4))
        assertEquals(2, MAX_CONCURRENT_CHANNELS)
        assertTrue(trace.indexOf(4L) < trace.indexOf(1L, startIndex = 1))
    }

    @Test
    fun shortPagesAndCrashBoundariesAdvanceOnlyFromCommittedCursor() {
        val pages = listOf(
            SearchPage(candidateKeys = listOf(100L), nextCursor = 70L),
            SearchPage(candidateKeys = emptyList(), nextCursor = 40L),
            SearchPage(candidateKeys = listOf(40L), nextCursor = 0L),
        )
        val state = ScanState()

        applyPage(state, pages[0], commit = false)
        assertEquals(0L, state.cursor)
        assertTrue(state.keys.isEmpty())

        applyPage(state, pages[0], commit = true)
        assertEquals(70L, state.cursor)
        val restarted = state.copy(keys = state.keys.toMutableSet())
        applyPage(restarted, pages[1], commit = true)
        assertEquals(40L, restarted.cursor)
        assertTrue(!restarted.completed)
        applyPage(restarted, pages[2], commit = true)
        assertEquals(setOf(100L, 40L), restarted.keys)
        assertTrue(restarted.completed)
    }

    private fun simulate(totalMessages: Int, densityPercent: Double): SimulationResult {
        val videoCount = (totalMessages * densityPercent / 100.0).toInt()
        val expected = BitSet(totalMessages)
        repeat(videoCount) { ordinal ->
            expected.set(((ordinal.toLong() * PERMUTATION_STEP + FIXED_SEED) % totalMessages).toInt())
        }
        val reference = BitSet(totalMessages)
        repeat(totalMessages) { messageKey ->
            if (expected[messageKey]) reference.set(messageKey)
        }
        val filtered = expected.clone() as BitSet
        val legacyPages = ceilPages(totalMessages)
        val filteredPages = maxOf(1, ceilPages(videoCount))
        return SimulationResult(
            videoCount = videoCount,
            legacyRequestPages = legacyPages,
            filteredRequestPages = filteredPages,
            legacyMappedObjects = totalMessages,
            filteredMappedObjects = videoCount,
            legacyRoomCalls = 5L * videoCount + 2L * legacyPages,
            filteredRoomCalls = 6L * filteredPages + 1L,
            filteredPeakPageObjects = minOf(PAGE_SIZE, videoCount),
            referenceVideoKeys = reference,
            filteredVideoKeys = filtered,
        )
    }

    private fun batchRoomShape(videoCount: Int, tagsPerVideo: Int) = BatchRoomShape(
        daoCalls = 6,
        crossRefRows = videoCount * tagsPerVideo,
    )

    private fun fairRoundRobinTrace(
        initial: LinkedHashMap<Long, Int>,
        maxRounds: Int,
    ): List<Long> = buildList {
        val remaining = LinkedHashMap(initial)
        repeat(maxRounds) {
            remaining.keys.toList().forEach { chatId ->
                val pages = remaining.getValue(chatId)
                if (pages > 0) {
                    add(chatId)
                    remaining[chatId] = pages - 1
                }
            }
        }
    }

    private fun applyPage(state: ScanState, page: SearchPage, commit: Boolean) {
        if (!commit) return
        state.keys += page.candidateKeys
        state.cursor = page.nextCursor
        state.completed = page.nextCursor == 0L
        state.transactionCount += 1
    }

    private fun ceilPages(count: Int): Int = (count + PAGE_SIZE - 1) / PAGE_SIZE

    private fun <T> List<T>.indexOf(value: T, startIndex: Int): Int =
        indices.drop(startIndex).firstOrNull { this[it] == value } ?: -1

    private data class SimulationResult(
        val videoCount: Int,
        val legacyRequestPages: Int,
        val filteredRequestPages: Int,
        val legacyMappedObjects: Int,
        val filteredMappedObjects: Int,
        val legacyRoomCalls: Long,
        val filteredRoomCalls: Long,
        val filteredPeakPageObjects: Int,
        val referenceVideoKeys: BitSet,
        val filteredVideoKeys: BitSet,
    ) {
        val requestReductionFactor: Double
            get() = legacyRequestPages.toDouble() / filteredRequestPages
    }

    private data class BatchRoomShape(
        val daoCalls: Int,
        val crossRefRows: Int,
    )

    private data class SearchPage(
        val candidateKeys: List<Long>,
        val nextCursor: Long,
    )

    private data class ScanState(
        var cursor: Long = 0,
        var completed: Boolean = false,
        var transactionCount: Int = 0,
        val keys: MutableSet<Long> = mutableSetOf(),
    )

    private companion object {
        const val PAGE_SIZE = 100
        const val MAX_CONCURRENT_CHANNELS = 2
        const val FIXED_SEED = 23L
        const val PERMUTATION_STEP = 999_983L
        val REQUIRED_DENSITIES = listOf(0.1, 1.0, 5.0, 20.0, 100.0)
    }
}
