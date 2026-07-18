package com.alessandro.falco.desktop

import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension

object LocalScanner {
    private val formats = setOf("mp3", "flac", "wav", "m4a", "aac", "ogg", "aiff", "aif")

    fun scan(root: Path, onTrack: (Track) -> Unit) {
        Files.walk(root).use { paths -> paths.filter(Files::isRegularFile).filter { it.extension.lowercase() in formats }.forEach { path ->
            val audio = runCatching { AudioFileIO.read(path.toFile()) }.getOrNull()
            val tag = audio?.tag
            fun field(key: FieldKey) = runCatching { tag?.getFirst(key).orEmpty() }.getOrDefault("")
            onTrack(Track(
                source = "LOCAL", uri = path.toUri().toString(), title = field(FieldKey.TITLE).ifBlank { path.nameWithoutExtension },
                artist = field(FieldKey.ARTIST), album = field(FieldKey.ALBUM), genre = field(FieldKey.GENRE),
                year = field(FieldKey.YEAR).take(4).toIntOrNull(), durationMs = (audio?.audioHeader?.trackLength ?: 0) * 1000L,
                sizeBytes = Files.size(path), modifiedAt = Files.getLastModifiedTime(path).toMillis(), format = path.extension.uppercase()
            ))
        } }
    }
}

