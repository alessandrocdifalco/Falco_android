package com.alessandro.falco.ai

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

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

    fun state(): MaestModelState = if (modelFile.isFile && modelFile.length() > MIN_MODEL_BYTES) {
        MaestModelState(installed = true, sizeBytes = modelFile.length(), message = "MAEST 30s pronto")
    } else MaestModelState()

    suspend fun download(onProgress: (MaestModelState) -> Unit): Result<MaestModelState> = withContext(Dispatchers.IO) {
        runCatching {
            modelDir.mkdirs()
            val partial = File(modelDir, "${modelFile.name}.part")
            val request = Request.Builder().url(MODEL_URL).get().build()
            OkHttpClient.Builder().followRedirects(true).followSslRedirects(true).build().newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("Download MAEST: HTTP ${response.code}")
                val body = response.body ?: error("Il server non ha restituito il modello")
                val total = body.contentLength()
                partial.outputStream().buffered().use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 4)
                        var copied = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            copied += count
                            val percent = if (total > 0) (copied * 100 / total).toInt() else 0
                            onProgress(MaestModelState(downloading = true, progress = percent, sizeBytes = copied, message = "Download MAEST $percent%"))
                        }
                    }
                }
                if (partial.length() <= MIN_MODEL_BYTES) error("File MAEST incompleto (${partial.length()} byte)")
                if (modelFile.exists()) modelFile.delete()
                if (!partial.renameTo(modelFile)) error("Impossibile installare il modello")
            }
            state()
        }.onFailure { File(modelDir, "${modelFile.name}.part").delete() }
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
