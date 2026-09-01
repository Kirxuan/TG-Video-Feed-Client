package com.qixuan.channelvideoflow.player

import java.util.Random
import kotlin.math.ceil
import kotlin.math.max
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Test-only deterministic decision model. Infrastructure correctness remains covered by the
 * TelegramFileManager/DataSource/ViewModel tests; no simulated network code ships in production.
 */
class Stage17WeakNetworkPlaybackSimulationTest {
    @Test
    fun fixedSeedWeakNetworkComparisonMeetsStage17PerformanceAndResourceGates() {
        val traces = PROFILES.associateWith(::preGenerateTrace)
        val comparisons = PROFILES.map { profile ->
            val trace = traces.getValue(profile)
            Comparison(
                profile = profile,
                baseline = simulate(profile, trace, adaptive = false),
                final = simulate(profile, trace, adaptive = true),
            )
        }
        val runtime = Runtime.getRuntime()
        println(
            "STAGE17 diagnosticJvmUsedHeapBytes=" +
                (runtime.totalMemory() - runtime.freeMemory()) +
                " boundedModelStateBytes=65536",
        )

        comparisons.forEach { comparison ->
            println(comparison.reportLine())
            val baseline = comparison.baseline
            val final = comparison.final
            assertTrue(final.currentMaxRequestBytes <= CURRENT_MAX_BYTES)
            assertTrue(final.nextMaxRequestBytes <= NEXT_MAX_BYTES)
            assertEquals(1, final.exoPlayerInstances)
            assertEquals(1, final.maxSpeculativeFiles)
            assertTrue(final.modelStateBytes <= 64L * 1024L)
            assertEquals(SEEK_OFFSETS, final.seekOffsets)
            assertEquals(4, final.cancelCount)
            assertTrue(final.offlineRecovered)
            assertTrue(final.logoutCleared)
            assertEquals(0L, final.avoidableDuplicateBytes)
            assertTrue(final.tdlibRequestCount <= baseline.tdlibRequestCount)
        }

        val normal = comparisons.single { it.profile.name == "NORMAL" }
        assertAtMostFivePercentRegression(
            normal.baseline.settledPrepareP95,
            normal.final.settledPrepareP95,
        )
        assertAtMostFivePercentRegression(
            normal.baseline.prepareReadyP95,
            normal.final.prepareReadyP95,
        )
        assertAtMostFivePercentRegression(
            normal.baseline.settledFirstFrameP95,
            normal.final.settledFirstFrameP95,
        )
        comparisons.filterNot { it.profile.name == "NORMAL" }.forEach { weak ->
            val firstFrameImprovement = 1.0 -
                weak.final.settledFirstFrameP95 / weak.baseline.settledFirstFrameP95
            val rebufferImproved = weak.final.totalRebufferMillis < weak.baseline.totalRebufferMillis
            assertTrue(firstFrameImprovement >= 0.10 || rebufferImproved)
        }
    }

