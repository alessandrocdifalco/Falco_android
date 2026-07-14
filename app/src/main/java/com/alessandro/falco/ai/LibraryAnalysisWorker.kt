package com.alessandro.falco.ai

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import com.alessandro.falco.MainActivity
import com.alessandro.falco.R
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
        setForeground(notification(0, initialPending, "Preparazione analisi…"))
        tracks.forEachIndexed { index, track ->
            if (isStopped) return Result.retry()
            setProgress(Data.Builder().putInt(KEY_DONE, index).putInt(KEY_TOTAL, initialPending).putString(KEY_TRACK, track.title).build())
            setForeground(notification(index, initialPending, track.title))
            val waveform = if (track.waveformCache.isBlank()) runCatching { WaveformExtractor(applicationContext).extract(track, authorization) }.getOrDefault(emptyList()) else AiCache.decodeWaveform(track.waveformCache)
            if (track.maestCache.isNotBlank() && track.aiSourceModifiedAt == track.modifiedAt) {
                dao.updateAnalysis(track.id, AiCache.encodeWaveform(waveform), track.maestCache, track.aiAnalyzedAt, track.aiAnalysisError)
            } else runCatching { MaestEngine(applicationContext).analyze(track, authorization) }
                .onSuccess { prediction -> dao.updateAnalysis(track.id, AiCache.encodeWaveform(waveform), AiCache.encodePrediction(prediction), System.currentTimeMillis(), "") }
                .onFailure { error -> dao.updateAnalysis(track.id, AiCache.encodeWaveform(waveform), "", System.currentTimeMillis(), error.message ?: "Analisi fallita") }
        }
        val remaining = dao.pendingAnalysisCount()
        setProgress(Data.Builder().putInt(KEY_DONE, initialPending - remaining).putInt(KEY_TOTAL, initialPending).build())
        return if (remaining > 0) Result.retry() else Result.success()
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = notification(0, 0, "Preparazione analisi…")

    private fun notification(done: Int, total: Int, track: String): ForegroundInfo {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Analisi libreria", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Avanzamento dell'analisi audio locale di FALCO"
                setShowBadge(false)
            })
        }
        val openApp = PendingIntent.getActivity(applicationContext, 0, Intent(applicationContext, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val cancel = WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)
        val progressMax = total.coerceAtLeast(1)
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("FALCO analizza la libreria")
            .setContentText(if (total > 0) "${done.coerceAtMost(total)}/$total • $track" else track)
            .setStyle(NotificationCompat.BigTextStyle().bigText(if (total > 0) "${done.coerceAtMost(total)}/$total • $track" else track))
            .setProgress(progressMax, done.coerceIn(0, progressMax), total <= 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Interrompi", cancel)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(NOTIFICATION_ID, notification)
    }

    companion object {
        const val UNIQUE_NAME = "falco-library-analysis"
        const val KEY_DONE = "done"; const val KEY_TOTAL = "total"; const val KEY_TRACK = "track"; const val KEY_ERROR = "error"
        private const val CHANNEL_ID = "falco_library_analysis"
        private const val NOTIFICATION_ID = 4107
        private const val BATCH_SIZE = 12
    }
}
