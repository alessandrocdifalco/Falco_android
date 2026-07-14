package com.alessandro.falco.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "tracks", indices = [Index("uri", unique = true), Index("title"), Index("artist"), Index("format")])
data class TrackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uri: String,
    val folderUri: String,
    val displayName: String,
    val title: String,
    val artist: String,
    val album: String,
    val genre: String,
    val year: Int?,
    val durationMs: Long,
    val relativePath: String,
    val sizeBytes: Long,
    val modifiedAt: Long,
    val format: String,
    val customTags: String = "",
    val notes: String = "",
    val rating: Int = 0,
    val workStatus: String = "DA_VALUTARE",
    val favorite: Boolean = false,
    val lastScannedAt: Long = System.currentTimeMillis(),
    val waveformCache: String = "",
    val maestCache: String = "",
    val aiAnalyzedAt: Long = 0,
    val aiSourceModifiedAt: Long = 0,
    val aiAnalysisError: String = ""
)

data class LibraryStats(
    val count: Int = 0,
    val totalDuration: Long = 0,
    val totalBytes: Long = 0,
    val artists: Int = 0,
    val genres: Int = 0
)

data class DuplicateGroup(val signature: String, val tracks: List<TrackEntity>)

enum class WorkStatus(val label: String) {
    DA_VALUTARE("Da valutare"), DA_TAGGARE("Da taggare"), PRONTO("Pronto"), ARCHIVIATO("Archiviato")
}
