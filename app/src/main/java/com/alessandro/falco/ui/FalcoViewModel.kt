package com.alessandro.falco.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.alessandro.falco.data.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class SortMode(val label: String) { TITLE("Titolo"), ARTIST("Artista"), DATE("Data"), DURATION("Durata"), SIZE("Dimensione") }
data class Filters(val genre: String? = null, val artist: String? = null, val format: String? = null, val year: Int? = null, val rating: Int? = null, val status: String? = null, val favorites: Boolean = false)
data class UiState(
    val tracks: List<TrackEntity> = emptyList(), val query: String = "", val filters: Filters = Filters(), val sort: SortMode = SortMode.TITLE,
    val loading: Boolean = true, val scanning: Boolean = false, val scanProgress: ScanProgress? = null, val error: String? = null,
    val selected: TrackEntity? = null, val playing: TrackEntity? = null, val isPlaying: Boolean = false, val position: Long = 0
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
    private val player = ExoPlayer.Builder(app).build()
    private val mutable = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = mutable.asStateFlow()
    init {
        viewModelScope.launch { repo.tracks.collect { mutable.update { s -> s.copy(tracks = it, loading = false, selected = s.selected?.let { old -> it.find { n -> n.id == old.id } }) } } }
        player.addListener(object : Player.Listener { override fun onIsPlayingChanged(v: Boolean) { mutable.update { it.copy(isPlaying = v) } } })
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
    fun play(v: TrackEntity) {
        if (mutable.value.playing?.id == v.id) { if (player.isPlaying) player.pause() else player.play(); return }
        player.setMediaItem(MediaItem.fromUri(v.uri)); player.prepare(); player.play(); mutable.update { it.copy(playing = v, position = 0) }
    }
    fun seek(v: Long) = player.seekTo(v)
    private fun fail(t: Throwable) = mutable.update { it.copy(error = t.message ?: "Errore imprevisto", scanning = false, loading = false) }
    override fun onCleared() { player.release(); super.onCleared() }
}
