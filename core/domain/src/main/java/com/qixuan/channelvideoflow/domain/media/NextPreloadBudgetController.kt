package com.qixuan.channelvideoflow.domain.media

import kotlin.math.ceil

enum class NextPreloadBudgetTier(val ceilingBytes: Long) {
    BLOCKED(0L),
    METADATA_ONLY(0L),
    TWO_MIB(2L * 1024L * 1024L),
    FIVE_MIB(5L * 1024L * 1024L),
    TEN_MIB(10L * 1024L * 1024L),
}

enum class NextPreloadStopReason {
    NONE,
    STARTUP_SEEK_REBUFFER,
    CURRENT_BUFFER_LOW,
    BUFFER_FALLING,
    METERED_DEFAULT_DISABLED,
    DEVICE_PRESSURE,
    METADATA_ONLY,
    UNRELIABLE_BITRATE,
    SEGMENT_EXCEEDS_TIER,
    TARGET_REACHED,
    HARD_LIMIT,
    TARGET_CHANGED,
}

data class NextPreloadSafetySnapshot(
    val playbackState: PlaybackRiskState = PlaybackRiskState.STARTUP,
    val currentBufferedSeconds: Double = 0.0,
    val bufferSlopeSecondsPerSecond: Double = 0.0,
    val fastThroughputBitsPerSecond: Long? = null,
    val slowThroughputBitsPerSecond: Long? = null,
    val timeToFirstByteP90Millis: Long? = null,
    val isMetered: Boolean = true,
    val isMobileNetwork: Boolean = false,
    val isPowerSaver: Boolean = false,
    val hasThermalPressure: Boolean = false,
    val hasStoragePressure: Boolean = false,
    val networkGeneration: Long = 0L,
)

data class HlsPlayableBoundary(
    val playableSeconds: Double,
    val requiredEndOffsetBytes: Long,
)

data class NextPreloadBudgetInput(
    val safety: NextPreloadSafetySnapshot,
    val peakBitrateBitsPerSecond: Long?,
    val cachedCoveredBytes: Long,
    val downloadedNewNetworkBytes: Long,
    val hlsBoundaries: List<HlsPlayableBoundary> = emptyList(),
)

data class NextPreloadBudgetDecision(
    val calculatedTargetSeconds: Double,
    val calculatedTargetBytes: Long,
    val allowedBudgetTier: NextPreloadBudgetTier,
    val remainingNewNetworkBudgetBytes: Long,
    val cachedCoveredBytes: Long,
    val downloadedNewNetworkBytes: Long,
    val canceledBytes: Long = 0L,
    val skippedNextWastedBytes: Long = 0L,
    val currentBufferedSeconds: Double,
    val bufferSlopeSecondsPerSecond: Double,
    val predictedCompletionMillis: Long?,
    val starvationDeadlineMillis: Long,
    val preloadStopReason: NextPreloadStopReason,
)

/** Progressive/HLS duration-first budget with a non-negotiable 10 MiB new-network ceiling. */
object NextPreloadBudgetController {
    const val ABSOLUTE_MAX_BYTES = 10L * 1024L * 1024L
    const val MIN_PROGRESSIVE_BYTES = 256L * 1024L
    const val RANGE_CHUNK_BYTES = 512L * 1024L

    fun evaluate(input: NextPreloadBudgetInput): NextPreloadBudgetDecision {
        val tierAndSeconds = tier(input.safety)
        val tier = tierAndSeconds.first
        val targetSeconds = tierAndSeconds.second
        val deadline = ((input.safety.currentBufferedSeconds - 1.2).coerceAtLeast(0.0) * 1_000.0).toLong()
        val hardConsumed = input.downloadedNewNetworkBytes.coerceAtLeast(0L)
        val stop = blockedReason(input.safety, tier)
        if (tier.ceilingBytes == 0L) {
            return decision(input, targetSeconds, 0L, tier, 0L, deadline, stop)
        }
        val progressiveTarget = input.peakBitrateBitsPerSecond
            ?.takeIf { it > 0L }
            ?.let { bitrate ->
                ceil(bitrate.toDouble() * targetSeconds / 8.0 * 1.25).toLong()
                    .coerceIn(MIN_PROGRESSIVE_BYTES, minOf(tier.ceilingBytes, ABSOLUTE_MAX_BYTES))
            }
        val hlsTarget = input.hlsBoundaries
            .sortedBy(HlsPlayableBoundary::playableSeconds)
            .firstOrNull { boundary ->
                boundary.playableSeconds >= targetSeconds &&
                    boundary.requiredEndOffsetBytes in 1..minOf(tier.ceilingBytes, ABSOLUTE_MAX_BYTES)
            }
            ?.requiredEndOffsetBytes
        val target = when {
            input.hlsBoundaries.isNotEmpty() && hlsTarget == null -> 0L
            hlsTarget != null -> hlsTarget
            progressiveTarget != null -> progressiveTarget
            else -> MIN_PROGRESSIVE_BYTES.coerceAtMost(tier.ceilingBytes)
        }.coerceAtMost(ABSOLUTE_MAX_BYTES)
        val covered = input.cachedCoveredBytes.coerceIn(0L, target)
        val allowedNewTotal = (target - covered).coerceAtLeast(0L)
            .coerceAtMost(ABSOLUTE_MAX_BYTES)
        val remaining = (allowedNewTotal - hardConsumed).coerceAtLeast(0L)
        val completion = predictedCompletionMillis(
            remaining,
            listOfNotNull(
                input.safety.fastThroughputBitsPerSecond,
                input.safety.slowThroughputBitsPerSecond,
            ).minOrNull(),
            input.safety.timeToFirstByteP90Millis,
        )
        val finalStop = when {
            target == 0L && input.hlsBoundaries.isNotEmpty() -> NextPreloadStopReason.SEGMENT_EXCEEDS_TIER
            input.peakBitrateBitsPerSecond == null && input.hlsBoundaries.isEmpty() ->
                NextPreloadStopReason.UNRELIABLE_BITRATE
            remaining == 0L && hardConsumed >= ABSOLUTE_MAX_BYTES -> NextPreloadStopReason.HARD_LIMIT
            remaining == 0L -> NextPreloadStopReason.TARGET_REACHED
            else -> NextPreloadStopReason.NONE
        }
        return decision(input, targetSeconds, target, tier, remaining, deadline, finalStop, completion)
    }

