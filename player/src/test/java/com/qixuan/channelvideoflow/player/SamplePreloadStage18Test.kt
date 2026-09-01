package com.qixuan.channelvideoflow.player

import com.qixuan.channelvideoflow.domain.media.NextPreloadBudgetController
import com.qixuan.channelvideoflow.domain.media.NextPreloadBudgetInput
import com.qixuan.channelvideoflow.domain.media.NextPreloadBudgetTier
import com.qixuan.channelvideoflow.domain.media.NextPreloadSafetySnapshot
import com.qixuan.channelvideoflow.domain.media.PlaybackRiskState
import com.qixuan.channelvideoflow.domain.media.TelegramFileDeleteResult
import com.qixuan.channelvideoflow.domain.media.TelegramFileGateway
import com.qixuan.channelvideoflow.domain.media.TelegramFileOwnerKind
import com.qixuan.channelvideoflow.domain.media.TelegramFileProtectionLease
import com.qixuan.channelvideoflow.domain.media.TelegramFileRangeLease
import com.qixuan.channelvideoflow.domain.media.TelegramFileRequestPriority
import com.qixuan.channelvideoflow.domain.media.TelegramFileSnapshot
import com.qixuan.channelvideoflow.model.video.IndexedVideo
import com.qixuan.channelvideoflow.model.video.VideoKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SamplePreloadStage18Test {
    @Test
    fun metadataOnlyTierAdmitsTrackPreparationWithoutAdmittingBlockedPayload() {
        fun decision(bufferedSeconds: Double) = NextPreloadBudgetController.evaluate(
            NextPreloadBudgetInput(
                safety = NextPreloadSafetySnapshot(
                    playbackState = PlaybackRiskState.PLAYING,
                    currentBufferedSeconds = bufferedSeconds,
                    bufferSlopeSecondsPerSecond = 0.1,
                    isMetered = false,
                ),
                peakBitrateBitsPerSecond = 1_000_000L,
                cachedCoveredBytes = 0L,
                downloadedNewNetworkBytes = 0L,
            ),
        )

        val metadata = decision(12.0)
        val blocked = decision(7.0)
        assertEquals(NextPreloadBudgetTier.METADATA_ONLY, metadata.allowedBudgetTier)
        assertEquals(0L, metadata.calculatedTargetBytes)
        assertTrue(metadata.permitsSamplePreload())
        assertEquals(NextPreloadBudgetTier.BLOCKED, blocked.allowedBudgetTier)
        assertFalse(blocked.permitsSamplePreload())
    }

    @Test
    fun featureStaysOffUntilMeasuredP95ImprovesByFifteenPercent() {
        assertFalse(BuildConfig.SAMPLE_QUEUE_PRELOAD_ENABLED)
        val belowGate = SamplePreloadAbEvaluator.evaluate(
            baselineMillis = List(30) { 300L },
            candidateMillis = List(30) { 258L },
            firstFrameCount = 30,
            transitionCount = 30,
            safetyFailures = 0,
        )
        val pass = SamplePreloadAbEvaluator.evaluate(
            baselineMillis = List(30) { 300L },
            candidateMillis = List(30) { 250L },
            firstFrameCount = 30,
            transitionCount = 30,
            safetyFailures = 0,
        )

        assertFalse(belowGate.enableByDefault)
        assertTrue(pass.enableByDefault)
        assertTrue(pass.improvementFraction >= 0.15)
    }

    @Test
    fun missingFirstFrameOrAnyWrongVideoBlackScreenAudioOrCrashFailsTheGate() {
        assertFalse(
            SamplePreloadAbEvaluator.evaluate(
                List(30) { 300L }, List(30) { 100L }, 29, 30, 0,
            ).enableByDefault,
        )
        assertFalse(
            SamplePreloadAbEvaluator.evaluate(
                List(30) { 300L }, List(30) { 100L }, 30, 30, 1,
            ).enableByDefault,
        )
    }

    @Test
    fun exactCommittedTargetCanBeConsumedOnceAndWrongOrCancelledTargetCannot() {
        val gate = SamplePreloadHandoffGate()
        val first = video(1)
        val wrong = video(2)
        gate.register(first)
        assertFalse(gate.take(first))
        assertFalse(gate.commit(wrong))
        assertTrue(gate.commit(first))
        assertFalse(gate.take(wrong))
        assertTrue(gate.take(first))
        assertFalse(gate.take(first))

        gate.register(first)
        assertTrue(gate.cancelUnless(wrong))
        assertFalse(gate.commit(first))
    }

    @Test
    fun sampleDataSourceCannotReadPastStage18dTargetOrUseOversizedChunk() {
        val gateway = RecordingGateway()
        val requestSession = PlaybackRangeRequestSession(preloadOnly = true)
        val capped = CappedNextSampleGateway(
            delegate = gateway,
            payloadFileIds = setOf(10, 11),
            allowedPayloadEnd = 1_024L,
            requestSession = requestSession,
        )
        capped.acquireRange(
            10, 512L, 512L, TelegramFileRequestPriority.NEXT_PRELOAD,
            "owner", TelegramFileOwnerKind.NEXT_PRELOAD, 512L,
        ).close()
        assertEquals(1, gateway.requests)
        assertThrows(IllegalArgumentException::class.java) {
            capped.acquireRange(
                11, 1_024L, 1L, TelegramFileRequestPriority.NEXT_PRELOAD,
                "owner", TelegramFileOwnerKind.NEXT_PRELOAD, 1L,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            capped.acquireRange(
                10, 0L, 513L * 1024L, TelegramFileRequestPriority.NEXT_PRELOAD,
                "owner", TelegramFileOwnerKind.NEXT_PRELOAD, 513L * 1024L,
            )
        }

        requestSession.promoteToCurrent()
        capped.acquireRange(
            10, 1_024L, 512L, TelegramFileRequestPriority.CURRENT_STARTUP,
            "current", TelegramFileOwnerKind.CURRENT_PLAYBACK, 512L,
        ).close()
        assertEquals(2, gateway.requests)
    }

    private fun video(id: Int) = IndexedVideo(
        key = VideoKey(1L, id.toLong()),
        fileId = id,
        remoteUniqueId = "r$id",
        caption = "",
        supportsStreaming = true,
        fileSize = 1_000L,
        durationSeconds = 1,
        width = 640,
        height = 360,
        publishTime = 0L,
        editTime = null,
        canBeSaved = false,
        tags = emptyList(),
    )

    private class RecordingGateway : TelegramFileGateway {
        var requests = 0
        override fun acquireRange(
            fileId: Int,
            offset: Long,
            length: Long,
            priority: TelegramFileRequestPriority,
            ownerToken: String,
            ownerKind: TelegramFileOwnerKind,
            readAheadBytes: Long,
        ): TelegramFileRangeLease {
            requests += 1
            return object : TelegramFileRangeLease {
                override val fileId = fileId
                override val offset = offset
                override val length = length
                override fun awaitAvailable(timeoutMillis: Long): TelegramFileSnapshot = error("unused")
                override fun updatePriority(priority: TelegramFileRequestPriority) = Unit
                override fun close() = Unit
            }
        }
        override fun pinFile(fileId: Int, ownerToken: String, ownerKind: TelegramFileOwnerKind) =
            object : TelegramFileProtectionLease {
                override val fileId = fileId
                override val ownerKind = ownerKind
                override fun close() = Unit
            }
        override fun observeFile(fileId: Int): Flow<TelegramFileSnapshot> = emptyFlow()
        override fun currentSnapshot(fileId: Int): TelegramFileSnapshot? = null
        override fun protectedFileIds(): Set<Int> = emptySet()
        override suspend fun deleteCachedFile(fileId: Int) = TelegramFileDeleteResult.DELETED
        override fun release(ownerToken: String) = Unit
    }
}
