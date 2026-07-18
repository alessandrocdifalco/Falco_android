package com.alessandro.falco.audio

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.alessandro.falco.data.TrackEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

class CoverArtExtractor(private val context: Context) {
    suspend fun extract(track: TrackEntity, authorization: String?): ByteArray? = withContext(Dispatchers.IO) {
        val cache = File(context.cacheDir, "covers/${track.uri.sha256()}.jpg")
        if (cache.exists() && cache.length() > 0) return@withContext cache.readBytes()
        val retriever = MediaMetadataRetriever()
        try {
            if (track.uri.startsWith("http")) retriever.setDataSource(track.uri, authorization?.let { mapOf("Authorization" to it) }.orEmpty())
            else retriever.setDataSource(context, Uri.parse(track.uri))
            retriever.embeddedPicture?.also { bytes ->
                cache.parentFile?.mkdirs(); runCatching { cache.writeBytes(bytes) }
            }
        } catch (_: Throwable) { null }
        finally { runCatching { retriever.release() } }
    }
}

private fun String.sha256(): String = MessageDigest.getInstance("SHA-256")
    .digest(toByteArray()).joinToString("") { "%02x".format(it) }