    private fun tier(safety: NextPreloadSafetySnapshot): Pair<NextPreloadBudgetTier, Double> = when {
        safety.playbackState != PlaybackRiskState.PLAYING -> NextPreloadBudgetTier.BLOCKED to 0.0
        safety.isMobileNetwork || safety.isMetered -> NextPreloadBudgetTier.BLOCKED to 0.0
        safety.isPowerSaver || safety.hasThermalPressure || safety.hasStoragePressure ->
            NextPreloadBudgetTier.BLOCKED to 0.0
        safety.currentBufferedSeconds < 8.0 -> NextPreloadBudgetTier.BLOCKED to 0.0
        safety.bufferSlopeSecondsPerSecond < -0.05 -> NextPreloadBudgetTier.BLOCKED to 0.0
        safety.currentBufferedSeconds < 15.0 -> NextPreloadBudgetTier.METADATA_ONLY to 0.0
        safety.currentBufferedSeconds < 25.0 -> NextPreloadBudgetTier.TWO_MIB to 3.0
        safety.currentBufferedSeconds < 35.0 -> NextPreloadBudgetTier.FIVE_MIB to 5.0
        reliable(safety) -> NextPreloadBudgetTier.TEN_MIB to 10.0
        else -> NextPreloadBudgetTier.FIVE_MIB to 5.0
    }

    private fun reliable(safety: NextPreloadSafetySnapshot): Boolean {
        val fast = safety.fastThroughputBitsPerSecond ?: return false
        val slow = safety.slowThroughputBitsPerSecond ?: return false
        val ratio = minOf(fast, slow).toDouble() / maxOf(fast, slow).coerceAtLeast(1L)
        return ratio >= 0.70 && safety.bufferSlopeSecondsPerSecond >= 0.0
    }

    private fun blockedReason(
        safety: NextPreloadSafetySnapshot,
        tier: NextPreloadBudgetTier,
    ): NextPreloadStopReason = when {
        safety.playbackState != PlaybackRiskState.PLAYING -> NextPreloadStopReason.STARTUP_SEEK_REBUFFER
        safety.isMobileNetwork || safety.isMetered -> NextPreloadStopReason.METERED_DEFAULT_DISABLED
        safety.isPowerSaver || safety.hasThermalPressure || safety.hasStoragePressure ->
            NextPreloadStopReason.DEVICE_PRESSURE
        safety.currentBufferedSeconds < 8.0 -> NextPreloadStopReason.CURRENT_BUFFER_LOW
        safety.bufferSlopeSecondsPerSecond < -0.05 -> NextPreloadStopReason.BUFFER_FALLING
        tier == NextPreloadBudgetTier.METADATA_ONLY -> NextPreloadStopReason.METADATA_ONLY
        else -> NextPreloadStopReason.NONE
    }

    private fun predictedCompletionMillis(bytes: Long, throughput: Long?, ttfb: Long?): Long? {
        if (bytes <= 0L) return 0L
        throughput?.takeIf { it > 0L } ?: return null
        return ceil(bytes.toDouble() * 8_000.0 / throughput.toDouble()).toLong()
            .saturatedAdd(ttfb ?: 0L)
    }

    private fun decision(
        input: NextPreloadBudgetInput,
        seconds: Double,
        bytes: Long,
        tier: NextPreloadBudgetTier,
        remaining: Long,
        deadline: Long,
        reason: NextPreloadStopReason,
        completion: Long? = null,
    ) = NextPreloadBudgetDecision(
        calculatedTargetSeconds = seconds,
        calculatedTargetBytes = bytes,
        allowedBudgetTier = tier,
        remainingNewNetworkBudgetBytes = remaining.coerceAtMost(ABSOLUTE_MAX_BYTES),
        cachedCoveredBytes = input.cachedCoveredBytes.coerceAtLeast(0L),
        downloadedNewNetworkBytes = input.downloadedNewNetworkBytes.coerceIn(0L, ABSOLUTE_MAX_BYTES),
        canceledBytes = 0L,
        skippedNextWastedBytes = 0L,
        currentBufferedSeconds = input.safety.currentBufferedSeconds,
        bufferSlopeSecondsPerSecond = input.safety.bufferSlopeSecondsPerSecond,
        predictedCompletionMillis = completion,
        starvationDeadlineMillis = deadline,
        preloadStopReason = reason,
    )

    private fun Long.saturatedAdd(value: Long): Long =
        if (this > Long.MAX_VALUE - value) Long.MAX_VALUE else this + value
}
