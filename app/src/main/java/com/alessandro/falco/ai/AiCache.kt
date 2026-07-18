package com.alessandro.falco.ai

import org.json.JSONArray
import org.json.JSONObject

object AiCache {
    fun encodeWaveform(values: List<Float>) = if (values.isEmpty()) "~" else values.joinToString(",") { "%.4f".format(java.util.Locale.US, it) }
    fun decodeWaveform(value: String): List<Float> = if (value.isBlank()) emptyList() else value.split(',').mapNotNull(String::toFloatOrNull)

    fun encodePrediction(value: MaestPrediction): String {
        val styles = JSONArray()
        value.styles.forEach { styles.put(JSONObject().put("label", it.label).put("score", it.score.toDouble())) }
        return JSONObject().put("styles", styles).put("genre", value.genre).put("subgenre", value.subgenre)
            .put("confidence", value.confidence).put("elapsedMs", value.elapsedMs).toString()
    }

    fun decodePrediction(value: String): MaestPrediction? = runCatching {
        if (value.isBlank()) return null
        val json = JSONObject(value); val array = json.getJSONArray("styles")
        MaestPrediction(
            styles = List(array.length()) { i -> array.getJSONObject(i).let { MaestStyle(it.getString("label"), it.getDouble("score").toFloat()) } },
            genre = json.optString("genre").takeIf { it.isNotBlank() && it != "null" },
            subgenre = json.optString("subgenre").takeIf { it.isNotBlank() && it != "null" },
            confidence = json.optInt("confidence"), elapsedMs = json.optLong("elapsedMs")
        )
    }.getOrNull()
}
