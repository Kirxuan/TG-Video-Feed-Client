package com.qixuan.channelvideoflow.domain.video

import com.qixuan.channelvideoflow.model.video.IndexedVideo
import com.qixuan.channelvideoflow.model.video.VideoFeedOrder
import com.qixuan.channelvideoflow.model.video.VideoKey

fun interface VideoQueueRandomSource {
    fun nextInt(until: Int): Int
}

data class RandomRound(
    val items: List<IndexedVideo>,
    val generation: Long,
) {
    fun entry(index: Int): RandomRoundEntry {
        val video = items.getOrNull(index) ?: error("Random round index is out of bounds")
        return RandomRoundEntry(
            video = video,
            roundGeneration = generation,
            index = index,
        )
    }
}

data class RandomRoundEntry(
    val video: IndexedVideo,
    val roundGeneration: Long,
    val index: Int,
)

data class RandomRoundState(
    val current: RandomRound,
    val currentIndex: Int,
    val upcoming: RandomRound?,
    val previousBoundaryKey: VideoKey?,
) {
    fun nextEntry(): RandomRoundEntry? = when {
        current.items.size <= 1 -> null
        currentIndex < current.items.lastIndex -> current.entry(currentIndex + 1)
        else -> upcoming?.takeIf { it.items.isNotEmpty() }?.entry(0)
    }
}

/**
 * Builds the finite metadata queue for a feed session. It does not own a player,
 * download, TDLib object, or persisted history.
 */
