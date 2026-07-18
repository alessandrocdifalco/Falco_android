package com.alessandro.falco.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ScanProgress(val folder: String, val scanned: Int, val current: String)

class AudioScanner(private val context: Context) {
    private val extensions = setOf("mp3", "flac", "wav", "m4a", "aac", "ogg", "aif", "aiff")

    suspend fun scan(folderUri: String, progress: (ScanProgress) -> Unit): List<TrackEntity> = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, Uri.parse(folderUri)) ?: error("Cartella non accessibile")
        val files = buildList { walk(root, this) }
        files.mapIndexedNotNull { index, file ->
            progress(ScanProgress(root.name ?: "Cartella", index + 1, file.name.orEmpty()))
            read(file, folderUri, root.name.orEmpty())
        }
    }

    private fun walk(node: DocumentFile, out: MutableList<DocumentFile>) {
        node.listFiles().forEach { if (it.isDirectory) walk(it, out) else if (it.extension() in extensions) out += it }
    }

    private fun DocumentFile.extension() = name?.substringAfterLast('.', "")?.lowercase().orEmpty()

    private fun read(file: DocumentFile, folderUri: String, rootName: String): TrackEntity? = runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, file.uri)
            fun meta(key: Int) = retriever.extractMetadata(key)?.trim().orEmpty()
            val fileName = file.name ?: "Senza nome"
            TrackEntity(
                uri = file.uri.toString(), folderUri = folderUri, displayName = fileName,
                title = meta(MediaMetadataRetriever.METADATA_KEY_TITLE).ifBlank { fileName.substringBeforeLast('.') },
                artist = meta(MediaMetadataRetriever.METADATA_KEY_ARTIST).ifBlank { "Artista sconosciuto" },
                album = meta(MediaMetadataRetriever.METADATA_KEY_ALBUM).ifBlank { "Album sconosciuto" },
                genre = meta(MediaMetadataRetriever.METADATA_KEY_GENRE).ifBlank { "Senza genere" },
                year = meta(MediaMetadataRetriever.METADATA_KEY_YEAR).take(4).toIntOrNull(),
                durationMs = meta(MediaMetadataRetriever.METADATA_KEY_DURATION).toLongOrNull() ?: 0,
                relativePath = "$rootName/$fileName", sizeBytes = file.length(), modifiedAt = file.lastModified(),
                format = file.extension().uppercase().replace("AIF", "AIFF")
            )
        } finally { retriever.release() }
    }.getOrNull()
}
