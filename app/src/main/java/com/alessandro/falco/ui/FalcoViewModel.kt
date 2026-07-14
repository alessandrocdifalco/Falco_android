@file:androidx.media3.common.util.UnstableApi

package com.alessandro.falco.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.alessandro.falco.data.*
import com.alessandro.falco.webdav.*
import com.alessandro.falco.audio.WaveformExtractor
import com.alessandro.falco.ai.*
import okhttp3.OkHttpClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class SortMode(val label: String) { TITLE("Titolo"), ARTIST("Artista"), DATE("Data"), DURATION("Durata"), SIZE("Dimensione") }
data class Filters(val genre: String? = null, val artist: String? = null, val format: String? = null, val year: Int? = null, val rating: Int? = null, val status: String? = null, val favorites: Boolean = false)
data class UiState(
    val tracks: List<TrackEntity> = emptyList(), val query: String = "", val filters: Filters = Filters(), val sort: SortMode = SortMode.TITLE,
    val loading: Boolean = true, val scanning: Boolean = false, val scanProgress: ScanProgress? = null, val error: String? = null,
    val selected: TrackEntity? = null, val playing: TrackEntity? = null, val isPlaying: Boolean = false, val position: Long = 0,
    val webDavConfig: WebDavConfig = WebDavConfig(), val webDavItems: List<WebDavItem> = emptyList(), val webDavPath: String = "/", val webDavBusy: Boolean = false, val webDavMessage: String? = null,
    val playbackDuration: Long = 0, val lastReviewed: TrackEntity? = null, val waveform: List<Float> = emptyList(), val waveformLoading: Boolean = false, val aiSuggestion: AiSuggestion? = null,
    val maest: MaestModelState = MaestModelState(), val maestPrediction: MaestPrediction? = null, val maestAnalyzing: Boolean = false, val maestError: String? = null,
    val libraryAnalyzing: Boolean = false, val libraryAnalysisDone: Int = 0, val libraryAnalysisTotal: Int = 0, val libraryAnalysisTrack: String = ""
) {
    val visible: List<TrackEntity> get() {
        val f = tracks.filter { t ->
            (query.isBlank() || listOf(t.title, t.artist, t.album, t.genre, t.customTags).any { it.contains(query, true) }) &&
                (filters.genre == null || t.genre == filters.genre) && (filters.artist == null || t.artist == filters.artist) &&
                (filters.format == null || t.format == filters.format) && (filters.year == null || t.year == filters.year) &&
                (filters.rating == null || t.rating >= filters.rating) && (filters.status == null || t.workStatus == filters.status) &&
                (!filters.favorites || t.favorite)
        }
        return when (sort) { SortMode.TITLE -> f.sortedBy { it.title.lowercase() }; SortMode.ARTIST -> f.sortedBy { it.artist.lowercase() }; SortMode.DATE -> f.sortedByDescending { it.modifiedAt }; SortMode.DURATION -> f.sortedByDescending { it.durationMs }; SortMode.SIZE -> f.sortedByDescending { it.sizeBytes } }
    }
    val stats get() = LibraryStats(tracks.size, tracks.sumOf { it.durationMs }, tracks.sumOf { it.sizeBytes }, tracks.map { it.artist }.distinct().size, tracks.map { it.genre }.distinct().size)
    val duplicates get() = tracks.groupBy { "${it.title.lowercase().trim()}|${it.artist.lowercase().trim()}|${it.durationMs / 2000}" }.filterValues { it.size > 1 }.map { DuplicateGroup(it.key, it.value) }
}

class FalcoViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = MusicRepository(app)
    private val localAi = LocalAiEngine(app)
    private val maestStore = MaestModelStore(app)
    private val maestEngine = MaestEngine(app)
    private val workManager = WorkManager.getInstance(app)
    private var player = createPlayer(app, repo.webDavConfig())
    private val mutable = MutableStateFlow(UiState(webDavConfig = repo.webDavConfig(), maest = maestStore.state()))
    val state: StateFlow<UiState> = mutable.asStateFlow()
    init {
        viewModelScope.launch { repo.tracks.collect { mutable.update { s -> s.copy(tracks = it, loading = false, selected = s.selected?.let { old -> it.find { n -> n.id == old.id } }) } } }
        attachPlayerListener()
        viewModelScope.launch { while (true) { mutable.update { it.copy(position = player.currentPosition.coerceAtLeast(0)) }; delay(500) } }
        viewModelScope.launch {
            workManager.getWorkInfosForUniqueWorkFlow(LibraryAnalysisWorker.UNIQUE_NAME).collect { jobs ->
                val job = jobs.lastOrNull(); val progress = job?.progress
                mutable.update { state -> state.copy(
                    libraryAnalyzing = job?.state == WorkInfo.State.RUNNING || job?.state == WorkInfo.State.ENQUEUED,
                    libraryAnalysisDone = progress?.getInt(LibraryAnalysisWorker.KEY_DONE, 0) ?: 0,
                    libraryAnalysisTotal = progress?.getInt(LibraryAnalysisWorker.KEY_TOTAL, 0) ?: 0,
                    libraryAnalysisTrack = progress?.getString(LibraryAnalysisWorker.KEY_TRACK).orEmpty()
                ) }
            }
        }
    }
    fun folders() = repo.folders()
    fun addFolder(uri: Uri) { runCatching { repo.persistFolder(getApplication(), uri) }.onFailure { fail(it) }; scan() }
    fun removeFolder(uri: String) = viewModelScope.launch { repo.removeFolder(uri) }
    fun scan() = viewModelScope.launch {
        mutable.update { it.copy(scanning = true, error = null) }
        runCatching { repo.scanAll { p -> mutable.update { it.copy(scanProgress = p) } } }.onFailure(::fail)
        mutable.update { it.copy(scanning = false, scanProgress = null) }
    }
    fun query(v: String) = mutable.update { it.copy(query = v) }
    fun filters(v: Filters) = mutable.update { it.copy(filters = v) }
    fun sort(v: SortMode) = mutable.update { it.copy(sort = v) }
    fun select(v: TrackEntity?) = mutable.update { it.copy(selected = v) }
    fun save(v: TrackEntity) = viewModelScope.launch { repo.update(v); mutable.update { it.copy(selected = v) } }
    fun toggleFavorite(v: TrackEntity) = save(v.copy(favorite = !v.favorite))
    fun play(v: TrackEntity) = playFrom(v, null)
    private fun playFrom(v: TrackEntity, startAtMs: Long?) {
        if (mutable.value.playing?.id == v.id) { if (player.isPlaying) player.pause() else player.play(); return }
        player.setMediaItem(MediaItem.fromUri(v.uri)); startAtMs?.let(player::seekTo); player.prepare(); player.play(); mutable.update { it.copy(playing = v, position = startAtMs ?: 0) }
    }
    fun seek(v: Long) = player.seekTo(v)
    fun skip(delta: Long) = player.seekTo((player.currentPosition + delta).coerceIn(0, player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE))
    fun preview(v: TrackEntity) = playFrom(v, 60_000L)
    fun review(v: TrackEntity, status: String, genre: String = v.genre, rating: Int = v.rating, tags: Set<String> = v.customTags.split(',').filter { it.isNotBlank() }.toSet()) = viewModelScope.launch {
        val energy = tags.firstOrNull { it.matches(Regex("E[1-5]")) }?.drop(1)?.toIntOrNull() ?: mutable.value.aiSuggestion?.energy ?: 3
        val subgenre = tags.firstOrNull { it.startsWith("SUB:") }?.removePrefix("SUB:") ?: genre
        localAi.learn(v, mutable.value.waveform, genre, subgenre, energy, rating); mutable.update { it.copy(lastReviewed = v) }; repo.update(v.copy(workStatus = status, genre = genre, rating = rating, customTags = tags.joinToString(",")))
    }
    fun undoReview() = viewModelScope.launch { mutable.value.lastReviewed?.let { repo.update(it); mutable.update { s -> s.copy(lastReviewed = null) } } }
    fun downloadMaest() = viewModelScope.launch {
        if (mutable.value.maest.downloading) return@launch
        mutable.update { it.copy(maest = MaestModelState(downloading = true, message = "Avvio download MAEST…")) }
        maestStore.download { progress -> mutable.update { it.copy(maest = progress) } }
            .onSuccess { ready -> mutable.update { it.copy(maest = ready) } }
            .onFailure { error -> mutable.update { it.copy(maest = maestStore.state().copy(message = "${error.message ?: "Download interrotto"}. Premi per riprendere.")) } }
    }
    fun removeMaest() { maestStore.remove(); mutable.update { it.copy(maest = maestStore.state()) } }
    fun startLibraryAnalysis() {
        if (!maestStore.state().installed) { mutable.update { it.copy(error = "Installa prima il modello MAEST") }; return }
        val request = OneTimeWorkRequestBuilder<LibraryAnalysisWorker>()
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build()).build()
        workManager.enqueueUniqueWork(LibraryAnalysisWorker.UNIQUE_NAME, ExistingWorkPolicy.REPLACE, request)
    }
    fun cancelLibraryAnalysis() = workManager.cancelUniqueWork(LibraryAnalysisWorker.UNIQUE_NAME)
    fun loadWaveform(track: TrackEntity) = viewModelScope.launch {
        mutable.update { it.copy(waveform = emptyList(), waveformLoading = true, aiSuggestion = null, maestPrediction = null, maestError = null) }
        val cachedWaveform = AiCache.decodeWaveform(track.waveformCache)
        val cachedPrediction = AiCache.decodePrediction(track.maestCache)
        if (cachedWaveform.isNotEmpty() || cachedPrediction != null) {
            mutable.update { it.copy(waveform = cachedWaveform, waveformLoading = false, aiSuggestion = localAi.suggest(track, cachedWaveform), maestPrediction = cachedPrediction, maestError = track.aiAnalysisError.takeIf(String::isNotBlank)) }
            return@launch
        }
        val auth = mutable.value.webDavConfig.takeIf { track.uri.startsWith("http") && it.ready }?.let { WebDavClient(it).authorization() }
        val peaks = runCatching { WaveformExtractor(getApplication()).extract(track, auth) }.getOrDefault(emptyList())
        mutable.update { it.copy(waveform = peaks, waveformLoading = false, aiSuggestion = localAi.suggest(track, peaks)) }
        if (maestStore.state().installed) {
            mutable.update { it.copy(maestAnalyzing = true) }
            runCatching { maestEngine.analyze(track, auth) }
                .onSuccess { prediction ->
                    repo.update(track.copy(waveformCache = AiCache.encodeWaveform(peaks), maestCache = AiCache.encodePrediction(prediction), aiAnalyzedAt = System.currentTimeMillis(), aiSourceModifiedAt = track.modifiedAt, aiAnalysisError = ""))
                    mutable.update { it.copy(maestPrediction = prediction, maestAnalyzing = false) }
                }
                .onFailure { error -> mutable.update { it.copy(maestAnalyzing = false, maestError = error.message ?: "Analisi MAEST fallita") } }
        }
    }
    fun saveWebDav(config: WebDavConfig) { repo.saveWebDav(config); player.release(); player = createPlayer(getApplication(), config); attachPlayerListener(); mutable.update { it.copy(webDavConfig = config, webDavMessage = "Connessione salvata") } }
    fun testWebDav(config: WebDavConfig) = viewModelScope.launch { mutable.update { it.copy(webDavBusy = true, webDavMessage = null) }; val result = repo.testWebDav(config); mutable.update { it.copy(webDavBusy = false, webDavMessage = if (result.isSuccess) "Connessione riuscita" else result.exceptionOrNull()?.message ?: "Connessione fallita") } }
    fun browseWebDav(path: String) = viewModelScope.launch { mutable.update { it.copy(webDavBusy = true, webDavMessage = null) }; runCatching { repo.browseWebDav(path) }.onSuccess { items -> mutable.update { it.copy(webDavItems = items, webDavPath = path, webDavBusy = false) } }.onFailure { error -> mutable.update { it.copy(webDavBusy = false, webDavMessage = "Errore WebDAV: ${error.message}") } } }
    fun scanWebDav(path: String, recursive: Boolean) = viewModelScope.launch { mutable.update { it.copy(webDavBusy = true, webDavMessage = "Analisi WebDAV…") }; runCatching { repo.scanWebDav(path, recursive) { n, p -> mutable.update { it.copy(webDavMessage = "$n cartelle analizzate • $p") } } }.onSuccess { mutable.update { it.copy(webDavBusy = false, webDavMessage = "Scansione completata") } }.onFailure { e -> mutable.update { it.copy(webDavBusy = false, webDavMessage = "Errore: ${e.message}") } } }
    private fun attachPlayerListener() { player.addListener(object : Player.Listener { override fun onIsPlayingChanged(v: Boolean) { mutable.update { it.copy(isPlaying = v) } }; override fun onPlaybackStateChanged(state: Int) { if (state == Player.STATE_READY) mutable.update { it.copy(playbackDuration = player.duration.coerceAtLeast(0)) } } }) }
    fun playWebDav(item: WebDavItem) = play(WebDavClient(mutable.value.webDavConfig).toTrack(item))
    private fun createPlayer(app: Application, config: WebDavConfig): ExoPlayer {
        val factory = OkHttpDataSource.Factory(OkHttpClient()).setDefaultRequestProperties(if (config.ready) mapOf("Authorization" to WebDavClient(config).authorization()) else emptyMap())
        return ExoPlayer.Builder(app).setMediaSourceFactory(DefaultMediaSourceFactory(factory)).build()
    }
    private fun fail(t: Throwable) = mutable.update { it.copy(error = t.message ?: "Errore imprevisto", scanning = false, loading = false) }
    override fun onCleared() { player.release(); super.onCleared() }
}
