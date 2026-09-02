package com.qixuan.channelvideoflow.telegram.config

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SecureTelegramCredentialsAndroidTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val credentialDirectory = File(context.noBackupFilesDir, "credentials")

    @After
    fun removeSyntheticCredentialFile() {
        credentialDirectory.deleteRecursively()
    }

    @Test
    fun androidKeystoreRoundTripKeepsPlaintextOutOfPrivateFile() = runBlocking {
        val provider = SecureTelegramCredentialsProvider(
            context = context,
            packagedCredentialsProvider = PackagedTelegramCredentialsProvider {
                TelegramCredentialsResult.Unavailable(
                    setOf(TELEGRAM_API_ID_KEY, TELEGRAM_API_HASH_KEY),
                )
            },
            ioDispatcher = Dispatchers.IO,
        )

        provider.save("54321", SYNTHETIC_HASH)
        val loaded = provider.get() as TelegramCredentialsResult.Available

        assertEquals(54321, loaded.credentials.apiId)
        assertEquals(SYNTHETIC_HASH, loaded.credentials.apiHash)
        val encryptedText = File(credentialDirectory, "telegram-api.v1")
            .readBytes()
            .toString(Charsets.ISO_8859_1)
        assertFalse(encryptedText.contains("54321"))
        assertFalse(encryptedText.contains(SYNTHETIC_HASH))
    }

    private companion object {
        const val SYNTHETIC_HASH = "fedcba9876543210fedcba9876543210"
    }
}
