package com.alessandro.falco.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.Flow

class MusicRepository(context: Context) {
    private val dao = FalcoDatabase.get(context).tracks()
    private val folders = FolderStore(context)
    private val scanner = AudioScanner(context)
    val tracks: Flow<List<TrackEntity>> = dao.observeAll()
    fun folders() = folders.all()

    fun persistFolder(context: Context, uri: Uri) {
        context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        folders.add(uri)
    }

    suspend fun scanAll(progress: (ScanProgress) -> Unit) {
        folders.all().forEach { folder ->
            val existing = dao.forFolder(folder).associateBy { it.uri }
            val scanned = scanner.scan(folder, progress).map { fresh ->
                existing[fresh.uri]?.let { old -> fresh.copy(id = old.id, customTags = old.customTags, notes = old.notes, rating = old.rating, workStatus = old.workStatus, favorite = old.favorite) } ?: fresh
            }
            dao.upsertAll(scanned)
            if (scanned.isEmpty()) dao.deleteFolder(folder) else dao.deleteMissing(folder, scanned.map { it.uri })
        }
    }

    suspend fun removeFolder(uri: String) { folders.remove(uri); dao.deleteFolder(uri) }
    suspend fun update(track: TrackEntity) = dao.update(track)
}
