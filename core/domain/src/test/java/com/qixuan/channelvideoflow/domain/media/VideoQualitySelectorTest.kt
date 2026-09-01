package com.qixuan.channelvideoflow.domain.media

import com.qixuan.channelvideoflow.model.video.IndexedVideo
import com.qixuan.channelvideoflow.model.video.VideoKey
import com.qixuan.channelvideoflow.model.video.VideoPlaybackVariant
import com.qixuan.channelvideoflow.model.video.VideoQualityPreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoQualitySelectorTest {
    @Test
    fun dataSaverChoosesLowestResolutionH264Alternative() {
        val selected = VideoQualitySelector.select(
            video = video(),
            preference = VideoQualityPreference.DATA_SAVER,
            network = NetworkTransport.WIFI,
        )

        assertEquals(360, selected.playbackHeight)
        assertEquals(103, selected.playbackFileId)
    }

    @Test
    fun sevenTwentyChoosesHighestVariantWithinPixelBudget() {
        val selected = VideoQualitySelector.select(
            video = video(),
            preference = VideoQualityPreference.HD_720,
            network = NetworkTransport.MOBILE,
        )

        assertEquals(102, selected.playbackFileId)
        assertEquals(1280, selected.playbackWidth)
        assertEquals(720, selected.playbackHeight)
    }

    @Test
    fun autoUsesSevenTwentyOnWifiAndDataSaverOnMobile() {
        val source = video()

        val wifi = VideoQualitySelector.select(
            source,
            VideoQualityPreference.AUTO,
            NetworkTransport.WIFI,
        )
        val mobile = VideoQualitySelector.select(
            source,
            VideoQualityPreference.AUTO,
            NetworkTransport.MOBILE,
        )

        assertEquals(102, wifi.playbackFileId)
        assertEquals(103, mobile.playbackFileId)
    }

    @Test
    fun originalOrUnsupportedCodecSafelyKeepsOriginalFile() {
        val source = video().copy(
            alternativeVariants = listOf(
                variant(201, 640, 360, codec = "av1"),
                variant(202, 1280, 720, codec = "h265"),
            ),
        )

        val original = VideoQualitySelector.select(
            video(),
            VideoQualityPreference.ORIGINAL,
            NetworkTransport.MOBILE,
        )
        val unsupported = VideoQualitySelector.select(
            source,
            VideoQualityPreference.DATA_SAVER,
            NetworkTransport.MOBILE,
        )

        assertNull(original.selectedAlternative)
        assertNull(unsupported.selectedAlternative)
        assertEquals(source.fileId, unsupported.playbackFileId)
    }

    @Test
    fun sevenTwentyDoesNotUpscaleAnAlreadySmallOriginal() {
        val source = video().copy(width = 640, height = 360)

        val selected = VideoQualitySelector.select(
            source,
            VideoQualityPreference.HD_720,
            NetworkTransport.WIFI,
        )

        assertNull(selected.selectedAlternative)
        assertEquals(source.fileId, selected.playbackFileId)
    }

    @Test
    fun dataSaverDoesNotChooseAFileLargerThanTheOriginal() {
        val source = video().copy(
            fileSize = 100,
            width = 640,
            height = 360,
            alternativeVariants = listOf(
                variant(301, 320, 180).copy(fileSize = 200),
            ),
        )

        val selected = VideoQualitySelector.select(
            source,
            VideoQualityPreference.DATA_SAVER,
            NetworkTransport.MOBILE,
        )

        assertNull(selected.selectedAlternative)
    }

    @Test
    fun autoUsesMeasuredBandwidthOnSlowWifi() {
        val selected = VideoQualitySelector.select(
            video = video(),
            preference = VideoQualityPreference.AUTO,
            network = NetworkTransport.WIFI,
            availableBandwidthBitsPerSecond = 100_000L,
        )

        assertEquals(103, selected.playbackFileId)
        assertEquals(360, selected.playbackHeight)
    }

    @Test
    fun measuredBandwidthNeverOverridesExplicitPreference() {
        val source = video()
        val hd = VideoQualitySelector.select(
            source,
            VideoQualityPreference.HD_720,
            NetworkTransport.WIFI,
            availableBandwidthBitsPerSecond = 1L,
        )
        val original = VideoQualitySelector.select(
            source,
            VideoQualityPreference.ORIGINAL,
            NetworkTransport.WIFI,
            availableBandwidthBitsPerSecond = 1L,
        )

        assertEquals(102, hd.playbackFileId)
        assertEquals(source.fileId, original.playbackFileId)
    }

    @Test
    fun autoExcludesUnknownDuplicateAndNonH264Variants() {
        val source = video().copy(
            alternativeVariants = listOf(
                variant(201, 640, 360).copy(fileSize = null),
                variant(202, 854, 480).copy(fileSize = 2_000_000L),
                variant(202, 1280, 720).copy(fileSize = 1L),
                variant(203, 1280, 720, codec = "h265").copy(fileSize = 1L),
            ),
        )

        val selected = VideoQualitySelector.select(
            source,
            VideoQualityPreference.AUTO,
            NetworkTransport.WIFI,
            availableBandwidthBitsPerSecond = 600_000L,
        )

        assertEquals(202, selected.playbackFileId)
    }

    @Test
    fun bitrateCalculationSaturatesWithoutOverflow() {
        val source = video().copy(
            fileSize = Long.MAX_VALUE,
            durationSeconds = 1,
            alternativeVariants = listOf(
                variant(301, 640, 360).copy(fileSize = Long.MAX_VALUE),
                variant(302, 320, 180).copy(fileSize = 1_000L),
            ),
        )

        val selected = VideoQualitySelector.select(
            source,
            VideoQualityPreference.AUTO,
            NetworkTransport.WIFI,
            availableBandwidthBitsPerSecond = 100_000L,
        )

        assertEquals(302, selected.playbackFileId)
    }

    private fun video() = IndexedVideo(
        key = VideoKey(1, 10),
        fileId = 100,
        remoteUniqueId = "original",
        caption = "",
        supportsStreaming = true,
        fileSize = 4_000_000,
        durationSeconds = 30,
        width = 1920,
        height = 1080,
        publishTime = 1,
        editTime = null,
        canBeSaved = true,
        tags = emptyList(),
        alternativeVariants = listOf(
            variant(101, 1920, 1080),
            variant(102, 1280, 720),
            variant(103, 640, 360),
        ),
    )

    private fun variant(
        fileId: Int,
        width: Int,
        height: Int,
        codec: String = "h264",
    ) = VideoPlaybackVariant(
        fileId = fileId,
        remoteUniqueId = "variant-$fileId",
        fileSize = width.toLong() * height,
        width = width,
        height = height,
        codec = codec,
    )
}
