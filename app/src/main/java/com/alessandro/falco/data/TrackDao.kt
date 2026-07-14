package com.alessandro.falco.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
    @Query("SELECT * FROM tracks") fun observeAll(): Flow<List<TrackEntity>>
    @Query("SELECT * FROM tracks WHERE id = :id") fun observe(id: Long): Flow<TrackEntity?>
    @Query("SELECT * FROM tracks WHERE folderUri = :folderUri") suspend fun forFolder(folderUri: String): List<TrackEntity>
    @Query("SELECT * FROM tracks WHERE waveformCache = '' OR (maestCache = '' AND aiAnalysisError = '') OR aiSourceModifiedAt != modifiedAt ORDER BY CASE WHEN workStatus = 'DA_VALUTARE' THEN 0 ELSE 1 END, modifiedAt DESC LIMIT :limit")
    suspend fun pendingAnalysis(limit: Int): List<TrackEntity>
    @Query("SELECT COUNT(*) FROM tracks WHERE waveformCache = '' OR (maestCache = '' AND aiAnalysisError = '') OR aiSourceModifiedAt != modifiedAt") suspend fun pendingAnalysisCount(): Int
    @Upsert suspend fun upsertAll(tracks: List<TrackEntity>)
    @Query("DELETE FROM tracks WHERE folderUri = :folderUri AND uri NOT IN (:uris)") suspend fun deleteMissing(folderUri: String, uris: List<String>)
    @Query("DELETE FROM tracks WHERE folderUri = :folderUri") suspend fun deleteFolder(folderUri: String)
    @Update suspend fun update(track: TrackEntity)
    @Query("UPDATE tracks SET waveformCache = :waveform, maestCache = :maest, aiAnalyzedAt = :analyzedAt, aiSourceModifiedAt = modifiedAt, aiAnalysisError = :error WHERE id = :id")
    suspend fun updateAnalysis(id: Long, waveform: String, maest: String, analyzedAt: Long, error: String)
}
