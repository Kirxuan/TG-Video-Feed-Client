package com.qixuan.channelvideoflow.telegram.storage

import java.nio.file.Files
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TdLibDirectoriesTest {
    @Test
    fun ensureCreatedBuildsDirectoriesUnderOnlyTheirPrivateRoots() {
        val root = Files.createTempDirectory("cvf-tdlib-dirs").toFile()
        val directories = TdLibDirectories.forTest(root.resolve("no-backup"), root.resolve("cache"))

        directories.ensureCreated()

        assertTrue(directories.databaseDirectory.isDirectory)
        assertTrue(directories.filesDirectory.isDirectory)
        assertTrue(directories.databaseDirectory.canonicalPath.startsWith(root.resolve("no-backup").canonicalPath))
        assertTrue(directories.filesDirectory.canonicalPath.startsWith(root.resolve("cache").canonicalPath))
    }

    @Test
    fun directoryFailureUsesARedactedMessage() {
        val root = Files.createTempDirectory("cvf-tdlib-file").toFile()
        val blockingFile = root.resolve("blocking").apply { writeText("not a directory") }
        val directories = TdLibDirectories.forTest(blockingFile, root.resolve("cache"))

        val error = runCatching { directories.ensureCreated() }.exceptionOrNull()

        assertTrue(error is TdLibDirectoryException)
        assertFalse(checkNotNull(error).message.orEmpty().contains(root.absolutePath))
    }
}
