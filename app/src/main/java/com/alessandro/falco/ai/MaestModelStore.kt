package com.alessandro.falco.ai

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

data class MaestModelState(
    val installed: Boolean = false,
    val downloading: Boolean = false,
    val progress: Int = 0,
    val sizeBytes: Long = 0,
    val message: String = "Modello non installato"
)

/** Stores the private, non-commercial MAEST model. It never writes to music sources or WebDAV. */
class MaestModelStore(private val context: Context) {
    private val modelDir = File(context.filesDir, "ai_models")
    val modelFile = File(modelDir, "discogs-maest-30s-pw-519l-2.onnx")

    fun state(): MaestModelState {
        if (modelFile.isFile && modelFile.length() > MIN_MODEL_BYTES) {
            return MaestModelState(installed = true, sizeBytes = modelFile.length(), message = "MAEST 30s pronto")
        }
        val partial = File(modelDir, "${modelFile.name}.part")
        return if (partial.isFile && partial.length() > 0) MaestModelState(
            sizeBytes = partial.length(),
            message = "Download interrotto: premi per riprendere da ${partial.length() / 1024 / 1024} MB"
        ) else MaestModelState()
    }

    suspend fun download(onProgress: (MaestModelState) -> Unit): Result<MaestModelState> = withContext(Dispatchers.IO) {
        runCatching {
            modelDir.mkdirs()
            val partial = File(modelDir, "${modelFile.name}.part")
            val alreadyDownloaded = partial.takeIf { it.isFile }?.length() ?: 0L
            val request = Request.Builder().url(MODEL_URL).get().apply {
                if (alreadyDownloaded > 0) header("Range", "bytes=$alreadyDownloaded-")
            }.build()
            val client = OkHttpClient.Builder()
                .followRedirects(true)
                .followSslRedirects(true)
                .retryOnConnectionFailure(true)
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .callTimeout(0, TimeUnit.MILLISECONDS)
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("Download MAEST: HTTP ${response.code}")
                val body = response.body ?: error("Il server non ha restituito il modello")
                val resumed = alreadyDownloaded > 0 && response.code == 206
                if (alreadyDownloaded > 0 && !resumed) partial.delete()
                val base = if (resumed) alreadyDownloaded else 0L
                val remaining = body.contentLength()
                val total = if (remaining > 0) base + remaining else -1L
                partial.outputStream(append = resumed).buffered().use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 4)
                        var copied = base
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            copied += count
                            val percent = if (total > 0) (copied * 100 / total).toInt().coerceIn(0, 100) else 0
                            onProgress(MaestModelState(downloading = true, progress = percent, sizeBytes = copied, message = "Download MAEST $percent%"))
                        }
                    }
                }
                if (partial.length() <= MIN_MODEL_BYTES) error("File MAEST incompleto (${partial.length()} byte)")
                if (modelFile.exists()) modelFile.delete()
                if (!partial.renameTo(modelFile)) error("Impossibile installare il modello")
            }
            state()
        }
    }

    fun remove() {
        modelFile.delete()
        File(modelDir, "${modelFile.name}.part").delete()
    }

    companion object {
        const val MODEL_URL = "https://essentia.upf.edu/models/feature-extractors/maest/discogs-maest-30s-pw-519l-2.onnx"
        private const val MIN_MODEL_BYTES = 10L * 1024 * 1024
    }
}
