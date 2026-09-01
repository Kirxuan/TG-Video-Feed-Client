package com.qixuan.channelvideoflow.domain.media

import kotlin.math.ceil

enum class PlaybackRiskState {
    STARTUP,
    PLAYING,
    SEEK,
    REBUFFER,
}

enum class PlaybackRiskAction {
    KEEP,
    DOWNGRADE,
    UPGRADE,
    ABANDON_REQUEST,
    ACCUMULATE_RESERVOIR,
}

enum class PlaybackRiskReason {
    STABLE,
    UNRELIABLE_ESTIMATE,
    CURRENT_PLAYBACK_PRIORITY,
    STARVATION_DEADLINE,
    UNSUSTAINABLE_BITRATE,
    UPGRADE_STABILITY,
    SWITCH_COOLDOWN,
    DEVICE_PRESSURE,
    SAME_REPRESENTATION,
}

data class PlaybackRiskInput(
    val fastThroughputBitsPerSecond: Long?,
    val slowThroughputBitsPerSecond: Long?,
    val timeToFirstByteP50Millis: Long?,
    val timeToFirstByteP90Millis: Long?,
    val currentBufferedDurationMillis: Long,
    val bufferSlopeSecondsPerSecond: Double,
    val currentRepresentationBitrate: Long,
    val candidatePeakBitrate: Long,
    val minimumRepresentationBitrate: Long,
    val currentRequestDownloadedBytes: Long,
    val currentRequestRemainingBytes: Long,
    val nextPlayableSeconds: Double,
    val playbackState: PlaybackRiskState,
    val isMetered: Boolean,
    val isPowerSaver: Boolean,
    val hasThermalPressure: Boolean,
    val hasStoragePressure: Boolean,
    val networkGeneration: Long,
    val nowMillis: Long,
)

data class PlaybackRiskDecision(
    val action: PlaybackRiskAction,
    val reason: PlaybackRiskReason,
    val predictedCompletionMillis: Long?,
    val starvationDeadlineMillis: Long,
    val maximumSafeBitrate: Long,
)

