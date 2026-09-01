package com.qixuan.channelvideoflow.domain.media

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A conservative, session-local estimate derived only from TDLib network progress. */
data class NetworkThroughputEstimate(
    val availableBitsPerSecond: Long,
    val medianBitsPerSecond: Long,
    val reliableSampleCount: Int,
    val revision: Long,
    val fastBitsPerSecond: Long = medianBitsPerSecond,
    val slowBitsPerSecond: Long = medianBitsPerSecond,
    val timeToFirstByteP50Millis: Long? = null,
    val timeToFirstByteP90Millis: Long? = null,
    val network: NetworkTransport = NetworkTransport.OFFLINE,
    val networkGeneration: Long = 0L,
)

/** One active TDLib network-progress sample. Cached/local bytes must be marked and are ignored. */
data class TdLibNetworkTransferSample(
    val bytes: Long,
    val durationNanos: Long,
    val contextRevision: Long,
    val timeToFirstByteNanos: Long? = null,
    val isCached: Boolean = false,
    val isActiveNetworkDownload: Boolean = true,
)

/**
 * Shared domain boundary for Telegram transfer samples, playback feedback and AUTO quality reads.
 * It never persists network identifiers or measurements.
 */
interface StreamingNetworkMetricsRepository {
    val estimate: StateFlow<NetworkThroughputEstimate?>
    val contextRevision: Long

    fun recordNetworkProgress(bytes: Long, durationNanos: Long, contextRevision: Long)
    fun recordTdLibTransfer(sample: TdLibNetworkTransferSample) {
        if (!sample.isCached && sample.isActiveNetworkDownload) {
            recordNetworkProgress(sample.bytes, sample.durationNanos, sample.contextRevision)
        }
    }
    fun resetNetworkContext(network: NetworkTransport, networkGeneration: Long)
    fun resetSession()
    fun onRebuffer()
}

/** Bounded robust estimator: fast downgrade, delayed upgrade and a 0.7 safety factor. */
class StreamingNetworkMetricsEstimator : StreamingNetworkMetricsRepository {
    private val lock = Any()
    private val samples = ArrayDeque<ThroughputSample>(MAX_SAMPLES)
    private val ttfbSamplesMillis = ArrayDeque<Long>(MAX_TTFB_SAMPLES)
    private val mutableEstimate = MutableStateFlow<NetworkThroughputEstimate?>(null)
    override val estimate: StateFlow<NetworkThroughputEstimate?> = mutableEstimate.asStateFlow()

    @Volatile
    private var revision = 0L
    private var networkKey: NetworkKey? = null
    private var estimateRevision = 0L
    private var upgradeStreak = 0
    private var downgradeStreak = 0
    private var fastEwma: Double? = null
    private var slowEwma: Double? = null

    override val contextRevision: Long
        get() = revision

    override fun recordNetworkProgress(
        bytes: Long,
        durationNanos: Long,
        contextRevision: Long,
    ) {
        recordTdLibTransfer(
            TdLibNetworkTransferSample(
                bytes = bytes,
                durationNanos = durationNanos,
                contextRevision = contextRevision,
            ),
        )
    }

