package com.alessandro.falco.ai

import android.content.Context
import com.alessandro.falco.data.TrackEntity
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.sqrt

data class AiSuggestion(val genre: String?, val subgenre: String?, val energy: Int, val rating: Int?, val confidence: Int, val neighbors: Int, val rejectProbability: Int? = null, val rejectionNeighbors: Int = 0)
data class AiLearningStats(
    val samples: Int = 0, val decisions: Int = 0, val rejected: Int = 0,
    val genreAccuracy: Int? = null, val energyError: Float? = null,
    val recentGenreAccuracy: Int? = null, val previousGenreAccuracy: Int? = null,
    val genres: List<Pair<String, Int>> = emptyList(), val energies: List<Pair<Int, Int>> = emptyList(), val lastLearnedAt: Long = 0
)

class LocalAiEngine(context: Context) {
    private val prefs = context.getSharedPreferences("falco_local_ai", Context.MODE_PRIVATE)
    private data class Sample(val features: FloatArray, val genre: String, val subgenre: String, val energy: Int, val rating: Int, val status: String, val learnedAt: Long)
    private data class Feedback(val predictedGenre: String, val chosenGenre: String, val predictedEnergy: Int, val chosenEnergy: Int, val status: String, val at: Long)

    fun suggest(track: TrackEntity, waveform: List<Float>): AiSuggestion {
        val feature = features(track, waveform); val samples = load()
        val nearest = samples.map { it to distance(feature, it.features) }.sortedBy { it.second }.take(9)
        val energyFromAudio = ((feature[0] * 4) + 1).toInt().coerceIn(1, 5)
        val decisionNeighbors = nearest.filter { it.first.status in setOf("REJECT", "KEEP", "MAYBE", "PRONTO") }
        val rejectProbability = decisionNeighbors.takeIf { it.size >= 3 }?.let { rows -> (rows.count { it.first.status == "REJECT" } * 100f / rows.size).toInt() }
        if (nearest.size < 3) return AiSuggestion(null, null, energyFromAudio, null, (25 + nearest.size * 10).coerceAtMost(50), nearest.size, rejectProbability, decisionNeighbors.size)
        fun weight(d: Float) = 1f / (d + .08f)
        val genre = nearest.filter { it.first.genre.isNotBlank() }.groupBy { it.first.genre }.maxByOrNull { (_, rows) -> rows.sumOf { weight(it.second).toDouble() } }?.key
        val subgenre = nearest.filter { it.first.genre == genre }.groupBy { it.first.subgenre }.maxByOrNull { (_, rows) -> rows.sumOf { weight(it.second).toDouble() } }?.key
        val energyRows = nearest.filter { it.first.energy in 1..5 }; val energy = if (energyRows.isEmpty()) energyFromAudio else (energyRows.sumOf { it.first.energy * weight(it.second).toDouble() } / energyRows.sumOf { weight(it.second).toDouble() }).toInt().coerceIn(1, 5)
        val ratingRows = nearest.filter { it.first.rating in 1..5 }; val rating = ratingRows.takeIf { it.isNotEmpty() }?.let { rows -> (rows.sumOf { it.first.rating * weight(it.second).toDouble() } / rows.sumOf { weight(it.second).toDouble() }).toInt().coerceIn(1, 5) }
        val agreement = nearest.count { it.first.genre == genre }.toFloat() / nearest.size
        return AiSuggestion(genre, subgenre, energy, rating, (45 + agreement * 45).toInt(), nearest.size, rejectProbability, decisionNeighbors.size)
    }

