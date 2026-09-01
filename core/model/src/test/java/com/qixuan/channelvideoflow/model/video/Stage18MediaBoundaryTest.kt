package com.qixuan.channelvideoflow.model.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Stage18MediaBoundaryTest {
    @Test
    fun hlsAndProgressiveResourcesUseOnlyAppOwnedTypes() {
        val variant = VideoPlaybackVariant(
            alternativeId = 7,
            fileId = 101,
            remoteUniqueId = "direct-resource",
            fileSize = 4_096,
            width = 640,
            height = 360,
            codec = "h264",
            hlsManifestFile = TelegramMediaFileReference(
                fileId = 102,
                remoteUniqueId = "manifest-resource",
                fileSize = 512,
            ),
        )

        assertEquals(
            setOf(VideoDeliveryCapability.PROGRESSIVE, VideoDeliveryCapability.HLS),
            variant.capabilities,
        )
        assertFalse(variant::class.java.declaredFields.any { field ->
            field.type.name.startsWith("org.drinkless.tdlib")
        })
        assertFalse(TelegramMediaFileReference::class.java.declaredFields.any { field ->
            field.type.name.startsWith("org.drinkless.tdlib")
        })
    }

    @Test
    fun capabilityLogHasOnlyFixedRedactedFields() {
        val observation = PlaybackCapabilityObservation(
            videoKey = VideoKey(chatId = 12, messageId = 34),
            directVariantCount = 3,
            hlsVariantCount = 2,
            status = HlsCapabilityStatus.AVAILABLE,
        )

        val line = observation.toRedactedLogLine()

        assertEquals(
            "playback_capability chatId=12 messageId=34 directVariants=3 hlsVariants=2 status=AVAILABLE",
            line,
        )
        listOf("http://", "https://", "content://", "file://", "caption", "password", "phone")
            .forEach { forbidden -> assertFalse(line.contains(forbidden, ignoreCase = true)) }
        assertTrue(line.length < 160)
    }
}
