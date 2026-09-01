package com.qixuan.channelvideoflow.telegram.media

import android.content.Context
import android.system.Os
import android.system.OsConstants
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

/**
 * Startup fallback for the private TDLib files root before exact TDLib statistics are available.
 */
internal class PrivateMediaCacheSizer @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val root = File(context.cacheDir, "tdlib/files")

    fun allocatedBytes(): Long {
        if (!root.isDirectory) return 0L
        val canonicalRoot = runCatching { root.canonicalFile }.getOrNull() ?: return 0L
        var total = 0L
        root.walkTopDown().forEach { candidate ->
            val canonical = runCatching { candidate.canonicalFile }.getOrNull() ?: return@forEach
            if (!canonical.toPath().startsWith(canonicalRoot.toPath())) return@forEach
            val stat = runCatching { Os.lstat(canonical.absolutePath) }.getOrNull()
                ?: return@forEach
            if (!OsConstants.S_ISREG(stat.st_mode)) return@forEach
            val allocated = stat.st_blocks.coerceAtLeast(0L).saturatedMultiply(BLOCK_BYTES)
            total = total.saturatedAdd(allocated)
        }
        return total
    }

    fun safeLastModified(path: String?): Long {
        if (path.isNullOrBlank()) return 0L
        val canonicalRoot = runCatching { root.canonicalFile }.getOrNull() ?: return 0L
        val candidate = runCatching { File(path).canonicalFile }.getOrNull() ?: return 0L
        if (!candidate.toPath().startsWith(canonicalRoot.toPath()) || !candidate.isFile) return 0L
        return candidate.lastModified().coerceAtLeast(0L)
    }

    private fun Long.saturatedMultiply(other: Long): Long =
        if (this > Long.MAX_VALUE / other) Long.MAX_VALUE else this * other

    private fun Long.saturatedAdd(other: Long): Long =
        if (this > Long.MAX_VALUE - other) Long.MAX_VALUE else this + other

    private companion object {
        const val BLOCK_BYTES = 512L
    }
}
