package com.qixuan.channelvideoflow.telegram.config

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.qixuan.channelvideoflow.telegram.di.TelegramIoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.KeyStore
import java.util.concurrent.atomic.AtomicBoolean
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

@Singleton
class SecureTelegramCredentialsProvider private constructor(
    private val packagedCredentialsProvider: PackagedTelegramCredentialsProvider,
    private val ioDispatcher: CoroutineDispatcher,
    private val encryptedStore: EncryptedTelegramCredentialStore,
    @Suppress("UNUSED_PARAMETER") marker: Unit,
) : TelegramCredentialsProvider, TelegramCredentialsStore {
    private val resetCipherBeforeWrite = AtomicBoolean(false)

    @Inject
    constructor(
        @ApplicationContext context: Context,
        packagedCredentialsProvider: PackagedTelegramCredentialsProvider,
        @TelegramIoDispatcher ioDispatcher: CoroutineDispatcher,
    ) : this(
        packagedCredentialsProvider = packagedCredentialsProvider,
        ioDispatcher = ioDispatcher,
        encryptedStore = EncryptedTelegramCredentialStore(
            file = File(context.noBackupFilesDir, CREDENTIAL_FILE_PATH),
            cipher = AndroidKeystoreTelegramCredentialCipher(),
        ),
        marker = Unit,
    )

    internal constructor(
        packagedCredentialsProvider: PackagedTelegramCredentialsProvider,
        ioDispatcher: CoroutineDispatcher,
        encryptedStore: EncryptedTelegramCredentialStore,
    ) : this(
        packagedCredentialsProvider = packagedCredentialsProvider,
        ioDispatcher = ioDispatcher,
        encryptedStore = encryptedStore,
        marker = Unit,
    )

    override fun get(): TelegramCredentialsResult = when (val result = encryptedStore.read()) {
        CredentialReadResult.Missing -> packagedCredentialsProvider.get()
        is CredentialReadResult.Available -> result.credentials
        CredentialReadResult.Unreadable -> {
            resetCipherBeforeWrite.set(true)
            secureStorageUnavailable()
        }
    }

    override suspend fun save(apiId: String, apiHash: String): TelegramCredentialsResult {
        val validated = buildTelegramCredentialsResult(apiId, apiHash)
        if (validated !is TelegramCredentialsResult.Available) return validated

        return withContext(ioDispatcher) {
            try {
                if (resetCipherBeforeWrite.getAndSet(false)) {
                    encryptedStore.resetCipher()
                }
                encryptedStore.write(validated.credentials)
                validated
            } catch (_: Exception) {
                try {
                    encryptedStore.resetCipher()
                    encryptedStore.write(validated.credentials)
                    validated
                } catch (_: Exception) {
                    resetCipherBeforeWrite.set(true)
                    secureStorageUnavailable()
                }
            }
        }
    }

    private companion object {
        const val CREDENTIAL_FILE_PATH = "credentials/telegram-api.v1"

        fun secureStorageUnavailable() = TelegramCredentialsResult.Unavailable(
            invalidKeys = setOf(TELEGRAM_API_ID_KEY, TELEGRAM_API_HASH_KEY),
            reason = TelegramCredentialsUnavailableReason.SECURE_STORAGE,
        )
    }
}

internal sealed interface CredentialReadResult {
    data object Missing : CredentialReadResult
    data class Available(val credentials: TelegramCredentialsResult.Available) : CredentialReadResult
    data object Unreadable : CredentialReadResult
}

