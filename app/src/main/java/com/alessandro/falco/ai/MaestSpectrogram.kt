package com.alessandro.falco.ai

import kotlin.math.*

object MaestSpectrogram {
    const val FRAMES = 1876
    const val BANDS = 96
    private const val FFT = 512
    private const val HOP = 256
    private const val RATE = 16000
    private const val MEAN = 2.06755686098554
    private const val STD = 1.268292820667291
    private val window = FloatArray(FFT) { i -> (0.5 - 0.5 * cos(2.0 * Math.PI * i / FFT)).toFloat() }
    private val filters = melFilters()

    /** Time-major [1,1876,96], matching the official Essentia ONNX metadata. */
    fun create(audio: FloatArray): FloatArray {
        val padded = FloatArray(audio.size + FFT) { i -> audio.getOrElse(i - FFT / 2) { 0f } }
        val output = FloatArray(FRAMES * BANDS)
        val real = FloatArray(FFT); val imag = FloatArray(FFT)
        repeat(FRAMES) { frame ->
            val start = frame * HOP
            for (i in 0 until FFT) { real[i] = padded.getOrElse(start + i) { 0f } * window[i]; imag[i] = 0f }
            fft(real, imag)
            for (band in 0 until BANDS) {
                var mel = 0.0
                for (bin in 0..FFT / 2) { val power = real[bin] * real[bin] + imag[bin] * imag[bin]; mel += power * filters[band][bin] }
                val logMel = log10(1.0 + mel * 10000.0)
                output[frame * BANDS + band] = ((logMel - MEAN) / (STD * 2.0)).toFloat()
            }
        }
        return output
    }

    private fun melFilters(): Array<FloatArray> {
        fun hzToMel(hz: Double): Double { val minLogHz = 1000.0; val minLogMel = 15.0; val logStep = ln(6.4) / 27.0; return if (hz < minLogHz) hz / (200.0 / 3.0) else minLogMel + ln(hz / minLogHz) / logStep }
        fun melToHz(mel: Double): Double { val minLogHz = 1000.0; val minLogMel = 15.0; val logStep = ln(6.4) / 27.0; return if (mel < minLogMel) mel * (200.0 / 3.0) else minLogHz * exp(logStep * (mel - minLogMel)) }
        val low = hzToMel(0.0); val high = hzToMel(RATE / 2.0)
        val hz = DoubleArray(BANDS + 2) { i -> melToHz(low + (high - low) * i / (BANDS + 1)) }
        return Array(BANDS) { m -> FloatArray(FFT / 2 + 1) { bin ->
            val f = bin * RATE.toDouble() / FFT
            val triangular = when { f < hz[m] || f > hz[m + 2] -> 0.0; f <= hz[m + 1] -> (f - hz[m]) / (hz[m + 1] - hz[m]); else -> (hz[m + 2] - f) / (hz[m + 2] - hz[m + 1]) }
            (triangular * 2.0 / (hz[m + 2] - hz[m])).toFloat()
        } }
    }

    private fun fft(real: FloatArray, imag: FloatArray) {
        var j = 0
        for (i in 1 until FFT) { var bit = FFT shr 1; while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }; j = j xor bit; if (i < j) { val r = real[i]; real[i] = real[j]; real[j] = r; val q = imag[i]; imag[i] = imag[j]; imag[j] = q } }
        var len = 2
        while (len <= FFT) { val angle = -2.0 * Math.PI / len; val wr0 = cos(angle).toFloat(); val wi0 = sin(angle).toFloat(); for (base in 0 until FFT step len) { var wr = 1f; var wi = 0f; for (k in 0 until len / 2) { val u = base + k; val v = u + len / 2; val vr = real[v] * wr - imag[v] * wi; val vi = real[v] * wi + imag[v] * wr; real[v] = real[u] - vr; imag[v] = imag[u] - vi; real[u] += vr; imag[u] += vi; val next = wr * wr0 - wi * wi0; wi = wr * wi0 + wi * wr0; wr = next } }; len = len shl 1 }
    }
}
