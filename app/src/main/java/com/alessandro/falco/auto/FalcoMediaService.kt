@file:androidx.media3.common.util.UnstableApi

package com.alessandro.falco.auto

import android.net.Uri
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import com.alessandro.falco.ai.AiCache
import com.alessandro.falco.ai.LocalAiEngine
import com.alessandro.falco.data.FalcoDatabase
import com.alessandro.falco.data.FalcoTaxonomy
import com.alessandro.falco.data.TaxonomyItem
import com.alessandro.falco.data.TrackEntity
import com.alessandro.falco.webdav.WebDavClient
import com.alessandro.falco.webdav.WebDavStore
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

class FalcoMediaService : MediaLibraryService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var tracks: List<TrackEntity> = emptyList()
    private val dao by lazy { FalcoDatabase.get(this).tracks() }
    private val localAi by lazy { LocalAiEngine(this) }
    private lateinit var player: ExoPlayer
    private lateinit var session: MediaLibrarySession

    override fun onCreate() {
        super.onCreate()
        // The car UI must be able to open even if encrypted preferences are temporarily
        // unavailable (for example immediately after the phone has restarted).
        val config = runCatching { WebDavStore(this).load() }.getOrNull()
        val dataSource = OkHttpDataSource.Factory(OkHttpClient()).setDefaultRequestProperties(
            if (config?.ready == true) mapOf("Authorization" to WebDavClient(config).authorization()) else emptyMap()
        )
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSource))
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
        session = MediaLibrarySession.Builder(this, player, LibraryCallback()).build()
        scope.launch { dao.observeAll().collectLatest { fresh ->
            tracks = fresh
            if (::session.isInitialized) {
                listOf(ROOT, REVIEW, AI_REVIEW, AI_CONFIDENT, AI_UNCERTAIN, AI_PENDING, ALL, FAVORITES, GENRES, ARTISTS, READY).forEach { parent ->
                    session.notifyChildrenChanged(parent, childCount(parent), null)
                }
            }
        } }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession = session

    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        if (!player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        scope.cancel(); session.release(); player.release(); super.onDestroy()
    }

    private inner class LibraryCallback : MediaLibrarySession.Callback {
        override fun onGetLibraryRoot(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, params: LibraryParams?): ListenableFuture<LibraryResult<MediaItem>> =
            Futures.immediateFuture(LibraryResult.ofItem(folder(ROOT, "Libreria FALCO"), params))

        override fun onGetChildren(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, parentId: String, page: Int, pageSize: Int, params: LibraryParams?): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val items = runCatching { when {
                parentId == ROOT -> listOf(folder(REVIEW, "Revisione"), folder(AI_REVIEW, "Revisione AI"), folder(ALL, "Tutti i brani"), folder(FAVORITES, "Preferiti"), folder(GENRES, "Generi"), folder(ARTISTS, "Artisti"), folder(READY, "Pronti per il set"))
                parentId == REVIEW -> reviewTracks().map(::reviewFolder)
                parentId == AI_REVIEW -> listOf(
                    folder(AI_CONFIDENT, "AI sicura · 70% o più"),
                    folder(AI_UNCERTAIN, "AI incerta · sotto 70%"),
                    folder(AI_PENDING, "Non ancora analizzati")
                )
                parentId == AI_CONFIDENT -> reviewTracks().filter { maestConfidence(it) >= 70 }.map(::reviewFolder)
                parentId == AI_UNCERTAIN -> reviewTracks().filter { maestConfidence(it) in 1..69 }.map(::reviewFolder)
                parentId == AI_PENDING -> reviewTracks().filter { maestConfidence(it) <= 0 }.map(::reviewFolder)
                parentId == ALL -> tracks.sortedBy { it.title.lowercase() }.map(::song)
                parentId == FAVORITES -> tracks.filter { it.favorite }.sortedBy { it.title.lowercase() }.map(::song)
                parentId == READY -> tracks.filter { it.workStatus == "PRONTO" || it.workStatus == "KEEP" }.sortedBy { it.title.lowercase() }.map(::song)
                parentId == GENRES -> tracks.map { it.genre.ifBlank { "Sconosciuto" } }.distinct().sorted().map { folder("genre:${Uri.encode(it)}", it) }
                parentId == ARTISTS -> tracks.map { it.artist.ifBlank { "Sconosciuto" } }.distinct().sorted().map { folder("artist:${Uri.encode(it)}", it) }
                parentId.startsWith("genre:") -> tracks.filter { it.genre.ifBlank { "Sconosciuto" } == Uri.decode(parentId.removePrefix("genre:")) }.sortedBy { it.title.lowercase() }.map(::song)
                parentId.startsWith("artist:") -> tracks.filter { it.artist.ifBlank { "Sconosciuto" } == Uri.decode(parentId.removePrefix("artist:")) }.sortedBy { it.title.lowercase() }.map(::song)
                parentId.startsWith(REVIEW_TRACK_PREFIX) -> {
                    val id = parentId.removePrefix(REVIEW_TRACK_PREFIX).toLongOrNull()
                    tracks.firstOrNull { it.id == id }?.let { track ->
                        listOf(
                            reviewListen(track),
                            reviewAction(track, "KEEP", "✓  TIENI"),
                            reviewAction(track, "MAYBE", "◷  DOPO"),
                            reviewAction(track, "REJECT", "✕  SCARTA"),
                            aiFolder(track),
                            taxonomyFolder(track)
                        )
                    }.orEmpty()
                }
                parentId.startsWith(TAXONOMY_ROOT_PREFIX) -> {
                    val id = parentId.removePrefix(TAXONOMY_ROOT_PREFIX).toLongOrNull()
                    tracks.firstOrNull { it.id == id }?.let(::taxonomyCategories).orEmpty()
                }
                parentId.startsWith(TAXONOMY_CATEGORY_PREFIX) -> taxonomyValues(parentId)
                parentId.startsWith(AI_TRACK_PREFIX) -> {
                    val id = parentId.removePrefix(AI_TRACK_PREFIX).toLongOrNull()
                    tracks.firstOrNull { it.id == id }?.let(::aiSuggestions).orEmpty()
                }
                else -> emptyList()
            } }.getOrElse {
                Log.e(TAG, "Unable to load car library parent=$parentId", it)
                emptyList()
            }
            return Futures.immediateFuture(LibraryResult.ofItemList(page(items, page, pageSize), params))
        }

        override fun onGetItem(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, mediaId: String): ListenableFuture<LibraryResult<MediaItem>> {
            val item = resolveItem(mediaId)
            return Futures.immediateFuture(if (item != null) LibraryResult.ofItem(item, null) else LibraryResult.ofError(SessionError.ERROR_BAD_VALUE))
        }

        override fun onSearch(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, query: String, params: LibraryParams?): ListenableFuture<LibraryResult<Void>> {
            val count = search(query).size
            session.notifySearchResultChanged(browser, query, count, params)
            return Futures.immediateFuture(LibraryResult.ofVoid(params))
        }

        override fun onGetSearchResult(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, query: String, page: Int, pageSize: Int, params: LibraryParams?): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val found = search(query).map(::song)
            return Futures.immediateFuture(LibraryResult.ofItemList(page(found, page, pageSize), params))
        }

        override fun onAddMediaItems(mediaSession: MediaSession, controller: MediaSession.ControllerInfo, mediaItems: List<MediaItem>): ListenableFuture<List<MediaItem>> {
            val resolved = mediaItems.mapNotNull { requested ->
                val action = parseReviewAction(requested.mediaId)
                if (action != null) {
                    val (status, id) = action
                    applyReviewAction(id, status)?.let(::reviewSong)
                } else {
                    val taxonomy = parseTaxonomyAction(requested.mediaId)
                    if (taxonomy != null) {
                        val (type, value, id) = taxonomy
                        applyTaxonomy(id, type, value)?.let(::reviewSong)
                    } else tracks.firstOrNull { requested.mediaId == "track:${it.id}" }?.let(::song)
                }
            }
            return Futures.immediateFuture(resolved)
        }
    }

    private fun search(query: String): List<TrackEntity> {
        val q = query.trim()
        if (q.isBlank()) return emptyList()
        return tracks.filter { listOf(it.title, it.artist, it.album, it.genre, it.customTags).any { value -> value.contains(q, true) } }.sortedBy { it.title.lowercase() }
    }

    private fun reviewTracks() = tracks
        .filter { it.workStatus == "DA_VALUTARE" || it.workStatus == "DA_TAGGARE" }
        .sortedByDescending { it.modifiedAt }

    /** Some OEM head units request pageSize=0 or use values large enough to overflow Int. */
    private fun <T> page(items: List<T>, requestedPage: Int, requestedSize: Int): List<T> {
        if (items.isEmpty()) return emptyList()
        val size = if (requestedSize <= 0) items.size else requestedSize
        val fromLong = requestedPage.coerceAtLeast(0).toLong() * size.toLong()
        if (fromLong >= items.size) return emptyList()
        val from = fromLong.toInt()
        val to = (fromLong + size.toLong()).coerceAtMost(items.size.toLong()).toInt()
        return items.subList(from, to)
    }

    private fun childCount(parent: String): Int = when (parent) {
        ROOT -> 7
        REVIEW -> reviewTracks().size
        AI_REVIEW -> 3
        AI_CONFIDENT -> reviewTracks().count { maestConfidence(it) >= 70 }
        AI_UNCERTAIN -> reviewTracks().count { maestConfidence(it) in 1..69 }
        AI_PENDING -> reviewTracks().count { maestConfidence(it) <= 0 }
        FAVORITES -> tracks.count { it.favorite }
        READY -> tracks.count { it.workStatus == "PRONTO" || it.workStatus == "KEEP" }
        GENRES -> tracks.map { it.genre }.distinct().size
        ARTISTS -> tracks.map { it.artist }.distinct().size
        else -> tracks.size
    }

    private fun folder(id: String, title: String) = MediaItem.Builder().setMediaId(id).setMediaMetadata(
        MediaMetadata.Builder().setTitle(title).setIsBrowsable(true).setIsPlayable(false).setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED).build()
    ).build()

    private fun reviewFolder(track: TrackEntity) = MediaItem.Builder()
        .setMediaId("$REVIEW_TRACK_PREFIX${track.id}")
        .setMediaMetadata(MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(listOf(track.artist, aiSummary(track)).filter { it.isNotBlank() }.joinToString(" · "))
            .setAlbumTitle("Apri: ascolta, scegli o modifica i tag")
            .setIsBrowsable(true).setIsPlayable(false)
            .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED).build())
        .build()

    private fun reviewListen(track: TrackEntity) = MediaItem.Builder()
        .setMediaId("track:${track.id}")
        .setUri(track.uri)
        .setMediaMetadata(MediaMetadata.Builder()
            .setTitle("▶  ASCOLTA")
            .setArtist(listOf(track.title, track.artist, aiSummary(track)).filter { it.isNotBlank() }.joinToString(" · "))
            .setIsBrowsable(false).setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC).build())
        .build()

    private fun reviewAction(track: TrackEntity, status: String, label: String) = MediaItem.Builder()
        .setMediaId("$REVIEW_ACTION_PREFIX$status:${track.id}")
        .setMediaMetadata(MediaMetadata.Builder()
            .setTitle(label).setArtist(track.title)
            .setIsBrowsable(false).setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC).build())
        .build()

    private fun taxonomyFolder(track: TrackEntity) = folder("$TAXONOMY_ROOT_PREFIX${track.id}", "🏷  TASSONOMIA")

    private fun aiFolder(track: TrackEntity) = folder("$AI_TRACK_PREFIX${track.id}", "✨  ${aiSummary(track).ifBlank { "AI non ancora pronta" }}")

    private fun aiSuggestions(track: TrackEntity): List<MediaItem> {
        val maest = AiCache.decodePrediction(track.maestCache)
        val learned = runCatching { localAi.suggest(track, AiCache.decodeWaveform(track.waveformCache)) }.getOrNull()
        val out = mutableListOf<MediaItem>()
        maest?.genre?.let { out += taxonomyAction(track, "genre", TaxonomyItem(it, "MAEST genere: ${taxonomyLabel(it)} · ${maest.confidence}%")) }
        maest?.subgenre?.takeIf { it != maest.genre }?.let { out += taxonomyAction(track, "subgenre", TaxonomyItem(it, "MAEST sottogenere: ${taxonomyLabel(it)}")) }
        learned?.genre?.let { out += taxonomyAction(track, "genre", TaxonomyItem(it, "Imparata: ${taxonomyLabel(it)} · ${learned.confidence}%")) }
        learned?.energy?.takeIf { it in 1..5 }?.let { out += taxonomyAction(track, "energy", TaxonomyItem("E$it", "Energia imparata: E$it")) }
        learned?.rejectProbability?.let { probability -> out += folder("ai-info:reject:${track.id}", "Probabile scarto · $probability%") }
        return out.ifEmpty { listOf(folder("ai-info:pending:${track.id}", "Analisi AI non ancora disponibile")) }
    }

    private fun maestConfidence(track: TrackEntity) = AiCache.decodePrediction(track.maestCache)?.confidence ?: 0

    private fun aiSummary(track: TrackEntity): String {
        val prediction = AiCache.decodePrediction(track.maestCache) ?: return ""
        val genre = prediction.genre?.let(::taxonomyLabel) ?: "Analizzato"
        val subgenre = prediction.subgenre?.takeIf { it != prediction.genre }?.let { " / ${taxonomyLabel(it)}" }.orEmpty()
        return "AI: $genre$subgenre ${prediction.confidence}%"
    }

    private fun taxonomyCategories(track: TrackEntity): List<MediaItem> {
        val tags = tagSet(track)
        val sub = tags.firstOrNull { it.startsWith("SUB:") }?.removePrefix("SUB:")
        val energy = tags.firstOrNull { it.matches(Regex("E[1-5]")) }
        return listOf(
            taxonomyCategory(track, "genre", "Genere · ${taxonomyLabel(track.genre)}"),
            taxonomyCategory(track, "subgenre", "Sottogenere · ${taxonomyLabel(sub.orEmpty())}"),
            taxonomyCategory(track, "energy", "Energia · ${energy ?: "—"}"),
            taxonomyCategory(track, "rating", "Rating · ${if (track.rating > 0) "★".repeat(track.rating) else "—"}"),
            taxonomyCategory(track, "usage", "Uso / situazione"),
            taxonomyCategory(track, "voice", "Voce"),
            taxonomyCategory(track, "mood", "Mood"),
            taxonomyCategory(track, "elements", "Elementi musicali")
        )
    }

    private fun taxonomyCategory(track: TrackEntity, type: String, label: String) =
        folder("$TAXONOMY_CATEGORY_PREFIX$type:${track.id}", label)

    private fun taxonomyValues(parentId: String): List<MediaItem> {
        val value = parentId.removePrefix(TAXONOMY_CATEGORY_PREFIX)
        val type = value.substringBefore(':')
        val id = value.substringAfter(':', "").toLongOrNull() ?: return emptyList()
        val track = tracks.firstOrNull { it.id == id } ?: return emptyList()
        val items = when (type) {
            "genre" -> FalcoTaxonomy.genres
            "subgenre" -> FalcoTaxonomy.subgenres[track.genre].orEmpty()
            "energy" -> FalcoTaxonomy.energy
            "rating" -> (1..5).map { TaxonomyItem(it.toString(), "★".repeat(it)) }
            "usage" -> FalcoTaxonomy.usage
            "voice" -> FalcoTaxonomy.voice
            "mood" -> FalcoTaxonomy.tags.filter { it.id in MOOD_TAGS }
            "elements" -> FalcoTaxonomy.tags.filter { it.id in ELEMENT_TAGS }
            else -> emptyList()
        }
        return items.map { item -> taxonomyAction(track, type, item) }
    }

    private fun taxonomyAction(track: TrackEntity, type: String, item: TaxonomyItem): MediaItem {
        val selected = taxonomySelected(track, type, item.id)
        return MediaItem.Builder()
            .setMediaId("$TAXONOMY_ACTION_PREFIX$type:${Uri.encode(item.id)}:${track.id}")
            .setMediaMetadata(MediaMetadata.Builder()
                .setTitle("${if (selected) "✓  " else ""}${item.label}")
                .setArtist(track.title).setIsBrowsable(false).setIsPlayable(true)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC).build())
            .build()
    }

    private fun resolveItem(mediaId: String): MediaItem? {
        tracks.firstOrNull { mediaId == "track:${it.id}" }?.let { return song(it) }
        if (mediaId.startsWith(REVIEW_TRACK_PREFIX)) {
            val id = mediaId.removePrefix(REVIEW_TRACK_PREFIX).toLongOrNull()
            return tracks.firstOrNull { it.id == id }?.let(::reviewFolder)
        }
        if (mediaId.startsWith(TAXONOMY_ROOT_PREFIX)) {
            val id = mediaId.removePrefix(TAXONOMY_ROOT_PREFIX).toLongOrNull()
            return tracks.firstOrNull { it.id == id }?.let(::taxonomyFolder)
        }
        if (mediaId.startsWith(AI_TRACK_PREFIX)) {
            val id = mediaId.removePrefix(AI_TRACK_PREFIX).toLongOrNull()
            return tracks.firstOrNull { it.id == id }?.let(::aiFolder)
        }
        if (mediaId.startsWith(TAXONOMY_CATEGORY_PREFIX)) {
            val value = mediaId.removePrefix(TAXONOMY_CATEGORY_PREFIX)
            val id = value.substringAfter(':', "").toLongOrNull()
            val track = tracks.firstOrNull { it.id == id } ?: return null
            return taxonomyCategory(track, value.substringBefore(':'), value.substringBefore(':').replaceFirstChar(Char::uppercase))
        }
        parseTaxonomyAction(mediaId)?.let { (type, value, id) ->
            val track = tracks.firstOrNull { it.id == id } ?: return null
            return taxonomyAction(track, type, TaxonomyItem(value, taxonomyLabel(value)))
        }
        val action = parseReviewAction(mediaId) ?: return null
        val track = tracks.firstOrNull { it.id == action.second } ?: return null
        return reviewAction(track, action.first, when (action.first) {
            "KEEP" -> "✓  TIENI"; "MAYBE" -> "◷  DOPO"; else -> "✕  SCARTA"
        })
    }

    private fun parseReviewAction(mediaId: String): Pair<String, Long>? {
        if (!mediaId.startsWith(REVIEW_ACTION_PREFIX)) return null
        val value = mediaId.removePrefix(REVIEW_ACTION_PREFIX)
        val status = value.substringBefore(':').takeIf { it in setOf("KEEP", "MAYBE", "REJECT") } ?: return null
        val id = value.substringAfter(':', "").toLongOrNull() ?: return null
        return status to id
    }

    private fun applyReviewAction(id: Long, status: String): TrackEntity? {
        val current = tracks.firstOrNull { it.id == id } ?: return null
        val updated = current.copy(workStatus = status)
        tracks = tracks.map { if (it.id == id) updated else it }
        scope.launch {
            dao.update(updated)
            val tags = tagSet(updated)
            localAi.learn(
                updated,
                AiCache.decodeWaveform(updated.waveformCache),
                updated.genre,
                tags.firstOrNull { it.startsWith("SUB:") }?.removePrefix("SUB:").orEmpty(),
                tags.firstOrNull { it.matches(Regex("E[1-5]")) }?.drop(1)?.toIntOrNull() ?: 0,
                updated.rating,
                status,
                localAi.suggest(updated, AiCache.decodeWaveform(updated.waveformCache))
            )
        }
        session.notifyChildrenChanged(REVIEW, childCount(REVIEW), null)
        session.notifyChildrenChanged(AI_CONFIDENT, childCount(AI_CONFIDENT), null)
        session.notifyChildrenChanged(AI_UNCERTAIN, childCount(AI_UNCERTAIN), null)
        session.notifyChildrenChanged(AI_PENDING, childCount(AI_PENDING), null)
        return reviewTracks().firstOrNull()
    }

    private fun parseTaxonomyAction(mediaId: String): Triple<String, String, Long>? {
        if (!mediaId.startsWith(TAXONOMY_ACTION_PREFIX)) return null
        val raw = mediaId.removePrefix(TAXONOMY_ACTION_PREFIX)
        val parts = raw.split(':', limit = 3)
        if (parts.size != 3) return null
        return Triple(parts[0], Uri.decode(parts[1]), parts[2].toLongOrNull() ?: return null)
    }

    private fun applyTaxonomy(id: Long, type: String, value: String): TrackEntity? {
        val current = tracks.firstOrNull { it.id == id } ?: return null
        val tags = tagSet(current).toMutableSet()
        val updated = when (type) {
            "genre" -> current.copy(genre = value, customTags = tags.filterNot { it.startsWith("SUB:") }.joinToString(","))
            "subgenre" -> current.copy(customTags = (tags.filterNot { it.startsWith("SUB:") } + "SUB:$value").joinToString(","))
            "energy" -> current.copy(customTags = (tags.filterNot { it.matches(Regex("E[1-5]")) } + value).joinToString(","))
            "rating" -> current.copy(rating = value.toIntOrNull()?.coerceIn(1, 5) ?: current.rating)
            "voice" -> current.copy(customTags = (tags.filterNot { it in VOICE_TAGS } + value).joinToString(","))
            "usage", "mood", "elements" -> current.copy(customTags = toggle(tags, value).joinToString(","))
            else -> current
        }
        tracks = tracks.map { if (it.id == id) updated else it }
        scope.launch { dao.update(updated) }
        session.notifyChildrenChanged("$TAXONOMY_ROOT_PREFIX$id", taxonomyCategories(updated).size, null)
        session.notifyChildrenChanged("$TAXONOMY_CATEGORY_PREFIX$type:$id", taxonomyValues("$TAXONOMY_CATEGORY_PREFIX$type:$id").size, null)
        return updated
    }

    private fun tagSet(track: TrackEntity) = track.customTags.split(',').filter { it.isNotBlank() }.toSet()
    private fun toggle(tags: MutableSet<String>, value: String): Set<String> = tags.apply { if (!add(value)) remove(value) }
    private fun taxonomySelected(track: TrackEntity, type: String, value: String): Boolean = when (type) {
        "genre" -> track.genre == value
        "subgenre" -> "SUB:$value" in tagSet(track)
        "energy" -> value in tagSet(track)
        "rating" -> track.rating == value.toIntOrNull()
        else -> value in tagSet(track)
    }
    private fun taxonomyLabel(value: String) = value.ifBlank { "—" }.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase)

    private fun reviewSong(track: TrackEntity): MediaItem {
        val suggestion = track.maestCache.takeIf { it.isNotBlank() }?.let { " • AI pronta" }.orEmpty()
        val metadata = MediaMetadata.Builder().setTitle(track.title).setArtist("${track.artist}$suggestion").setAlbumTitle("Revisione FALCO")
            .setGenre(track.genre).setIsBrowsable(false).setIsPlayable(true).setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .apply { if (track.durationMs > 0) setDurationMs(track.durationMs) }.build()
        return MediaItem.Builder().setMediaId("track:${track.id}").setUri(track.uri).setMediaMetadata(metadata).build()
    }

    private fun song(track: TrackEntity): MediaItem {
        val metadata = MediaMetadata.Builder().setTitle(track.title).setArtist(track.artist).setAlbumTitle(track.album).setGenre(track.genre)
            .setIsBrowsable(false).setIsPlayable(true).setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .apply { if (track.durationMs > 0) setDurationMs(track.durationMs) }.build()
        return MediaItem.Builder().setMediaId("track:${track.id}").setUri(track.uri).setMediaMetadata(metadata).build()
    }

    companion object {
        private const val TAG = "FalcoAuto"
        private const val ROOT = "root"; private const val ALL = "all"; private const val FAVORITES = "favorites"
        private const val GENRES = "genres"; private const val ARTISTS = "artists"; private const val READY = "ready"; private const val REVIEW = "review"
        private const val AI_REVIEW = "ai-review"; private const val AI_CONFIDENT = "ai-confident"
        private const val AI_UNCERTAIN = "ai-uncertain"; private const val AI_PENDING = "ai-pending"
        private const val AI_TRACK_PREFIX = "ai-track:"
        private const val REVIEW_TRACK_PREFIX = "review-track:"
        private const val REVIEW_ACTION_PREFIX = "review-action:"
        private const val TAXONOMY_ROOT_PREFIX = "taxonomy-root:"
        private const val TAXONOMY_CATEGORY_PREFIX = "taxonomy-category:"
        private const val TAXONOMY_ACTION_PREFIX = "taxonomy-action:"
        private val MOOD_TAGS = setOf("GROOVY", "ELEGANTE", "SOLARE", "SCURO", "SEXY", "NOTTURNO", "CLASSIC_FEEL")
        private val ELEMENT_TAGS = setOf("PIANO", "SAX", "FIATI", "CHITARRA", "BASSO_FORTE", "DUB", "PERCUSSIONI")
        private val VOICE_TAGS = FalcoTaxonomy.voice.map { it.id }.toSet()
    }
}
