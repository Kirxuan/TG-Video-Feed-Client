package com.qixuan.channelvideoflow.telegram.config

import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class SecureTelegramCredentialsProviderTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun missingEncryptedFileUsesPackagedDeveloperCredentials() = runTest {
        val packaged = availablePackagedCredentials()
        val provider = provider(packaged)

        val result = provider.get() as TelegramCredentialsResult.Available

        assertEquals(12345, result.credentials.apiId)
        assertEquals(SYNTHETIC_HASH, result.credentials.apiHash)
    }

    @Test
    fun savedCredentialsRoundTripWithoutPlaintextInFile() = runTest {
        val file = credentialFile()
        val provider = provider(
            packaged = PackagedTelegramCredentialsProvider {
                TelegramCredentialsResult.Unavailable(
                    setOf(TELEGRAM_API_ID_KEY, TELEGRAM_API_HASH_KEY),
                )
            },
            file = file,
        )

        val saved = provider.save("54321", SECOND_SYNTHETIC_HASH)
        val loaded = provider.get() as TelegramCredentialsResult.Available

        assertTrue(saved is TelegramCredentialsResult.Available)
        assertEquals(54321, loaded.credentials.apiId)
        assertEquals(SECOND_SYNTHETIC_HASH, loaded.credentials.apiHash)
        val raw = file.readBytes().toString(Charsets.ISO_8859_1)
        assertFalse(raw.contains("54321"))
        assertFalse(raw.contains(SECOND_SYNTHETIC_HASH))
    }

    @Test
    fun invalidCredentialsAreRejectedWithoutCreatingAFile() = runTest {
        val file = credentialFile()
        val provider = provider(availablePackagedCredentials(), file)

        val result = provider.save("invalid", "short")

        assertEquals(
            TelegramCredentialsResult.Unavailable(
                setOf(TELEGRAM_API_ID_KEY, TELEGRAM_API_HASH_KEY),
            ),
            result,
        )
        assertFalse(file.exists())
    }

    @Test
    fun unreadableEncryptedFileFailsClosedAndRecoversAfterExplicitSave() = runTest {
        val file = credentialFile().apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(1, 2, 3))
        }
        val cipher = XorCredentialCipher()
        val provider = provider(availablePackagedCredentials(), file, cipher)

        val unreadable = provider.get()
        val saved = provider.save("54321", SECOND_SYNTHETIC_HASH)
        val loaded = provider.get() as TelegramCredentialsResult.Available

        assertEquals(
            TelegramCredentialsResult.Unavailable(
                invalidKeys = setOf(TELEGRAM_API_ID_KEY, TELEGRAM_API_HASH_KEY),
                reason = TelegramCredentialsUnavailableReason.SECURE_STORAGE,
            ),
            unreadable,
        )
        assertTrue(saved is TelegramCredentialsResult.Available)
        assertTrue(cipher.resetCalls >= 1)
        assertEquals(54321, loaded.credentials.apiId)
    }

    private fun provider(
        packaged: PackagedTelegramCredentialsProvider,
        file: File = credentialFile(),
        cipher: XorCredentialCipher = XorCredentialCipher(),
    ): SecureTelegramCredentialsProvider = SecureTelegramCredentialsProvider(
        packagedCredentialsProvider = packaged,
        ioDispatcher = UnconfinedTestDispatcher(),
        encryptedStore = EncryptedTelegramCredentialStore(file, cipher),
    )

    private fun credentialFile(): File = File(temporaryFolder.root, "credentials/telegram-api.v1")

    private fun availablePackagedCredentials() = PackagedTelegramCredentialsProvider {
        TelegramCredentialsResult.Available(TelegramCredentials(12345, SYNTHETIC_HASH))
    }

    private companion object {
        const val SYNTHETIC_HASH = "0123456789abcdef0123456789abcdef"
        const val SECOND_SYNTHETIC_HASH = "fedcba9876543210fedcba9876543210"
    }
}

private class XorCredentialCipher : TelegramCredentialCipher {
    var resetCalls = 0
        private set

    override fun encrypt(plaintext: ByteArray): ByteArray = plaintext.map { byte ->
        (byte.toInt() xor XOR_MASK).toByte()
    }.toByteArray()

    override fun decrypt(encrypted: ByteArray): ByteArray = encrypt(encrypted)

    override fun reset() {
        resetCalls += 1
    }

    private companion object {
        const val XOR_MASK = 0x5A
    }
}
