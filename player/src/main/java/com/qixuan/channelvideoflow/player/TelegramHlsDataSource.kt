package com.qixuan.channelvideoflow.player

import android.net.Uri
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import com.qixuan.channelvideoflow.domain.media.TelegramFileGateway
import com.qixuan.channelvideoflow.domain.media.TelegramInternalResourceKind
import com.qixuan.channelvideoflow.domain.media.TelegramFileOwnerKind
import java.io.ByteArrayOutputStream
import java.io.IOException

@UnstableApi
internal class TelegramHlsDataSource(
    private val gateway: TelegramFileGateway,
    private val session: TelegramHlsPlaybackSession,
    private val rangeSession: PlaybackRangeRequestSession,
    private val timeoutMillis: Long = TelegramMediaDataSource.DEFAULT_TIMEOUT_MILLIS,
    private val isMainThread: () -> Boolean = {
        Looper.myLooper() == Looper.getMainLooper()
    },
    private val ownerKindOverride: TelegramFileOwnerKind? = null,
    private val maxReadAheadBytes: Long = TelegramMediaDataSource.MAX_CURRENT_READ_AHEAD_BYTES,
) : DataSource {
    private var memory: ByteArray? = null
    private var memoryPosition = 0
    private var memoryLimit = 0
    private var delegate: TelegramMediaDataSource? = null
    private var currentUri: Uri? = null

    override fun addTransferListener(transferListener: TransferListener) = Unit

    override fun open(dataSpec: DataSpec): Long {
        close()
        currentUri = dataSpec.uri
        val parsed = TelegramHlsUriCodec.parse(dataSpec.uri.toString())
            ?: throw TelegramMediaUnavailableException("无效的 Telegram HLS 内部 URI")
        if (parsed.accountGeneration != gateway.currentAccountGeneration()) {
            throw TelegramMediaUnavailableException("Telegram HLS 账号 generation 已失效")
        }
        if (session.isMaster(parsed)) {
            return openMemory(session.masterBytes, dataSpec)
        }
        val resolution = gateway.resolveInternalResource(
            accountGeneration = parsed.accountGeneration,
            opaqueToken = parsed.opaqueToken,
        ) ?: throw TelegramMediaUnavailableException("Telegram HLS token 已失效")
        if (parsed.kind != resolution.kind.name.lowercase()) {
            throw TelegramMediaUnavailableException("Telegram HLS 资源类型不匹配")
        }
        return when (resolution.kind) {
            TelegramInternalResourceKind.HLS_MANIFEST -> {
                val raw = readManifest(resolution.fileId, resolution.expectedSize, dataSpec.uri)
                val parsedManifest = StrictTelegramHlsManifestParser.parseAndRewrite(
                    bytes = raw,
                    expectedKind = TelegramHlsPlaylistKind.MEDIA,
                    allowedResources = resolution.referencedResources,
                )
                openMemory(parsedManifest.sanitizedBytes, dataSpec)
            }
            TelegramInternalResourceKind.HLS_MEDIA -> {
                val source = TelegramMediaDataSource(
                    gateway = gateway,
                    timeoutMillis = timeoutMillis,
                    fileIdOverride = resolution.fileId,
                    requestSession = rangeSession,
                    isMainThread = isMainThread,
                    ownerKindOverride = ownerKindOverride,
                    maxReadAheadBytes = maxReadAheadBytes,
                )
                delegate = source
                source.open(dataSpec)
            }
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        delegate?.let { return it.read(buffer, offset, length) }
        val bytes = memory ?: return C.RESULT_END_OF_INPUT
        if (memoryPosition >= memoryLimit) return C.RESULT_END_OF_INPUT
        val count = minOf(length, memoryLimit - memoryPosition)
        bytes.copyInto(buffer, offset, memoryPosition, memoryPosition + count)
        memoryPosition += count
        return count
    }

    override fun getUri(): Uri? = currentUri

    override fun getResponseHeaders(): Map<String, List<String>> = emptyMap()

    override fun close() {
        delegate?.close()
        delegate = null
        memory = null
        memoryPosition = 0
        memoryLimit = 0
        currentUri = null
    }

    private fun openMemory(bytes: ByteArray, dataSpec: DataSpec): Long {
        val start = dataSpec.position.coerceAtMost(bytes.size.toLong()).toInt()
        val available = bytes.size - start
        val requested = dataSpec.length
            .takeUnless { it == C.LENGTH_UNSET.toLong() }
            ?.coerceAtMost(available.toLong())
            ?.toInt()
            ?: available
        memory = bytes
        memoryPosition = start
        memoryLimit = start + requested
        return requested.toLong()
    }

    private fun readManifest(fileId: Int, expectedSize: Long?, originalUri: Uri): ByteArray {
        val size = expectedSize ?: gateway.currentSnapshot(fileId)?.size?.takeIf { it > 0L }
        if (size == null || size > StrictTelegramHlsManifestParser.MAX_MANIFEST_BYTES) {
            throw TelegramMediaUnavailableException("Telegram HLS manifest 大小未知或超限")
        }
        val source = TelegramMediaDataSource(
            gateway = gateway,
            timeoutMillis = timeoutMillis,
            fileIdOverride = fileId,
            requestSession = rangeSession,
            isMainThread = isMainThread,
            ownerKindOverride = ownerKindOverride,
            maxReadAheadBytes = maxReadAheadBytes,
        )
        try {
            source.open(DataSpec(originalUri, 0L, size))
            val output = ByteArrayOutputStream(size.toInt())
            val buffer = ByteArray(8 * 1024)
            while (output.size() <= StrictTelegramHlsManifestParser.MAX_MANIFEST_BYTES) {
                val read = source.read(buffer, 0, buffer.size)
                if (read == C.RESULT_END_OF_INPUT) break
                output.write(buffer, 0, read)
            }
            if (output.size() != size.toInt()) {
                throw TelegramMediaReadException("Telegram HLS manifest 未完整读取")
            }
            return output.toByteArray()
        } finally {
            source.close()
        }
    }

    class Factory(
        private val gateway: TelegramFileGateway,
        private val session: TelegramHlsPlaybackSession,
        private val rangeSession: PlaybackRangeRequestSession,
        private val isMainThread: () -> Boolean = {
            Looper.myLooper() == Looper.getMainLooper()
        },
        private val ownerKindOverride: TelegramFileOwnerKind? = null,
        private val maxReadAheadBytes: Long = TelegramMediaDataSource.MAX_CURRENT_READ_AHEAD_BYTES,
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource = TelegramHlsDataSource(
            gateway = gateway,
            session = session,
            rangeSession = rangeSession,
            isMainThread = isMainThread,
            ownerKindOverride = ownerKindOverride,
            maxReadAheadBytes = maxReadAheadBytes,
        )
    }
}
