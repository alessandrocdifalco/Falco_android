package com.alessandro.falco.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
    @Query("SELECT * FROM tracks") fun observeAll(): Flow<List<TrackEntity>>
    @Query("SELECT * FROM tracks WHERE id = :id") fun observe(id: Long): Flow<TrackEntity?>
    @Query("SELECT * FROM tracks WHERE folderUri = :folderUri") suspend fun forFolder(folderUri: String): List<TrackEntity>
    @Upsert suspend fun upsertAll(tracks: List<TrackEntity>)
    @Query("DELETE FROM tracks WHERE folderUri = :folderUri AND uri NOT IN (:uris)") suspend fun deleteMissing(folderUri: String, uris: List<String>)
    @Query("DELETE FROM tracks WHERE folderUri = :folderUri") suspend fun deleteFolder(folderUri: String)
    @Update suspend fun update(track: TrackEntity)
}