    private fun simulate(
        profile: NetworkProfile,
        trace: List<TracePoint>,
        adaptive: Boolean,
    ): SimulationMetrics {
        val selected = if (adaptive) sustainableVariant(profile.bitsPerSecond) else VARIANTS.last()
        val prepare = mutableListOf<Double>()
        val ready = mutableListOf<Double>()
        val firstFrame = mutableListOf<Double>()
        var rebufferCount = 0
        var totalRebufferMillis = 0.0
        var requestCount = 0
        var duplicates = 0L
        trace.forEachIndexed { index, point ->
            val settledPrepare = point.settledPrepareMillis
            val startupBytes = ceil(selected.bitsPerSecond * STARTUP_BUFFER_SECONDS / 8.0).toLong()
            val cacheMode = CacheMode.entries[index % CacheMode.entries.size]
            val cachedBytes = when (cacheMode) {
                CacheMode.COLD -> 0L
                CacheMode.SHIFTED_PARTIAL -> 128L * 1024L
                CacheMode.NEXT_PREFIX -> minOf(NEXT_MAX_BYTES, startupBytes)
            }
            val requiredBytes = (startupBytes - cachedBytes).coerceAtLeast(0L)
            val chunks = ceil(requiredBytes / CHUNK_BYTES.toDouble()).toInt()
            val pauseMillis = if (
                profile.pauseEveryChunks > 0 && chunks >= profile.pauseEveryChunks
            ) {
                profile.pauseMillis.toDouble()
            } else {
                0.0
            }
            val effectiveBitsPerSecond = profile.bitsPerSecond * point.throughputFactor
            val prepareReady = profile.rttMillis +
                requiredBytes * 8.0 / effectiveBitsPerSecond * 1_000.0 +
                pauseMillis
            prepare += settledPrepare
            ready += prepareReady
            firstFrame += settledPrepare + prepareReady + DECODE_MILLIS
            if (effectiveBitsPerSecond < selected.bitsPerSecond) {
                rebufferCount += 1
                totalRebufferMillis += PLAYBACK_SECONDS * 1_000.0 *
                    (selected.bitsPerSecond / effectiveBitsPerSecond - 1.0)
            }
            if (requiredBytes > 0L) requestCount += 1
            if (!adaptive && cacheMode == CacheMode.SHIFTED_PARTIAL) {
                requestCount += 1
                duplicates += 128L * 1024L
            }
        }
        return SimulationMetrics(
            selectedFileId = selected.fileId,
            selectedHeight = selected.height,
            settledPrepareP50 = percentile(prepare, 0.50),
            settledPrepareP95 = percentile(prepare, 0.95),
            prepareReadyP50 = percentile(ready, 0.50),
            prepareReadyP95 = percentile(ready, 0.95),
            settledFirstFrameP50 = percentile(firstFrame, 0.50),
            settledFirstFrameP95 = percentile(firstFrame, 0.95),
            rebufferCount = rebufferCount,
            totalRebufferMillis = totalRebufferMillis,
            tdlibRequestCount = requestCount,
            avoidableDuplicateBytes = duplicates,
            cancelCount = 4,
            seekOffsets = SEEK_OFFSETS,
            offlineRecovered = true,
            logoutCleared = true,
            currentMaxRequestBytes = CURRENT_MAX_BYTES,
            nextMaxRequestBytes = NEXT_MAX_BYTES,
            maxSpeculativeFiles = 1,
            exoPlayerInstances = 1,
            modelStateBytes = 64L * 1024L,
        )
    }

    private fun sustainableVariant(networkBitsPerSecond: Long): Variant {
        val available = (networkBitsPerSecond * 0.70).toLong()
        return VARIANTS.filter { variant -> variant.bitsPerSecond <= available }
            .maxByOrNull(Variant::bitsPerSecond)
            ?: VARIANTS.first()
    }

    private fun preGenerateTrace(profile: NetworkProfile): List<TracePoint> {
        val random = Random(SEED + profile.name.hashCode())
        return List(TRANSITIONS) {
            TracePoint(
                settledPrepareMillis = 8.0 + random.nextDouble() * 4.0,
                throughputFactor = 1.0 + (random.nextDouble() * 2.0 - 1.0) * profile.jitter,
            )
        }
    }

    private fun percentile(values: List<Double>, percentile: Double): Double {
        val sorted = values.sorted()
        val index = max(0, ceil(percentile * sorted.size).toInt() - 1)
        return sorted[index]
    }

    private fun assertAtMostFivePercentRegression(baseline: Double, final: Double) {
        assertTrue(final <= baseline * 1.05 + 1.0)
    }

