package com.alessandro.falco.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.alessandro.falco.data.TrackEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteOrder
import kotlin.math.abs

class WaveformExtractor(private val context: Context) {
    suspend fun extract(track: TrackEntity, authorization: String?): List<Float> = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        try {
            if (track.uri.startsWith("http")) extractor.setDataSource(track.uri, authorization?.let { mapOf("Authorization" to it) }.orEmpty())
            else extractor.setDataSource(context, Uri.parse(track.uri), null)
            val index = (0 until extractor.trackCount).firstOrNull { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true } ?: return@withContext emptyList()
            extractor.selectTrack(index); val format = extractor.getTrackFormat(index); val mime = format.getString(MediaFormat.KEY_MIME) ?: return@withContext emptyList()
            val codec = MediaCodec.createDecoderByType(mime); val values = ArrayList<Float>()
            try {
                codec.configure(format, null, null, 0); codec.start(); val info = MediaCodec.BufferInfo(); var inputDone = false; var outputDone = false
                while (!outputDone) {
                    if (!inputDone) { val i = codec.dequeueInputBuffer(10_000); if (i >= 0) { val buffer = codec.getInputBuffer(i)!!; val size = extractor.readSampleData(buffer, 0); if (size < 0) { codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputDone = true } else { codec.queueInputBuffer(i, 0, size, extractor.sampleTime, 0); extractor.advance() } } }
                    val o = codec.dequeueOutputBuffer(info, 10_000); if (o >= 0) { codec.getOutputBuffer(o)?.apply { position(info.offset); limit(info.offset + info.size); order(ByteOrder.nativeOrder()); val shorts = asShortBuffer(); var peak = 0; while (shorts.hasRemaining()) peak = maxOf(peak, abs(shorts.get().toInt())); values += peak / 32768f }; outputDone = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0; codec.releaseOutputBuffer(o, false) }
                }
            } finally { runCatching { codec.stop() }; codec.release() }
            downsample(values, 120)
        } finally { extractor.release() }
    }
    private fun downsample(input: List<Float>, count: Int): List<Float> = if (input.size <= count) input else List(count) { i -> val from = i * input.size / count; val to = ((i + 1) * input.size / count).coerceAtMost(input.size); input.subList(from, to).maxOrNull() ?: 0f }
}
