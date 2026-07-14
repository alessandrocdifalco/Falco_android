@file:androidx.media3.common.util.UnstableApi

package com.alessandro.falco.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.alessandro.falco.data.*
import com.alessandro.falco.webdav.*
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
    val playbackDuration: Long = 0, val lastReviewed: TrackEntity? = null
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
    private var player = createPlayer(app, repo.webDavConfig())
    private val mutable = MutableStateFlow(UiState(webDavConfig = repo.webDavConfig()))
    val state: StateFlow<UiState> = mutable.asStateFlow()
    init {
        viewModelScope.launch { repo.tracks.collect { mutable.update { s -> s.copy(tracks = it, loading = false, selected = s.selected?.let { old -> it.find { n -> n.id == old.id } }) } } }
        attachPlayerListener()
        viewModelScope.launch { while (true) { mutable.update { it.copy(position = player.currentPosition.coerceAtLeast(0)) }; delay(500) } }
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
        mutable.update { it.copy(lastReviewed = v) }; repo.update(v.copy(workStatus = status, genre = genre, rating = rating, customTags = tags.joinToString(",")))
    }
    fun undoReview() = viewModelScope.launch { mutable.value.lastReviewed?.let { repo.update(it); mutable.update { s -> s.copy(lastReviewed = null) } } }
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