    fun learn(track: TrackEntity, waveform: List<Float>, genre: String, subgenre: String, energy: Int, rating: Int, status: String, prediction: AiSuggestion?) {
        if (waveform.isEmpty()) return
        val now = System.currentTimeMillis()
        val all = load().takeLast(999).toMutableList().apply { add(Sample(features(track, waveform), genre, subgenre, energy.takeIf { it in 1..5 } ?: 0, rating.takeIf { it in 1..5 } ?: 0, status, now)) }
        val json = JSONArray(); all.forEach { s -> json.put(JSONObject().put("f", JSONArray(s.features.toList())).put("g", s.genre).put("s", s.subgenre).put("e", s.energy).put("r", s.rating).put("w", s.status).put("t", s.learnedAt)) }
        prefs.edit().putString("samples", json.toString()).apply()
        if (prediction != null && genre.isNotBlank()) {
            val feedback = loadFeedback().takeLast(499).toMutableList().apply { add(Feedback(prediction.genre.orEmpty(), genre, prediction.energy, energy, status, now)) }
            val out = JSONArray(); feedback.forEach { f -> out.put(JSONObject().put("pg", f.predictedGenre).put("cg", f.chosenGenre).put("pe", f.predictedEnergy).put("ce", f.chosenEnergy).put("w", f.status).put("t", f.at)) }
            prefs.edit().putString("feedback", out.toString()).apply()
        }
    }

    fun stats(): AiLearningStats {
        val samples = load(); val feedback = loadFeedback(); val comparable = feedback.filter { it.predictedGenre.isNotBlank() }
        fun accuracy(rows: List<Feedback>) = rows.takeIf { it.isNotEmpty() }?.let { values -> values.count { it.predictedGenre == it.chosenGenre } * 100 / values.size }
        val recent = comparable.takeLast(30); val previous = comparable.dropLast(recent.size).takeLast(30)
        val energyRows = feedback.filter { it.chosenEnergy in 1..5 }
        return AiLearningStats(samples.size, samples.count { it.status.isNotBlank() }, samples.count { it.status == "REJECT" }, accuracy(comparable), energyRows.takeIf { it.isNotEmpty() }?.map { kotlin.math.abs(it.predictedEnergy - it.chosenEnergy) }?.average()?.toFloat(), accuracy(recent), accuracy(previous), samples.filter { it.genre.isNotBlank() }.groupingBy { it.genre }.eachCount().entries.sortedByDescending { it.value }.take(8).map { it.key to it.value }, samples.filter { it.energy in 1..5 }.groupingBy { it.energy }.eachCount().entries.sortedBy { it.key }.map { it.key to it.value }, samples.maxOfOrNull { it.learnedAt } ?: 0)
    }

    private fun features(track: TrackEntity, w: List<Float>): FloatArray {
        val data = w.ifEmpty { listOf(0f) }; val mean = data.average().toFloat(); val max = data.maxOrNull() ?: 0f
        val variance = data.map { (it - mean) * (it - mean) }.average().toFloat(); val changes = data.zipWithNext().map { kotlin.math.abs(it.second - it.first) }.average().toFloat()
        val duration = (track.durationMs / 600_000f).coerceIn(0f, 1f); val density = if (track.durationMs > 0) (track.sizeBytes / track.durationMs.toFloat() / 80f).coerceIn(0f, 1f) else 0f
        return floatArrayOf(mean, max, sqrt(variance), changes, duration, density)
    }
    private fun distance(a: FloatArray, b: FloatArray) = sqrt(a.indices.sumOf { val d = a[it] - b.getOrElse(it) { 0f }; (d * d).toDouble() }).toFloat()
    private fun load(): List<Sample> = runCatching { val arr = JSONArray(prefs.getString("samples", "[]")); List(arr.length()) { i -> val o = arr.getJSONObject(i); val f = o.getJSONArray("f"); Sample(FloatArray(f.length()) { f.getDouble(it).toFloat() }, o.optString("g"), o.optString("s", o.optString("g")), o.optInt("e"), o.optInt("r"), o.optString("w"), o.optLong("t")) } }.getOrDefault(emptyList())
    private fun loadFeedback(): List<Feedback> = runCatching { val arr = JSONArray(prefs.getString("feedback", "[]")); List(arr.length()) { i -> arr.getJSONObject(i).let { Feedback(it.optString("pg"), it.optString("cg"), it.optInt("pe"), it.optInt("ce"), it.optString("w"), it.optLong("t")) } } }.getOrDefault(emptyList())
}
