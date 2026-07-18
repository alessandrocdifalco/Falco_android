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

class PcmExtractor(private val context: Context) {
    suspend fun centerMono16k(track: TrackEntity, authorization: String?, seconds: Int = 30): FloatArray = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        try {
            if (track.uri.startsWith("http")) extractor.setDataSource(track.uri, authorization?.let { mapOf("Authorization" to it) }.orEmpty())
            else extractor.setDataSource(context, Uri.parse(track.uri), null)
            val index = (0 until extractor.trackCount).firstOrNull { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
                ?: error("Traccia audio non decodificabile")
            extractor.selectTrack(index)
            val inputFormat = extractor.getTrackFormat(index)
            val durationUs = inputFormat.getLongOr(MediaFormat.KEY_DURATION, track.durationMs * 1000)
            val clipUs = seconds * 1_000_000L
            val startUs = ((durationUs - clipUs) / 2).coerceAtLeast(0)
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: error("Formato audio sconosciuto")
            val codec = MediaCodec.createDecoderByType(mime)
            val samples = ArrayList<Float>(seconds * 48_000)
            var sampleRate = inputFormat.getIntegerOr(MediaFormat.KEY_SAMPLE_RATE, 44_100)
            var channels = inputFormat.getIntegerOr(MediaFormat.KEY_CHANNEL_COUNT, 2).coerceAtLeast(1)
            try {
                codec.configure(inputFormat, null, null, 0); codec.start()
                val info = MediaCodec.BufferInfo(); var inputDone = false; var outputDone = false
                while (!outputDone) {
                    if (!inputDone) {
                        val inputIndex = codec.dequeueInputBuffer(10_000)
                        if (inputIndex >= 0) {
                            val buffer = codec.getInputBuffer(inputIndex)!!; val size = extractor.readSampleData(buffer, 0)
                            if (size < 0 || extractor.sampleTime > startUs + clipUs) {
                                codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputDone = true
                            } else { codec.queueInputBuffer(inputIndex, 0, size, extractor.sampleTime, 0); extractor.advance() }
                        }
                    }
                    when (val outputIndex = codec.dequeueOutputBuffer(info, 10_000)) {
                        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            sampleRate = codec.outputFormat.getIntegerOr(MediaFormat.KEY_SAMPLE_RATE, sampleRate)
                            channels = codec.outputFormat.getIntegerOr(MediaFormat.KEY_CHANNEL_COUNT, channels).coerceAtLeast(1)
                        }
                        else -> if (outputIndex >= 0) {
                            codec.getOutputBuffer(outputIndex)?.apply {
                                position(info.offset); limit(info.offset + info.size); order(ByteOrder.nativeOrder())
                                val shorts = asShortBuffer()
                                while (shorts.remaining() >= channels) {
                                    var sum = 0f; repeat(channels) { sum += shorts.get() / 32768f }; samples += sum / channels
                                }
                            }
                            outputDone = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                            codec.releaseOutputBuffer(outputIndex, false)
                        }
                    }
                }
            } finally { runCatching { codec.stop() }; codec.release() }
            resample(samples.toFloatArray(), sampleRate, 16_000, seconds * 16_000)
        } finally { extractor.release() }
    }

    private fun resample(source: FloatArray, fromRate: Int, toRate: Int, wanted: Int): FloatArray {
        if (source.isEmpty()) error("Nessun campione audio estratto")
        val out = FloatArray(wanted)
        val ratio = fromRate.toDouble() / toRate
        for (i in out.indices) {
            val p = i * ratio; val a = p.toInt().coerceIn(0, source.lastIndex); val b = (a + 1).coerceAtMost(source.lastIndex)
            out[i] = source[a] + (source[b] - source[a]) * (p - a).toFloat()
        }
        return out
    }
}

private fun MediaFormat.getIntegerOr(key: String, fallback: Int) = if (containsKey(key)) getInteger(key) else fallback
private fun MediaFormat.getLongOr(key: String, fallback: Long) = if (containsKey(key)) getLong(key) else fallback
