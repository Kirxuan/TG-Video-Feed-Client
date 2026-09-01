package com.qixuan.channelvideoflow.player

import android.os.Handler
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.upstream.BandwidthMeter
import com.qixuan.channelvideoflow.domain.media.StreamingNetworkMetricsRepository
import java.util.concurrent.CopyOnWriteArrayList

/** Media3 bridge backed only by active TDLib network progress; local reads never enter it. */
@UnstableApi
internal class TdLibBandwidthMeter(
    private val metrics: StreamingNetworkMetricsRepository,
    private val coldStartBitsPerSecond: Long = COLD_START_BITS_PER_SECOND,
) : BandwidthMeter {
    private val listeners = CopyOnWriteArrayList<ListenerRegistration>()
    @Volatile
    private var lastNotifiedRevision = Long.MIN_VALUE

    override fun getBitrateEstimate(): Long {
        val estimate = metrics.estimate.value
        notifyIfChanged()
        return estimate?.availableBitsPerSecond?.coerceAtLeast(1L)
            ?: coldStartBitsPerSecond
    }

    override fun getTimeToFirstByteEstimateUs(): Long = metrics.estimate.value
        ?.timeToFirstByteP50Millis
        ?.let { millis -> millis.saturatedMultiply(1_000L) }
        ?: C.TIME_UNSET

    /** TelegramMediaDataSource reports network bytes directly, so Media3 local transfer events are excluded. */
    override fun getTransferListener(): TransferListener? = null

    override fun addEventListener(handler: Handler, eventListener: BandwidthMeter.EventListener) {
        listeners.removeAll { it.listener === eventListener }
        listeners += ListenerRegistration(handler, eventListener)
    }

    override fun removeEventListener(eventListener: BandwidthMeter.EventListener) {
        listeners.removeAll { it.listener === eventListener }
    }

    private fun notifyIfChanged() {
        val estimate = metrics.estimate.value ?: return
        if (estimate.revision == lastNotifiedRevision) return
        synchronized(this) {
            if (estimate.revision == lastNotifiedRevision) return
            lastNotifiedRevision = estimate.revision
            listeners.forEach { registration ->
                registration.handler.post {
                    registration.listener.onBandwidthSample(
                        0,
                        0L,
                        estimate.availableBitsPerSecond,
                    )
                }
            }
        }
    }

    private fun Long.saturatedMultiply(value: Long): Long =
        if (this > Long.MAX_VALUE / value) Long.MAX_VALUE else this * value

    private data class ListenerRegistration(
        val handler: Handler,
        val listener: BandwidthMeter.EventListener,
    )

    private companion object {
        const val COLD_START_BITS_PER_SECOND = 450_000L
    }
}
