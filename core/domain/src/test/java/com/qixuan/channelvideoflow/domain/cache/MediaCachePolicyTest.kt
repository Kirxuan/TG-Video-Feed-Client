package com.qixuan.channelvideoflow.domain.cache

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaCachePolicyTest {
    @Test
    fun defaultAndSelectableLimitsUseTheRequestedBinarySizes() {
        assertEquals(500L * MediaCacheLimits.MEBIBYTE, MediaCacheLimits.DEFAULT_BYTES)
        assertEquals(
            listOf(200L, 500L, 1024L, 2048L, 5120L, 10240L, 15360L, 20480L)
                .map { it * MediaCacheLimits.MEBIBYTE },
            MediaCacheLimits.allowedBytes,
        )
    }

    @Test
    fun lruSelectsOldestUnprotectedFilesUntilUnderLimit() {
        val plan = MediaCacheEvictionPlanner.plan(
            currentBytes = 900,
            targetBytes = 500,
            entries = listOf(
                MediaCacheEntry(fileId = 3, cachedBytes = 300, lastAccessedAtMillis = 30),
                MediaCacheEntry(fileId = 1, cachedBytes = 250, lastAccessedAtMillis = 10),
                MediaCacheEntry(fileId = 2, cachedBytes = 250, lastAccessedAtMillis = 20),
            ),
            protectedFileIds = emptySet(),
        )

        assertEquals(listOf(1, 2), plan.fileIds)
        assertEquals(400, plan.expectedRemainingBytes)
        assertTrue(plan.canReachTarget)
    }

    @Test
    fun currentAndNextProtectionAreSkippedThenBecomeEligibleAfterRelease() {
        val entries = listOf(
            MediaCacheEntry(fileId = 1, cachedBytes = 300, lastAccessedAtMillis = 1),
            MediaCacheEntry(fileId = 2, cachedBytes = 300, lastAccessedAtMillis = 2),
            MediaCacheEntry(fileId = 3, cachedBytes = 300, lastAccessedAtMillis = 3),
        )

        val protected = MediaCacheEvictionPlanner.plan(
            currentBytes = 900,
            targetBytes = 300,
            entries = entries,
            protectedFileIds = setOf(1, 2),
        )
        assertEquals(listOf(3), protected.fileIds)
        assertFalse(protected.canReachTarget)

        val released = MediaCacheEvictionPlanner.plan(
            currentBytes = 900,
            targetBytes = 300,
            entries = entries,
            protectedFileIds = emptySet(),
        )
        assertEquals(listOf(1, 2), released.fileIds)
        assertTrue(released.canReachTarget)
    }
}