/** Pure deterministic hybrid throughput + buffer + request-deadline controller. */
class PlaybackRiskController(
    private val upgradeStableWindowsRequired: Int = 4,
    private val minimumSwitchIntervalMillis: Long = 12_000L,
    private val upgradeBufferMillis: Long = 25_000L,
    private val starvationSafetyMillis: Long = 1_200L,
) {
    private var networkGeneration: Long? = null
    private var upgradeStableWindows = 0
    private var lastSwitchMillis = Long.MIN_VALUE

    fun evaluate(input: PlaybackRiskInput): PlaybackRiskDecision {
        if (networkGeneration != input.networkGeneration) {
            networkGeneration = input.networkGeneration
            upgradeStableWindows = 0
            lastSwitchMillis = Long.MIN_VALUE
        }
        val fast = input.fastThroughputBitsPerSecond?.takeIf { it > 0L }
        val slow = input.slowThroughputBitsPerSecond?.takeIf { it > 0L }
        val conservative = listOfNotNull(fast, slow).minOrNull()
        val maximumSafe = conservative
            ?.let { (it * if (input.isMetered) 0.68 else 0.75).toLong() }
            ?.coerceAtLeast(input.minimumRepresentationBitrate)
            ?: input.minimumRepresentationBitrate
        val completion = predictCompletionMillis(input, conservative)
        val starvationDeadline = (
            input.currentBufferedDurationMillis - starvationSafetyMillis
        ).coerceAtLeast(0L)
        val urgentState = input.playbackState != PlaybackRiskState.PLAYING

        if (conservative == null || fast == null || slow == null) {
            upgradeStableWindows = 0
            return decision(
                if (input.currentRepresentationBitrate > input.minimumRepresentationBitrate) {
                    markSwitch(input.nowMillis, PlaybackRiskAction.DOWNGRADE)
                } else {
                    PlaybackRiskAction.ACCUMULATE_RESERVOIR
                },
                PlaybackRiskReason.UNRELIABLE_ESTIMATE,
                completion,
                starvationDeadline,
                maximumSafe,
            )
        }
        if (completion != null && completion >= starvationDeadline && input.currentRequestRemainingBytes > 0L) {
            upgradeStableWindows = 0
            val total = input.currentRequestDownloadedBytes.saturatedAdd(input.currentRequestRemainingBytes)
            val mostlyUnfinished = input.currentRequestDownloadedBytes * 2L < total
            val action = if (mostlyUnfinished && input.currentRepresentationBitrate > input.minimumRepresentationBitrate) {
                PlaybackRiskAction.ABANDON_REQUEST
            } else if (input.currentRepresentationBitrate > input.minimumRepresentationBitrate) {
                PlaybackRiskAction.DOWNGRADE
            } else {
                PlaybackRiskAction.ACCUMULATE_RESERVOIR
            }
            return decision(
                markSwitch(input.nowMillis, action),
                PlaybackRiskReason.STARVATION_DEADLINE,
                completion,
                starvationDeadline,
                maximumSafe,
            )
        }
        if (
            input.currentRepresentationBitrate > input.minimumRepresentationBitrate &&
            (input.currentRepresentationBitrate > maximumSafe || input.bufferSlopeSecondsPerSecond < -0.25)
        ) {
            upgradeStableWindows = 0
            return decision(
                markSwitch(input.nowMillis, PlaybackRiskAction.DOWNGRADE),
                PlaybackRiskReason.UNSUSTAINABLE_BITRATE,
                completion,
                starvationDeadline,
                maximumSafe,
            )
        }
        if (urgentState) {
            upgradeStableWindows = 0
            return decision(
                PlaybackRiskAction.ACCUMULATE_RESERVOIR,
                PlaybackRiskReason.CURRENT_PLAYBACK_PRIORITY,
                completion,
                starvationDeadline,
                maximumSafe,
            )
        }
        if (input.candidatePeakBitrate <= input.currentRepresentationBitrate) {
            upgradeStableWindows = 0
            return decision(
                PlaybackRiskAction.KEEP,
                PlaybackRiskReason.SAME_REPRESENTATION,
                completion,
                starvationDeadline,
                maximumSafe,
            )
        }
        val pressured = input.isPowerSaver || input.hasThermalPressure || input.hasStoragePressure
        val carriesCandidate =
            fast >= (input.candidatePeakBitrate * 1.35).toLong() &&
                slow >= (input.candidatePeakBitrate * 1.50).toLong()
        val stableBuffer = input.currentBufferedDurationMillis >= upgradeBufferMillis &&
            input.bufferSlopeSecondsPerSecond >= 0.0
        if (pressured || !carriesCandidate || !stableBuffer) {
            upgradeStableWindows = 0
            return decision(
                PlaybackRiskAction.KEEP,
                if (pressured) PlaybackRiskReason.DEVICE_PRESSURE else PlaybackRiskReason.UPGRADE_STABILITY,
                completion,
                starvationDeadline,
                maximumSafe,
            )
        }
        if (input.nowMillis - lastSwitchMillis < minimumSwitchIntervalMillis) {
            upgradeStableWindows = 0
            return decision(
                PlaybackRiskAction.KEEP,
                PlaybackRiskReason.SWITCH_COOLDOWN,
                completion,
                starvationDeadline,
                maximumSafe,
            )
        }
        upgradeStableWindows += 1
        return if (upgradeStableWindows >= upgradeStableWindowsRequired) {
            upgradeStableWindows = 0
            decision(
                markSwitch(input.nowMillis, PlaybackRiskAction.UPGRADE),
                PlaybackRiskReason.STABLE,
                completion,
                starvationDeadline,
                maximumSafe,
            )
        } else {
            decision(
                PlaybackRiskAction.KEEP,
                PlaybackRiskReason.UPGRADE_STABILITY,
                completion,
                starvationDeadline,
                maximumSafe,
            )
        }
    }

    private fun predictCompletionMillis(input: PlaybackRiskInput, throughput: Long?): Long? {
        if (input.currentRequestRemainingBytes <= 0L) return 0L
        throughput ?: return null
        val transferMillis = ceil(
            input.currentRequestRemainingBytes.toDouble() * 8_000.0 / throughput.toDouble(),
        ).toLong()
        return transferMillis.saturatedAdd(input.timeToFirstByteP90Millis ?: input.timeToFirstByteP50Millis ?: 0L)
    }

    private fun markSwitch(nowMillis: Long, action: PlaybackRiskAction): PlaybackRiskAction {
        if (action == PlaybackRiskAction.DOWNGRADE || action == PlaybackRiskAction.UPGRADE) {
            lastSwitchMillis = nowMillis
        }
        return action
    }

    private fun decision(
        action: PlaybackRiskAction,
        reason: PlaybackRiskReason,
        completion: Long?,
        starvation: Long,
        maximumSafe: Long,
    ) = PlaybackRiskDecision(action, reason, completion, starvation, maximumSafe)

    private fun Long.saturatedAdd(value: Long): Long =
        if (this > Long.MAX_VALUE - value) Long.MAX_VALUE else this + value
}