class VideoPlaybackQueue(
    private val random: VideoQueueRandomSource = VideoQueueRandomSource(kotlin.random.Random.Default::nextInt),
) {
    private var lastPlayedRandomKey: VideoKey? = null
    private var nextRandomRoundGeneration = 0L
    var randomRoundState: RandomRoundState? = null
        private set

    fun rebuild(
        videos: List<IndexedVideo>,
        order: VideoFeedOrder,
    ): List<IndexedVideo> = when (order) {
        VideoFeedOrder.LATEST -> videos.sortedWith(
            compareByDescending<IndexedVideo> { it.publishTime }
                .thenByDescending { it.key.chatId }
                .thenByDescending { it.key.messageId },
        )
        VideoFeedOrder.RANDOM -> startRandomSession(videos).current.items
    }

    fun startRandomSession(videos: List<IndexedVideo>): RandomRoundState {
        val current = newRound(videos, lastPlayedRandomKey)
        val upcoming = upcomingRound(videos, current.items.lastOrNull()?.key)
        return RandomRoundState(
            current = current,
            currentIndex = 0,
            upcoming = upcoming,
            previousBoundaryKey = lastPlayedRandomKey,
        ).also { randomRoundState = it }
    }

    fun settleRandom(entry: RandomRoundEntry): RandomRoundState {
        val state = checkNotNull(randomRoundState) { "Random session has not started" }
        val settled = when (entry.roundGeneration) {
            state.current.generation -> {
                require(state.current.items.getOrNull(entry.index)?.key == entry.video.key) {
                    "Random current entry does not match its round"
                }
                state.copy(currentIndex = entry.index)
            }
            state.upcoming?.generation -> {
                val promoted = state.upcoming
                require(promoted.items.getOrNull(entry.index)?.key == entry.video.key) {
                    "Random upcoming entry does not match its round"
                }
                val boundaryKey = state.current.items.lastOrNull()?.key
                RandomRoundState(
                    current = promoted,
                    currentIndex = entry.index,
                    upcoming = upcomingRound(promoted.items, promoted.items.lastOrNull()?.key),
                    previousBoundaryKey = boundaryKey,
                )
            }
            else -> error("Stale random round generation")
        }
        randomRoundState = settled
        return settled
    }

    fun reconcileRandomSession(videos: List<IndexedVideo>): RandomRoundState {
        val state = randomRoundState ?: return startRandomSession(videos)
        val settledKey = state.current.items.getOrNull(state.currentIndex)?.key
        val currentItems = reconcileRoundItems(
            existingItems = state.current.items,
            videos = videos,
            previousBoundaryKey = lastPlayedRandomKey,
        )
        val current = state.current.copy(items = currentItems)
        val currentIndex = settledKey
            ?.let { key -> currentItems.indexOfFirst { it.key == key } }
            ?.takeIf { it >= 0 }
            ?: state.currentIndex.coerceIn(0, currentItems.lastIndex.coerceAtLeast(0))
        val upcoming = when {
            currentItems.size <= 1 -> null
            state.upcoming == null -> upcomingRound(videos, currentItems.lastOrNull()?.key)
            else -> state.upcoming.copy(
                items = avoidBoundaryRepeat(
                    items = reconcileRoundItems(
                        existingItems = state.upcoming.items,
                        videos = videos,
                        previousBoundaryKey = null,
                    ),
                    previousBoundaryKey = currentItems.lastOrNull()?.key,
                ),
            )
        }
        return state.copy(
            current = current,
            currentIndex = currentIndex,
            upcoming = upcoming,
        ).also { randomRoundState = it }
    }

    fun recordPlayed(videoKey: VideoKey, order: VideoFeedOrder) {
        if (order == VideoFeedOrder.RANDOM) lastPlayedRandomKey = videoKey
    }

    /**
     * Applies Room metadata/file-reference updates without reshuffling the
     * current random round. Removed videos disappear and newly indexed videos
     * are appended in a shuffled tail.
     */
    fun reconcileRandomRound(
        currentRound: List<IndexedVideo>,
        videos: List<IndexedVideo>,
    ): List<IndexedVideo> = reconcileRoundItems(
        existingItems = currentRound,
        videos = videos,
        previousBoundaryKey = lastPlayedRandomKey,
    )

    private fun reconcileRoundItems(
        existingItems: List<IndexedVideo>,
        videos: List<IndexedVideo>,
        previousBoundaryKey: VideoKey?,
    ): List<IndexedVideo> {
        val latestByKey = videos.associateByTo(linkedMapOf(), IndexedVideo::key)
        val retained = existingItems.mapNotNull { current ->
            latestByKey.remove(current.key)
        }
        return retained + shuffledRound(latestByKey.values.toList(), previousBoundaryKey)
    }

    private fun newRound(
        videos: List<IndexedVideo>,
        previousBoundaryKey: VideoKey?,
    ): RandomRound = RandomRound(
        items = shuffledRound(videos, previousBoundaryKey),
        generation = ++nextRandomRoundGeneration,
    )

    private fun upcomingRound(
        videos: List<IndexedVideo>,
        previousBoundaryKey: VideoKey?,
    ): RandomRound? = videos
        .takeIf { it.size > 1 }
        ?.let { newRound(it, previousBoundaryKey) }

    private fun shuffledRound(
        videos: List<IndexedVideo>,
        previousBoundaryKey: VideoKey? = lastPlayedRandomKey,
    ): List<IndexedVideo> {
        val shuffled = videos.toMutableList()
        for (index in shuffled.lastIndex downTo 1) {
            val target = random.nextInt(index + 1)
            val value = shuffled[index]
            shuffled[index] = shuffled[target]
            shuffled[target] = value
        }

        return avoidBoundaryRepeat(shuffled, previousBoundaryKey)
    }

    private fun avoidBoundaryRepeat(
        items: List<IndexedVideo>,
        previousBoundaryKey: VideoKey?,
    ): List<IndexedVideo> {
        if (items.size <= 1 || items.first().key != previousBoundaryKey) return items
        val replacementIndex = items.indexOfFirst { it.key != previousBoundaryKey }
        if (replacementIndex <= 0) return items
        return items.toMutableList().apply {
            val first = this[0]
            this[0] = this[replacementIndex]
            this[replacementIndex] = first
        }
    }
}
