package com.alessandro.falco.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class BackupManager(private val context: Context) {
    suspend fun write(uri: Uri, tracks: List<TrackEntity>): Int = withContext(Dispatchers.IO) {
        val rows = JSONArray()
        tracks.forEach { t -> rows.put(JSONObject().apply {
            put("uri", t.uri); put("displayName", t.displayName); put("sizeBytes", t.sizeBytes); put("durationMs", t.durationMs)
            put("customTags", t.customTags); put("notes", t.notes); put("genre", t.genre); put("rating", t.rating)
            put("workStatus", t.workStatus); put("favorite", t.favorite); put("waveformCache", t.waveformCache)
            put("maestCache", t.maestCache); put("aiAnalyzedAt", t.aiAnalyzedAt); put("aiSourceModifiedAt", t.aiSourceModifiedAt)
            put("aiAnalysisError", t.aiAnalysisError)
        }) }
        val root = JSONObject().put("format", "falco-backup").put("version", 1).put("createdAt", System.currentTimeMillis()).put("tracks", rows)
        context.contentResolver.openOutputStream(uri, "rwt")!!.bufferedWriter().use { it.write(root.toString()) }
        tracks.size
    }

    suspend fun restore(uri: Uri, current: List<TrackEntity>): List<TrackEntity> = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
        val root = JSONObject(text)
        require(root.optString("format") == "falco-backup") { "File di backup FALCO non valido" }
        val rows = root.getJSONArray("tracks")
        val byUri = current.associateBy { it.uri }
        val byFingerprint = current.groupBy { "${it.displayName}|${it.sizeBytes}|${it.durationMs}" }
        buildList {
            for (i in 0 until rows.length()) {
                val row = rows.getJSONObject(i)
                val track = byUri[row.optString("uri")] ?: byFingerprint["${row.optString("displayName")}|${row.optLong("sizeBytes")}|${row.optLong("durationMs")}"]?.singleOrNull() ?: continue
                add(track.copy(
                    customTags = row.optString("customTags"), notes = row.optString("notes"), genre = row.optString("genre", track.genre),
                    rating = row.optInt("rating"), workStatus = row.optString("workStatus", track.workStatus), favorite = row.optBoolean("favorite"),
                    waveformCache = row.optString("waveformCache"), maestCache = row.optString("maestCache"), aiAnalyzedAt = row.optLong("aiAnalyzedAt"),
                    aiSourceModifiedAt = row.optLong("aiSourceModifiedAt"), aiAnalysisError = row.optString("aiAnalysisError")
                ))
            }
        }
    }
}
