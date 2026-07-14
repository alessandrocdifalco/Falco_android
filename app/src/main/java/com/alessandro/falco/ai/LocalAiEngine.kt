package com.alessandro.falco.ai

import android.content.Context
import com.alessandro.falco.data.TrackEntity
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.sqrt

data class AiSuggestion(val genre: String?, val energy: Int, val rating: Int?, val confidence: Int, val neighbors: Int)

class LocalAiEngine(context: Context) {
    private val prefs = context.getSharedPreferences("falco_local_ai", Context.MODE_PRIVATE)
    private data class Sample(val features: FloatArray, val genre: String, val energy: Int, val rating: Int)

    fun suggest(track: TrackEntity, waveform: List<Float>): AiSuggestion {
        val feature = features(track, waveform); val samples = load()
        val nearest = samples.map { it to distance(feature, it.features) }.sortedBy { it.second }.take(9)
        val energyFromAudio = ((feature[0] * 4) + 1).toInt().coerceIn(1, 5)
        if (nearest.size < 3) return AiSuggestion(null, energyFromAudio, null, (25 + nearest.size * 10).coerceAtMost(50), nearest.size)
        fun weight(d: Float) = 1f / (d + .08f)
        val genre = nearest.groupBy { it.first.genre }.maxByOrNull { (_, rows) -> rows.sumOf { weight(it.second).toDouble() } }?.key
        val energy = (nearest.sumOf { it.first.energy * weight(it.second).toDouble() } / nearest.sumOf { weight(it.second).toDouble() }).toInt().coerceIn(1, 5)
        val rating = (nearest.sumOf { it.first.rating * weight(it.second).toDouble() } / nearest.sumOf { weight(it.second).toDouble() }).toInt().coerceIn(1, 5)
        val agreement = nearest.count { it.first.genre == genre }.toFloat() / nearest.size
        return AiSuggestion(genre, energy, rating, (45 + agreement * 45).toInt(), nearest.size)
    }

    fun learn(track: TrackEntity, waveform: List<Float>, genre: String, energy: Int, rating: Int) {
        if (waveform.isEmpty() || genre.isBlank() || rating == 0) return
        val all = load().takeLast(999).toMutableList().apply { add(Sample(features(track, waveform), genre, energy.coerceIn(1, 5), rating.coerceIn(1, 5))) }
        val json = JSONArray(); all.forEach { s -> json.put(JSONObject().put("f", JSONArray(s.features.toList())).put("g", s.genre).put("e", s.energy).put("r", s.rating)) }
        prefs.edit().putString("samples", json.toString()).apply()
    }

    private fun features(track: TrackEntity, w: List<Float>): FloatArray {
        val data = w.ifEmpty { listOf(0f) }; val mean = data.average().toFloat(); val max = data.maxOrNull() ?: 0f
        val variance = data.map { (it - mean) * (it - mean) }.average().toFloat(); val changes = data.zipWithNext().map { kotlin.math.abs(it.second - it.first) }.average().toFloat()
        val duration = (track.durationMs / 600_000f).coerceIn(0f, 1f); val density = if (track.durationMs > 0) (track.sizeBytes / track.durationMs.toFloat() / 80f).coerceIn(0f, 1f) else 0f
        return floatArrayOf(mean, max, sqrt(variance), changes, duration, density)
    }
    private fun distance(a: FloatArray, b: FloatArray) = sqrt(a.indices.sumOf { val d = a[it] - b.getOrElse(it) { 0f }; (d * d).toDouble() }).toFloat()
    private fun load(): List<Sample> = runCatching { val arr = JSONArray(prefs.getString("samples", "[]")); List(arr.length()) { i -> val o = arr.getJSONObject(i); val f = o.getJSONArray("f"); Sample(FloatArray(f.length()) { f.getDouble(it).toFloat() }, o.getString("g"), o.getInt("e"), o.getInt("r")) } }.getOrDefault(emptyList())
}
