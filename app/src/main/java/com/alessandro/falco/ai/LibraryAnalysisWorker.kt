package com.alessandro.falco.ai

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.alessandro.falco.audio.WaveformExtractor
import com.alessandro.falco.data.FalcoDatabase
import com.alessandro.falco.webdav.WebDavClient
import com.alessandro.falco.webdav.WebDavStore

class LibraryAnalysisWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val dao = FalcoDatabase.get(applicationContext).tracks()
        val model = MaestModelStore(applicationContext)
        if (!model.state().installed) return Result.failure(Data.Builder().putString(KEY_ERROR, "Modello MAEST non installato").build())
        val config = WebDavStore(applicationContext).load()
        val authorization = config.takeIf { it.ready }?.let { WebDavClient(it).authorization() }
        val tracks = dao.pendingAnalysis(BATCH_SIZE)
        val initialPending = dao.pendingAnalysisCount()
        if (tracks.isEmpty()) return Result.success()
        tracks.forEachIndexed { index, track ->
            if (isStopped) return Result.retry()
            setProgress(Data.Builder().putInt(KEY_DONE, index).putInt(KEY_TOTAL, initialPending).putString(KEY_TRACK, track.title).build())
            val waveform = runCatching { WaveformExtractor(applicationContext).extract(track, authorization) }.getOrDefault(emptyList())
            runCatching { MaestEngine(applicationContext).analyze(track, authorization) }
                .onSuccess { prediction -> dao.updateAnalysis(track.id, AiCache.encodeWaveform(waveform), AiCache.encodePrediction(prediction), System.currentTimeMillis(), "") }
                .onFailure { error -> dao.updateAnalysis(track.id, AiCache.encodeWaveform(waveform), "", System.currentTimeMillis(), error.message ?: "Analisi fallita") }
        }
        val remaining = dao.pendingAnalysisCount()
        setProgress(Data.Builder().putInt(KEY_DONE, initialPending - remaining).putInt(KEY_TOTAL, initialPending).build())
        return if (remaining > 0) Result.retry() else Result.success()
    }

    companion object {
        const val UNIQUE_NAME = "falco-library-analysis"
        const val KEY_DONE = "done"; const val KEY_TOTAL = "total"; const val KEY_TRACK = "track"; const val KEY_ERROR = "error"
        private const val BATCH_SIZE = 12
    }
}