internal class EncryptedTelegramCredentialStore(
    private val file: File,
    private val cipher: TelegramCredentialCipher,
) {
    @Synchronized
    fun read(): CredentialReadResult {
        if (!file.isFile) return CredentialReadResult.Missing
        return try {
            val encrypted = file.readBytes()
            if (encrypted.isEmpty() || encrypted.size > MAX_ENCRYPTED_BYTES) {
                return CredentialReadResult.Unreadable
            }
            val plaintext = cipher.decrypt(encrypted)
            try {
                decodeCredentials(plaintext)
                    ?.let(CredentialReadResult::Available)
                    ?: CredentialReadResult.Unreadable
            } finally {
                plaintext.fill(0)
            }
        } catch (_: Exception) {
            CredentialReadResult.Unreadable
        }
    }

    @Synchronized
    fun write(credentials: TelegramCredentials) {
        val plaintext = encodeCredentials(credentials)
        try {
            atomicWrite(cipher.encrypt(plaintext))
        } finally {
            plaintext.fill(0)
        }
    }

    @Synchronized
    fun resetCipher() {
        cipher.reset()
    }

    private fun atomicWrite(bytes: ByteArray) {
        val parent = file.parentFile ?: throw IOException("Credential directory is unavailable")
        Files.createDirectories(parent.toPath())
        val temporary = File(parent, "${file.name}.tmp")
        try {
            temporary.outputStream().use { output ->
                output.write(bytes)
                output.flush()
                output.fd.sync()
            }
            try {
                Files.move(
                    temporary.toPath(),
                    file.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(
                    temporary.toPath(),
                    file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    private fun encodeCredentials(credentials: TelegramCredentials): ByteArray {
        val hashBytes = credentials.apiHash.toByteArray(StandardCharsets.US_ASCII)
        require(hashBytes.size == API_HASH_BYTES)
        return ByteBuffer.allocate(1 + Int.SIZE_BYTES + API_HASH_BYTES)
            .put(PLAINTEXT_VERSION)
            .putInt(credentials.apiId)
            .put(hashBytes)
            .array()
            .also { hashBytes.fill(0) }
    }

    private fun decodeCredentials(bytes: ByteArray): TelegramCredentialsResult.Available? {
        if (bytes.size != 1 + Int.SIZE_BYTES + API_HASH_BYTES) return null
        val buffer = ByteBuffer.wrap(bytes)
        if (buffer.get() != PLAINTEXT_VERSION) return null
        val apiId = buffer.int
        val hashBytes = ByteArray(API_HASH_BYTES)
        buffer.get(hashBytes)
        val apiHash = String(hashBytes, StandardCharsets.US_ASCII)
        hashBytes.fill(0)
        return buildTelegramCredentialsResult(apiId.toString(), apiHash)
            as? TelegramCredentialsResult.Available
    }

    private companion object {
        const val MAX_ENCRYPTED_BYTES = 4_096
        const val API_HASH_BYTES = 32
        const val PLAINTEXT_VERSION: Byte = 1
    }
}

internal interface TelegramCredentialCipher {
    fun encrypt(plaintext: ByteArray): ByteArray
    fun decrypt(encrypted: ByteArray): ByteArray
    fun reset()
}

private class AndroidKeystoreTelegramCredentialCipher : TelegramCredentialCipher {
    override fun encrypt(plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        cipher.updateAAD(AAD)
        val ciphertext = cipher.doFinal(plaintext)
        val iv = cipher.iv
        return ByteBuffer.allocate(Int.SIZE_BYTES + 1 + iv.size + ciphertext.size)
            .putInt(CONTAINER_MAGIC)
            .put(iv.size.toByte())
            .put(iv)
            .put(ciphertext)
            .array()
    }

    override fun decrypt(encrypted: ByteArray): ByteArray {
        val buffer = ByteBuffer.wrap(encrypted)
        if (buffer.remaining() < Int.SIZE_BYTES + 1) throw IOException("Credential file is truncated")
        if (buffer.int != CONTAINER_MAGIC) throw IOException("Credential file format is invalid")
        val ivSize = buffer.get().toInt() and 0xff
        if (ivSize != GCM_IV_BYTES || buffer.remaining() <= ivSize) {
            throw IOException("Credential file format is invalid")
        }
        val iv = ByteArray(ivSize)
        buffer.get(iv)
        val ciphertext = ByteArray(buffer.remaining())
        buffer.get(ciphertext)
        return Cipher.getInstance(TRANSFORMATION).run {
            init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            updateAAD(AAD)
            doFinal(ciphertext)
        }
    }

    override fun reset() {
        keyStore().deleteEntry(KEY_ALIAS)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = keyStore()
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .setKeySize(256)
                    .build(),
            )
            generateKey()
        }
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "com.qixuan.channelvideoflow.telegram.credentials.v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_IV_BYTES = 12
        const val GCM_TAG_BITS = 128
        const val CONTAINER_MAGIC = 0x564C5231
        val AAD = "VELORA_TELEGRAM_CREDENTIALS_V1".toByteArray(StandardCharsets.US_ASCII)
    }
}
