package com.qixuan.channelvideoflow.domain.video

import com.qixuan.channelvideoflow.model.video.IndexedVideo
import com.qixuan.channelvideoflow.model.video.VideoFeedOrder
import com.qixuan.channelvideoflow.model.video.VideoKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class VideoPlaybackQueueTest {
    @Test
    fun latestOrderUsesPublishTimeThenCompositeKeyAsStableTieBreakers() {
        val queue = VideoPlaybackQueue(FixedRandom())

        val ordered = queue.rebuild(
            listOf(video(chatId = 1, messageId = 1, publishTime = 10), video(2, 1, 20), video(2, 2, 20)),
            VideoFeedOrder.LATEST,
        )

        assertEquals(listOf(VideoKey(2, 2), VideoKey(2, 1), VideoKey(1, 1)), ordered.map { it.key })
    }

    @Test
    fun randomRoundContainsEveryVideoExactlyOnce() {
        val queue = VideoPlaybackQueue(FixedRandom(0, 0, 0, 0))
        val videos = (1L..5L).map { video(1, it, it) }

        val round = queue.rebuild(videos, VideoFeedOrder.RANDOM)

        assertEquals(videos.map { it.key }.toSet(), round.map { it.key }.toSet())
        assertEquals(videos.size, round.size)
    }

    @Test
    fun nextRandomRoundDoesNotStartWithPreviousRoundLastVideo() {
        val queue = VideoPlaybackQueue(FixedRandom(0, 0, 0, 0, 0, 0))
        val videos = (1L..3L).map { video(1, it, it) }
        val firstRound = queue.rebuild(videos, VideoFeedOrder.RANDOM)
        queue.recordPlayed(firstRound.last().key, VideoFeedOrder.RANDOM)

        val secondRound = queue.rebuild(videos, VideoFeedOrder.RANDOM)

        assertNotEquals(firstRound.last().key, secondRound.first().key)
    }

    @Test
    fun oneVideoMayRepeatAtRoundBoundary() {
        val queue = VideoPlaybackQueue(FixedRandom())
        val onlyVideo = video(1, 1, 1)
        queue.recordPlayed(onlyVideo.key, VideoFeedOrder.RANDOM)

        assertEquals(listOf(onlyVideo), queue.rebuild(listOf(onlyVideo), VideoFeedOrder.RANDOM))
    }

    @Test
    fun lastItemNextIsPregeneratedUpcomingFirstAndBoundaryPromotionDoesNotReshuffle() {
        val queue = VideoPlaybackQueue(FixedRandom(0, 0, 1, 0, 0, 0))
        val videos = (1L..3L).map { video(1, it, it) }
        val started = queue.startRandomSession(videos)
        val atBoundary = queue.settleRandom(
            started.current.entry(started.current.items.lastIndex),
        )
        val upcoming = requireNotNull(atBoundary.upcoming)

        assertNotEquals(atBoundary.current.items.first().key, atBoundary.nextEntry()?.video?.key)
        assertEquals(upcoming.items.first().key, atBoundary.nextEntry()?.video?.key)

        val promoted = queue.settleRandom(upcoming.entry(0))

        assertEquals(upcoming.generation, promoted.current.generation)
        assertEquals(upcoming.items.map(IndexedVideo::key), promoted.current.items.map(IndexedVideo::key))
    }

    @Test
    fun randomRoundMetadataRefreshKeepsExistingOrderAndUsesLatestFileReference() {
        val queue = VideoPlaybackQueue(FixedRandom())
        val first = video(1, 1, 1, fileId = 101)
        val second = video(1, 2, 2, fileId = 102)
        val added = video(1, 3, 3, fileId = 103)

        val reconciled = queue.reconcileRandomRound(
            currentRound = listOf(first, second),
            videos = listOf(
                second.copy(fileId = 202),
                first.copy(fileId = 201),
                added,
            ),
        )

        assertEquals(listOf(first.key, second.key, added.key), reconciled.map { it.key })
        assertEquals(listOf(201, 202, 103), reconciled.map { it.fileId })
    }

    @Test
    fun metadataRefreshKeepsBothRoundOrdersAndUpdatesTheirObjectReferences() {
        val queue = VideoPlaybackQueue(FixedRandom(0, 0, 1, 0))
        val original = (1L..3L).map { video(1, it, it, fileId = it.toInt()) }
        val started = queue.startRandomSession(original)
        val refreshed = original.reversed().map { it.copy(fileId = it.fileId + 100) }

        val reconciled = queue.reconcileRandomSession(refreshed)

        assertEquals(started.current.items.map(IndexedVideo::key), reconciled.current.items.map(IndexedVideo::key))
        assertEquals(
            started.current.items.map { it.fileId + 100 },
            reconciled.current.items.map(IndexedVideo::fileId),
        )
        assertEquals(
            started.upcoming?.items?.map(IndexedVideo::key),
            reconciled.upcoming?.items?.map(IndexedVideo::key),
        )
        assertEquals(
            started.upcoming?.items?.map { it.fileId + 100 },
            reconciled.upcoming?.items?.map(IndexedVideo::fileId),
        )
    }

    @Test
    fun deletingCurrentLastRecomputesTheBoundaryWithoutOutOfBoundsAccess() {
        val queue = VideoPlaybackQueue(FixedRandom(0, 0, 1, 0, 0, 0))
        val videos = (1L..3L).map { video(1, it, it) }
        val started = queue.startRandomSession(videos)
        queue.settleRandom(started.current.entry(started.current.items.lastIndex))
        val removedKey = started.current.items.last().key

        val reconciled = queue.reconcileRandomSession(videos.filterNot { it.key == removedKey })

        assertEquals(reconciled.current.items.lastIndex, reconciled.currentIndex)
        assertEquals(reconciled.upcoming?.items?.first()?.key, reconciled.nextEntry()?.video?.key)
        assertNotEquals(reconciled.current.items.last().key, reconciled.nextEntry()?.video?.key)
    }

    @Test
    fun deletingAnUpcomingItemRevalidatesBothRoundsAgainstTheSourceSet() {
        val queue = VideoPlaybackQueue(FixedRandom(0, 0, 1, 0))
        val videos = (1L..3L).map { video(1, it, it) }
        val started = queue.startRandomSession(videos)
        val removedKey = requireNotNull(started.upcoming).items[1].key

        val reconciled = queue.reconcileRandomSession(videos.filterNot { it.key == removedKey })

        assertEquals(videos.map { it.key }.toSet() - removedKey, reconciled.current.items.map { it.key }.toSet())
        assertEquals(videos.map { it.key }.toSet() - removedKey, reconciled.upcoming?.items?.map { it.key }?.toSet())
    }

    @Test
    fun newlyIndexedVideoJoinsCurrentTailAndTheUpcomingSourceSet() {
        val queue = VideoPlaybackQueue(FixedRandom(0, 1, 0, 0))
        val original = listOf(video(1, 1, 1), video(1, 2, 2))
        queue.startRandomSession(original)
        val added = video(1, 3, 3)

        val reconciled = queue.reconcileRandomSession(original + added)

        assertEquals(added.key, reconciled.current.items.last().key)
        assertEquals(1, reconciled.upcoming?.items?.count { it.key == added.key })
    }

    @Test
    fun emptyAndSingleItemSourcesDoNotCreateMeaninglessUpcomingRounds() {
        val queue = VideoPlaybackQueue(FixedRandom())

        val empty = queue.startRandomSession(emptyList())
        val single = queue.startRandomSession(listOf(video(1, 1, 1)))

        assertEquals(null, empty.upcoming)
        assertEquals(null, empty.nextEntry())
        assertEquals(null, single.upcoming)
        assertEquals(null, single.nextEntry())
    }

    @Test
    fun startingANewFilteredSessionInvalidatesBothOldRoundGenerations() {
        val queue = VideoPlaybackQueue(FixedRandom(0, 0, 0, 0))
        val old = queue.startRandomSession((1L..3L).map { video(1, it, it) })

        val rebuilt = queue.startRandomSession((4L..6L).map { video(2, it, it) })

        assertNotEquals(old.current.generation, rebuilt.current.generation)
        assertNotEquals(old.upcoming?.generation, rebuilt.upcoming?.generation)
        assertEquals((4L..6L).toSet(), rebuilt.current.items.map { it.key.messageId }.toSet())
        assertEquals((4L..6L).toSet(), rebuilt.upcoming?.items?.map { it.key.messageId }?.toSet())
    }

    private fun video(
        chatId: Long,
        messageId: Long,
        publishTime: Long,
        fileId: Int = messageId.toInt(),
    ) = IndexedVideo(
        key = VideoKey(chatId, messageId),
        fileId = fileId,
        remoteUniqueId = "remote-$chatId-$messageId",
        caption = "caption",
        supportsStreaming = true,
        fileSize = 1,
        durationSeconds = 1,
        width = 1,
        height = 1,
        publishTime = publishTime,
        editTime = null,
        canBeSaved = true,
        tags = emptyList(),
    )

    private class FixedRandom(vararg values: Int) : VideoQueueRandomSource {
        private val sequence = values.toList()
        private var index = 0

        override fun nextInt(until: Int): Int = sequence.getOrElse(index++) { 0 }.mod(until)
    }
}