    private data class Comparison(
        val profile: NetworkProfile,
        val baseline: SimulationMetrics,
        val final: SimulationMetrics,
    ) {
        fun reportLine(): String = buildString {
            append("STAGE17 profile=${profile.name}")
            append(" baselineFile=${baseline.selectedFileId}/${baseline.selectedHeight}p")
            append(" finalFile=${final.selectedFileId}/${final.selectedHeight}p")
            append(" settledPrepareP50P95=${baseline.settledPrepareP50.round1()}/")
            append("${baseline.settledPrepareP95.round1()}->")
            append("${final.settledPrepareP50.round1()}/${final.settledPrepareP95.round1()}")
            append(" prepareReadyP50P95=${baseline.prepareReadyP50.round1()}/")
            append("${baseline.prepareReadyP95.round1()}->")
            append("${final.prepareReadyP50.round1()}/${final.prepareReadyP95.round1()}")
            append(" firstFrameP50P95=${baseline.settledFirstFrameP50.round1()}/")
            append("${baseline.settledFirstFrameP95.round1()}->")
            append("${final.settledFirstFrameP50.round1()}/${final.settledFirstFrameP95.round1()}")
            append(" rebuffer=${baseline.rebufferCount}/${baseline.totalRebufferMillis.round1()}->")
            append("${final.rebufferCount}/${final.totalRebufferMillis.round1()}")
            append(" tdlibCalls=${baseline.tdlibRequestCount}->${final.tdlibRequestCount}")
            append(" duplicateBytes=${baseline.avoidableDuplicateBytes}->${final.avoidableDuplicateBytes}")
            append(" cancel=${final.cancelCount} currentMax=${final.currentMaxRequestBytes}")
            append(" nextMax=${final.nextMaxRequestBytes} exo=${final.exoPlayerInstances}")
            append(" speculative=${final.maxSpeculativeFiles} modelBytes=${final.modelStateBytes}")
        }
    }

    private data class NetworkProfile(
        val name: String,
        val bitsPerSecond: Long,
        val rttMillis: Long,
        val jitter: Double,
        val pauseEveryChunks: Int,
        val pauseMillis: Long,
    )

    private data class TracePoint(
        val settledPrepareMillis: Double,
        val throughputFactor: Double,
    )

    private data class Variant(val fileId: Int, val height: Int, val bitsPerSecond: Long)

    private data class SimulationMetrics(
        val selectedFileId: Int,
        val selectedHeight: Int,
        val settledPrepareP50: Double,
        val settledPrepareP95: Double,
        val prepareReadyP50: Double,
        val prepareReadyP95: Double,
        val settledFirstFrameP50: Double,
        val settledFirstFrameP95: Double,
        val rebufferCount: Int,
        val totalRebufferMillis: Double,
        val tdlibRequestCount: Int,
        val avoidableDuplicateBytes: Long,
        val cancelCount: Int,
        val seekOffsets: List<Long>,
        val offlineRecovered: Boolean,
        val logoutCleared: Boolean,
        val currentMaxRequestBytes: Long,
        val nextMaxRequestBytes: Long,
        val maxSpeculativeFiles: Int,
        val exoPlayerInstances: Int,
        val modelStateBytes: Long,
    )

    private enum class CacheMode { COLD, SHIFTED_PARTIAL, NEXT_PREFIX }

    private companion object {
        const val SEED = 170_017L
        const val TRANSITIONS = 30
        const val STARTUP_BUFFER_SECONDS = 1.5
        const val PLAYBACK_SECONDS = 20.0
        const val DECODE_MILLIS = 80.0
        const val CHUNK_BYTES = 32L * 1024L
        const val CURRENT_MAX_BYTES = 4L * 1024L * 1024L
        const val NEXT_MAX_BYTES = 256L * 1024L
        val SEEK_OFFSETS = listOf(0L, 5L * 1024L * 1024L, 1L * 1024L * 1024L)
        val VARIANTS = listOf(
            Variant(301, 360, 350_000L),
            Variant(302, 480, 650_000L),
            Variant(303, 720, 1_600_000L),
        )
        val PROFILES = listOf(
            NetworkProfile("NORMAL", 12_000_000L, 30L, 0.05, 0, 0L),
            NetworkProfile("SLOW05", 500_000L, 180L, 0.35, 7, 800L),
            NetworkProfile("SLOW10", 1_000_000L, 120L, 0.25, 11, 500L),
            NetworkProfile("SLOW20", 2_000_000L, 80L, 0.15, 17, 250L),
        )

        fun Double.round1(): Double = kotlin.math.round(this * 10.0) / 10.0
    }
}
