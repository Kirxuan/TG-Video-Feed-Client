package com.qixuan.channelvideoflow.telegram.storage

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException

internal class TdLibDirectories private constructor(
    private val databaseRoot: File,
    private val filesRoot: File,
) {
    constructor(@ApplicationContext context: Context) : this(context.noBackupFilesDir, context.cacheDir)

    val databaseDirectory: File = File(databaseRoot, "tdlib/database")
    val filesDirectory: File = File(filesRoot, "tdlib/files")

    fun ensureCreated() {
        try {
            createDirectory(databaseDirectory)
            createDirectory(filesDirectory)
            verifyContained(databaseDirectory, databaseRoot)
            verifyContained(filesDirectory, filesRoot)
        } catch (failure: TdLibDirectoryException) {
            throw failure
        } catch (_: IOException) {
            throw TdLibDirectoryException()
        } catch (_: SecurityException) {
            throw TdLibDirectoryException()
        } catch (_: IllegalArgumentException) {
            throw TdLibDirectoryException()
        }
    }

    private fun createDirectory(directory: File) {
        if (!directory.isDirectory && !directory.mkdirs()) throw TdLibDirectoryException()
    }

    private fun verifyContained(directory: File, root: File) {
        if (!directory.canonicalFile.toPath().startsWith(root.canonicalFile.toPath())) {
            throw TdLibDirectoryException()
        }
    }

    internal companion object {
        fun forTest(databaseRoot: File, filesRoot: File): TdLibDirectories =
            TdLibDirectories(databaseRoot, filesRoot)
    }
}

internal class TdLibDirectoryException : IllegalStateException("TDLib private directory unavailable")
