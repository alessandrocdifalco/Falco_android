@file:androidx.media3.common.util.UnstableApi

package com.alessandro.falco.auto

import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.CommandButton
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaConstants
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.alessandro.falco.R
import com.alessandro.falco.data.FalcoDatabase
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
        session = MediaLibrarySession.Builder(this, player, LibraryCallback())
            .setMediaButtonPreferences(reviewButtons())
            .build()
        scope.launch { dao.observeAll().collectLatest { fresh ->
            tracks = fresh
            if (::session.isInitialized) {
                listOf(ROOT, REVIEW, ALL, FAVORITES, GENRES, ARTISTS, READY).forEach { parent ->
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
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(KEEP_COMMAND).add(LATER_COMMAND).add(REJECT_COMMAND).build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            val status = when (customCommand.customAction) {
                ACTION_KEEP -> "KEEP"
                ACTION_LATER -> "MAYBE"
                ACTION_REJECT -> "REJECT"
                else -> return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
            }
            val mediaId = args.getString(MediaConstants.EXTRA_KEY_MEDIA_ID)
                ?: player.currentMediaItem?.mediaId
                ?: return Futures.immediateFuture(SessionResult(SessionError.ERROR_BAD_VALUE))
            val id = mediaId.removePrefix("track:").toLongOrNull()
                ?: return Futures.immediateFuture(SessionResult(SessionError.ERROR_BAD_VALUE))
            val current = tracks.firstOrNull { it.id == id }
                ?: return Futures.immediateFuture(SessionResult(SessionError.ERROR_BAD_VALUE))
            val updated = current.copy(workStatus = status)
            tracks = tracks.map { if (it.id == id) updated else it }
            scope.launch { dao.update(updated) }

            val next = tracks.firstOrNull { it.workStatus == "DA_VALUTARE" || it.workStatus == "DA_TAGGARE" }
            if (next != null) {
                player.setMediaItem(reviewSong(next))
                player.prepare()
                player.play()
            } else {
                player.pause()
            }
            this@FalcoMediaService.session.notifyChildrenChanged(REVIEW, childCount(REVIEW), null)
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        override fun onGetLibraryRoot(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, params: LibraryParams?): ListenableFuture<LibraryResult<MediaItem>> =
            Futures.immediateFuture(LibraryResult.ofItem(folder(ROOT, "Libreria FALCO"), params))

        override fun onGetChildren(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, parentId: String, page: Int, pageSize: Int, params: LibraryParams?): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val items = runCatching { when {
                parentId == ROOT -> listOf(folder(REVIEW, "Revisione"), folder(ALL, "Tutti i brani"), folder(FAVORITES, "Preferiti"), folder(GENRES, "Generi"), folder(ARTISTS, "Artisti"), folder(READY, "Pronti per il set"))
                parentId == REVIEW -> tracks.filter { it.workStatus == "DA_VALUTARE" || it.workStatus == "DA_TAGGARE" }.sortedByDescending { it.modifiedAt }.map(::reviewSong)
                parentId == ALL -> tracks.sortedBy { it.title.lowercase() }.map(::song)
                parentId == FAVORITES -> tracks.filter { it.favorite }.sortedBy { it.title.lowercase() }.map(::song)
                parentId == READY -> tracks.filter { it.workStatus == "PRONTO" || it.workStatus == "KEEP" }.sortedBy { it.title.lowercase() }.map(::song)
                parentId == GENRES -> tracks.map { it.genre.ifBlank { "Sconosciuto" } }.distinct().sorted().map { folder("genre:${Uri.encode(it)}", it) }
                parentId == ARTISTS -> tracks.map { it.artist.ifBlank { "Sconosciuto" } }.distinct().sorted().map { folder("artist:${Uri.encode(it)}", it) }
                parentId.startsWith("genre:") -> tracks.filter { it.genre.ifBlank { "Sconosciuto" } == Uri.decode(parentId.removePrefix("genre:")) }.sortedBy { it.title.lowercase() }.map(::song)
                parentId.startsWith("artist:") -> tracks.filter { it.artist.ifBlank { "Sconosciuto" } == Uri.decode(parentId.removePrefix("artist:")) }.sortedBy { it.title.lowercase() }.map(::song)
                else -> emptyList()
            } }.getOrElse {
                Log.e(TAG, "Unable to load car library parent=$parentId", it)
                emptyList()
            }
            return Futures.immediateFuture(LibraryResult.ofItemList(page(items, page, pageSize), params))
        }

        override fun onGetItem(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, mediaId: String): ListenableFuture<LibraryResult<MediaItem>> {
            val item = tracks.firstOrNull { mediaId == "track:${it.id}" }?.let(::song)
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
            val resolved = mediaItems.mapNotNull { requested -> tracks.firstOrNull { requested.mediaId == "track:${it.id}" }?.let(::song) }
            return Futures.immediateFuture(resolved)
        }
    }

    private fun search(query: String): List<TrackEntity> {
        val q = query.trim()
        if (q.isBlank()) return emptyList()
        return tracks.filter { listOf(it.title, it.artist, it.album, it.genre, it.customTags).any { value -> value.contains(q, true) } }.sortedBy { it.title.lowercase() }
    }

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
        ROOT -> 6
        REVIEW -> tracks.count { it.workStatus == "DA_VALUTARE" || it.workStatus == "DA_TAGGARE" }
        FAVORITES -> tracks.count { it.favorite }
        READY -> tracks.count { it.workStatus == "PRONTO" || it.workStatus == "KEEP" }
        GENRES -> tracks.map { it.genre }.distinct().size
        ARTISTS -> tracks.map { it.artist }.distinct().size
        else -> tracks.size
    }

    private fun folder(id: String, title: String) = MediaItem.Builder().setMediaId(id).setMediaMetadata(
        MediaMetadata.Builder().setTitle(title).setIsBrowsable(true).setIsPlayable(false).setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED).build()
    ).build()

    private fun reviewButtons() = listOf(
        CommandButton.Builder().setDisplayName("Tieni").setIconResId(R.drawable.ic_auto_keep).setSessionCommand(KEEP_COMMAND).build(),
        CommandButton.Builder().setDisplayName("Dopo").setIconResId(R.drawable.ic_auto_later).setSessionCommand(LATER_COMMAND).build(),
        CommandButton.Builder().setDisplayName("Scarta").setIconResId(R.drawable.ic_auto_reject).setSessionCommand(REJECT_COMMAND).build()
    )

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
        private const val ACTION_KEEP = "com.alessandro.falco.KEEP"
        private const val ACTION_LATER = "com.alessandro.falco.LATER"
        private const val ACTION_REJECT = "com.alessandro.falco.REJECT"
        private val KEEP_COMMAND = SessionCommand(ACTION_KEEP, Bundle.EMPTY)
        private val LATER_COMMAND = SessionCommand(ACTION_LATER, Bundle.EMPTY)
        private val REJECT_COMMAND = SessionCommand(ACTION_REJECT, Bundle.EMPTY)
    }
}