    override fun recordTdLibTransfer(sample: TdLibNetworkTransferSample) {
        val bytes = sample.bytes
        val durationNanos = sample.durationNanos
        val contextRevision = sample.contextRevision
        if (
            sample.isCached || !sample.isActiveNetworkDownload ||
            contextRevision != revision || bytes < MIN_SAMPLE_BYTES ||
            durationNanos < MIN_NETWORK_SAMPLE_DURATION_NANOS
        ) return
        val bitsPerSecond = saturatedBitsPerSecond(bytes, durationNanos)
        if (bitsPerSecond !in MIN_REASONABLE_BITS_PER_SECOND..MAX_REASONABLE_BITS_PER_SECOND) return
        synchronized(lock) {
            if (contextRevision != revision) return
            sample.timeToFirstByteNanos?.let { ttfbNanos ->
                val millis = (ttfbNanos / 1_000_000L)
                    .coerceIn(MIN_TTFB_MILLIS, MAX_TTFB_MILLIS)
                if (ttfbSamplesMillis.size == MAX_TTFB_SAMPLES) ttfbSamplesMillis.removeFirst()
                ttfbSamplesMillis.addLast(millis)
            }
            val clipped = robustClip(bitsPerSecond)
            fastEwma = ewma(fastEwma, clipped.toDouble(), FAST_EWMA_ALPHA)
            slowEwma = ewma(slowEwma, clipped.toDouble(), SLOW_EWMA_ALPHA)
            if (samples.size == MAX_SAMPLES) samples.removeFirst()
            samples.addLast(ThroughputSample(clipped, bytes))
            if (samples.size < MIN_RELIABLE_SAMPLES) return
            val median = weightedMedian(samples)
            val conservativeEwma = minOf(
                fastEwma ?: median.toDouble(),
                slowEwma ?: median.toDouble(),
            )
            val candidate = (conservativeEwma * BANDWIDTH_FRACTION).toLong().coerceAtLeast(1L)
            val current = mutableEstimate.value
            val latestCandidate =
                (clipped.toDouble() * BANDWIDTH_FRACTION).toLong().coerceAtLeast(1L)
            if (
                current != null &&
                latestCandidate < current.availableBitsPerSecond * RAPID_DROP_THRESHOLD
            ) {
                downgradeStreak += 1
                if (downgradeStreak >= RAPID_DROP_SAMPLE_STREAK) {
                    upgradeStreak = 0
                    downgradeStreak = 0
                    publish(latestCandidate, median)
                    return
                }
            } else {
                downgradeStreak = 0
            }
            when {
                current == null -> publish(
                    (median.toDouble() * BANDWIDTH_FRACTION).toLong().coerceAtLeast(1L),
                    median,
                )
                candidate < current.availableBitsPerSecond * DOWNGRADE_THRESHOLD -> {
                    upgradeStreak = 0
                    publish(candidate, median)
                }
                candidate > current.availableBitsPerSecond * UPGRADE_THRESHOLD -> {
                    upgradeStreak += 1
                    if (upgradeStreak >= UPGRADE_SAMPLE_STREAK) {
                        publish(candidate, median)
                        upgradeStreak = 0
                    } else {
                        publish(current.availableBitsPerSecond, median)
                    }
                }
                else -> {
                    upgradeStreak = 0
                    publish(current.availableBitsPerSecond, median)
                }
            }
        }
    }

    override fun resetNetworkContext(network: NetworkTransport, networkGeneration: Long) {
        val next = NetworkKey(network, networkGeneration)
        synchronized(lock) {
            if (networkKey == next) return
            networkKey = next
            resetLocked()
        }
    }

    override fun resetSession() {
        synchronized(lock) {
            networkKey = null
            resetLocked()
        }
    }

    override fun onRebuffer() {
        synchronized(lock) {
            val current = mutableEstimate.value ?: return
            samples.clear()
            upgradeStreak = 0
            downgradeStreak = 0
            estimateRevision += 1L
            mutableEstimate.value = current.copy(
                availableBitsPerSecond =
                    (current.availableBitsPerSecond * REBUFFER_REDUCTION).toLong().coerceAtLeast(1L),
                reliableSampleCount = 0,
                revision = estimateRevision,
            )
        }
    }

    private fun resetLocked() {
        revision += 1L
        samples.clear()
        ttfbSamplesMillis.clear()
        fastEwma = null
        slowEwma = null
        upgradeStreak = 0
        downgradeStreak = 0
        mutableEstimate.value = null
    }

    private fun publish(candidate: Long, median: Long) {
        estimateRevision += 1L
        val key = networkKey
        val sortedTtfb = ttfbSamplesMillis.sorted()
        mutableEstimate.value = NetworkThroughputEstimate(
            availableBitsPerSecond = candidate,
            medianBitsPerSecond = median,
            reliableSampleCount = samples.size,
            revision = estimateRevision,
            fastBitsPerSecond = fastEwma?.toLong()?.coerceAtLeast(1L) ?: median,
            slowBitsPerSecond = slowEwma?.toLong()?.coerceAtLeast(1L) ?: median,
            timeToFirstByteP50Millis = percentile(sortedTtfb, 0.50),
            timeToFirstByteP90Millis = percentile(sortedTtfb, 0.90),
            network = key?.network ?: NetworkTransport.OFFLINE,
            networkGeneration = key?.generation ?: 0L,
        )
    }

