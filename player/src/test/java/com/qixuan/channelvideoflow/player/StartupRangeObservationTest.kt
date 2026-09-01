package com.qixuan.channelvideoflow.player

import com.qixuan.channelvideoflow.domain.media.TelegramFileSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

class StartupRangeObservationTest {
    @Test
    fun cachedHeadFollowedByTailMissClassifiesTheFirstMissAsTail() {
        val observation = StartupRangeObservation()
        observation.onDataSpecOpened(
            position = 0L,
            requestedLength = KIB_256,
            snapshot = snapshot(size = MIB_4, offset = 0L, prefix = KIB_256),
        )
        observation.onDataSpecOpened(
            position = MIB_4 - KIB_64,
            requestedLength = KIB_64,
            snapshot = snapshot(size = MIB_4, offset = 0L, prefix = KIB_256),
        )

        assertEquals(
            StartupRangeObservationSnapshot(
                firstMissCategory = StartupDataSpecOffsetCategory.TAIL,
                coveredBeforeCurrentBytes = KIB_256,
                dataSpecOpenCount = 2,
                extractorRangeSwitchCount = 1,
            ),
            observation.snapshot(),
        )
    }

    @Test
    fun unknownSizeAtHeadUsesSafeHeadClassification() {
        val observation = StartupRangeObservation()

        observation.onDataSpecOpened(0L, KIB_256, snapshot(size = 0L, offset = 0L, prefix = 0L))

        assertEquals(
            StartupDataSpecOffsetCategory.HEAD,
            observation.snapshot().firstMissCategory,
        )
    }

    @Test
    fun unknownSizeAwayFromHeadIsUnknown() {
        val observation = StartupRangeObservation()

        observation.onDataSpecOpened(KIB_256, KIB_64, snapshot(size = 0L, offset = 0L, prefix = 0L))

        assertEquals(
            StartupDataSpecOffsetCategory.UNKNOWN,
            observation.snapshot().firstMissCategory,
        )
    }

    @Test
    fun knownNonTailOffsetIsMiddle() {
        val observation = StartupRangeObservation()

        observation.onDataSpecOpened(MIB_1, KIB_64, snapshot(size = MIB_4, offset = 0L, prefix = 0L))

        assertEquals(
            StartupDataSpecOffsetCategory.MIDDLE,
            observation.snapshot().firstMissCategory,
        )
    }

    @Test
    fun smallFileNeverCreatesAnOverlappingTailCategory() {
        val observation = StartupRangeObservation()

        observation.onDataSpecOpened(
            position = KIB_64,
            requestedLength = KIB_64,
            snapshot = snapshot(size = KIB_128, offset = 0L, prefix = 0L),
        )

        assertEquals(
            StartupDataSpecOffsetCategory.HEAD,
            observation.snapshot().firstMissCategory,
        )
    }

    private fun snapshot(
        size: Long,
        offset: Long,
        prefix: Long,
    ) = TelegramFileSnapshot(
        fileId = 1,
        size = size,
        expectedSize = size,
        localPath = "private-test-file",
        canBeDownloaded = true,
        isDownloadingActive = prefix < size,
        isDownloadingCompleted = size > 0L && prefix >= size,
        downloadOffset = offset,
        downloadedPrefixSize = prefix,
        downloadedSize = prefix,
    )

    private companion object {
        const val KIB_64 = 64L * 1024L
        const val KIB_128 = 128L * 1024L
        const val KIB_256 = 256L * 1024L
        const val MIB_1 = 1024L * 1024L
        const val MIB_4 = 4L * 1024L * 1024L
    }
}