    private fun robustClip(value: Long): Long {
        if (samples.size < MIN_RELIABLE_SAMPLES) return value
        val median = weightedMedian(samples)
        return value.coerceIn(
            (median * OUTLIER_LOW_FACTOR).toLong().coerceAtLeast(MIN_REASONABLE_BITS_PER_SECOND),
            (median * OUTLIER_HIGH_FACTOR).toLong().coerceAtMost(MAX_REASONABLE_BITS_PER_SECOND),
        )
    }

    private data class NetworkKey(val network: NetworkTransport, val generation: Long)
    private data class ThroughputSample(val bitsPerSecond: Long, val bytes: Long)

    private companion object {
        const val MIN_RELIABLE_SAMPLES = 3
        const val MAX_SAMPLES = 9
        const val MAX_TTFB_SAMPLES = 20
        const val MIN_SAMPLE_BYTES = 32L * 1024L
        const val MIN_NETWORK_SAMPLE_DURATION_NANOS = 2_000_000L
        const val MIN_REASONABLE_BITS_PER_SECOND = 16_000L
        const val MAX_REASONABLE_BITS_PER_SECOND = 500_000_000L
        const val BANDWIDTH_FRACTION = 0.70
        const val DOWNGRADE_THRESHOLD = 0.85
        const val RAPID_DROP_THRESHOLD = 0.70
        const val RAPID_DROP_SAMPLE_STREAK = 2
        const val UPGRADE_THRESHOLD = 1.25
        const val UPGRADE_SAMPLE_STREAK = 3
        const val REBUFFER_REDUCTION = 0.60
        const val FAST_EWMA_ALPHA = 0.50
        const val SLOW_EWMA_ALPHA = 0.15
        const val OUTLIER_LOW_FACTOR = 0.25
        const val OUTLIER_HIGH_FACTOR = 4.0
        const val MIN_TTFB_MILLIS = 1L
        const val MAX_TTFB_MILLIS = 30_000L

        fun ewma(previous: Double?, sample: Double, alpha: Double): Double =
            previous?.let { alpha * sample + (1.0 - alpha) * it } ?: sample

        fun percentile(sorted: List<Long>, quantile: Double): Long? {
            if (sorted.isEmpty()) return null
            val index = kotlin.math.ceil((sorted.size - 1) * quantile).toInt()
            return sorted[index.coerceIn(0, sorted.lastIndex)]
        }

        fun saturatedBitsPerSecond(bytes: Long, durationNanos: Long): Long {
            if (bytes <= 0L || durationNanos <= 0L) return 0L
            val multiplier = 8_000_000_000L
            return if (bytes > Long.MAX_VALUE / multiplier) {
                Long.MAX_VALUE
            } else {
                bytes * multiplier / durationNanos
            }
        }

        fun weightedMedian(samples: Collection<ThroughputSample>): Long {
            val sorted = samples.sortedBy(ThroughputSample::bitsPerSecond)
            val totalWeight = sorted.sumOf { sample -> sample.bytes.coerceAtMost(MAX_SAMPLE_WEIGHT) }
            val target = (totalWeight + 1L) / 2L
            var cumulative = 0L
            sorted.forEach { sample ->
                cumulative += sample.bytes.coerceAtMost(MAX_SAMPLE_WEIGHT)
                if (cumulative >= target) return sample.bitsPerSecond
            }
            return sorted.last().bitsPerSecond
        }

        const val MAX_SAMPLE_WEIGHT = 512L * 1024L
    }
}

/** Test/default fallback that never changes AUTO's cold-start network rule. */
object NoOpStreamingNetworkMetricsRepository : StreamingNetworkMetricsRepository {
    private val empty = MutableStateFlow<NetworkThroughputEstimate?>(null)
    override val estimate: StateFlow<NetworkThroughputEstimate?> = empty.asStateFlow()
    override val contextRevision: Long = 0L
    override fun recordNetworkProgress(bytes: Long, durationNanos: Long, contextRevision: Long) = Unit
    override fun recordTdLibTransfer(sample: TdLibNetworkTransferSample) = Unit
    override fun resetNetworkContext(network: NetworkTransport, networkGeneration: Long) = Unit
    override fun resetSession() = Unit
    override fun onRebuffer() = Unit
}
