package com.alessandro.falco

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val Black = Color(0xFF0B0D10)
private val Panel = Color(0xFF15191E)
private val Accent = Color(0xFFB8FF3D)
private val Muted = Color(0xFF98A1AA)
private val Danger = Color(0xFFFF6B6B)

data class AudioTrack(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val folder: String,
    val durationMs: Long,
    val uri: Uri
)

private enum class Section(val label: String) { LIBRARY("Libreria"), REVIEW("Revisione"), PLAYLISTS("Playlist") }
private enum class ReviewState { KEEP, LATER, DISCARD }

class MainActivity : ComponentActivity() {
    private val prefs by lazy { getSharedPreferences("falco_library", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Surface(color = Black, modifier = Modifier.fillMaxSize()) { FalcoApp() } } }
    }

    @Composable
    private fun FalcoApp() {
        val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) }
        var tracks by remember { mutableStateOf(emptyList<AudioTrack>()) }
        var section by remember { mutableStateOf(Section.REVIEW) }
        var current by remember { mutableStateOf<AudioTrack?>(null) }
        var playing by remember { mutableStateOf(false) }
        var player by remember { mutableStateOf<MediaPlayer?>(null) }
        var reviewStates by remember { mutableStateOf(loadReviewStates()) }
        var playlists by remember { mutableStateOf(loadPlaylists()) }
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }

        fun play(track: AudioTrack) {
            if (current?.id == track.id) {
                if (playing) player?.pause() else player?.start()
                playing = !playing
                return
            }
            runCatching {
                player?.release()
                player = MediaPlayer().apply {
                    setDataSource(this@MainActivity, track.uri)
                    setOnCompletionListener { playing = false }
                    prepare()
                    start()
                }
                current = track
                playing = true
            }
        }

        DisposableEffect(Unit) { onDispose { player?.release() } }
        LaunchedEffect(granted) { if (granted) tracks = loadTracks() }

        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 14.dp)) {
            Header(tracks.size, reviewStates.size)
            Spacer(Modifier.height(12.dp))
            if (!granted) {
                PermissionPanel { launcher.launch(permission) }
                return@Column
            }
            Navigation(section) { section = it }
            Spacer(Modifier.height(12.dp))
            Box(Modifier.weight(1f)) {
                when (section) {
                    Section.LIBRARY -> LibraryScreen(tracks, current, playing, ::play)
                    Section.REVIEW -> ReviewScreen(
                        tracks = tracks,
                        states = reviewStates,
                        current = current,
                        playing = playing,
                        onPlay = ::play,
                        onDecision = { track, state ->
                            reviewStates = reviewStates + (track.id to state)
                            saveReviewStates(reviewStates)
                        },
                        onAddToPlaylist = { name, track ->
                            playlists = playlists + (name to ((playlists[name] ?: emptySet()) + track.id))
                            savePlaylists(playlists)
                        }
                    )
                    Section.PLAYLISTS -> PlaylistsScreen(
                        tracks = tracks,
                        playlists = playlists,
                        current = current,
                        playing = playing,
                        onPlay = ::play,
                        onCreate = { name ->
                            if (name.isNotBlank() && name !in playlists) {
                                playlists = playlists + (name.trim() to emptySet())
                                savePlaylists(playlists)
                            }
                        },
                        onRemove = { name, id ->
                            playlists = playlists + (name to ((playlists[name] ?: emptySet()) - id))
                            savePlaylists(playlists)
                        }
                    )
                }
            }
            current?.let { track ->
                Spacer(Modifier.height(8.dp))
                NowPlaying(track, playing) { play(track) }
            }
        }
    }

    @Composable
    private fun Header(count: Int, reviewed: Int) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.background(Accent, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 6.dp)) {
                Text("F", color = Black, fontWeight = FontWeight.Black, fontSize = 22.sp)
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text("FALCO", color = Color.White, fontWeight = FontWeight.Black, fontSize = 25.sp)
                Text("$reviewed/$count REVISIONATI", color = Muted, fontSize = 11.sp, letterSpacing = 1.sp)
            }
        }
    }

    @Composable
    private fun Navigation(selected: Section, onSelect: (Section) -> Unit) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Section.entries.forEach { item ->
                val active = item == selected
                Button(
                    onClick = { onSelect(item) }, modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (active) Accent else Panel,
                        contentColor = if (active) Black else Color.White
                    )
                ) { Text(item.label, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }

    @Composable
    private fun LibraryScreen(
        tracks: List<AudioTrack>, current: AudioTrack?, playing: Boolean, onPlay: (AudioTrack) -> Unit
    ) {
        var query by remember { mutableStateOf("") }
        var folder by remember { mutableStateOf<String?>(null) }
        val folders = remember(tracks) { tracks.map { it.folder }.distinct().sorted() }
        val shown = remember(tracks, query, folder) {
            tracks.filter { track ->
                (folder == null || track.folder == folder) && (query.isBlank() || listOf(track.title, track.artist, track.album).any { it.contains(query, true) })
            }
        }
        OutlinedTextField(
            value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("Cerca titolo, artista o album") },
            colors = darkFieldColors()
        )
        Spacer(Modifier.height(7.dp))
        FolderPicker(folders, folder) { folder = it }
        Spacer(Modifier.height(7.dp))
        if (shown.isEmpty()) EmptyMessage("Nessun brano trovato") else TrackList(shown, current, playing, onPlay)
    }

    @Composable
    private fun ReviewScreen(
        tracks: List<AudioTrack>, states: Map<Long, ReviewState>, current: AudioTrack?, playing: Boolean,
        onPlay: (AudioTrack) -> Unit, onDecision: (AudioTrack, ReviewState) -> Unit,
        onAddToPlaylist: (String, AudioTrack) -> Unit
    ) {
        var folder by remember { mutableStateOf<String?>(null) }
        var index by remember { mutableIntStateOf(0) }
        var showPlaylistDialog by remember { mutableStateOf(false) }
        val folders = remember(tracks) { tracks.map { it.folder }.distinct().sorted() }
        val queue = remember(tracks, states, folder) { tracks.filter { it.id !in states && (folder == null || it.folder == folder) } }
        LaunchedEffect(queue.size, folder) { index = index.coerceIn(0, (queue.size - 1).coerceAtLeast(0)) }
        val track = queue.getOrNull(index)

        Column(Modifier.fillMaxSize()) {
            FolderPicker(folders, folder) { folder = it; index = 0 }
            Spacer(Modifier.height(10.dp))
            if (track == null) {
                EmptyMessage(if (tracks.isEmpty()) "Nessun brano sul dispositivo" else "Revisione completata in questa cartella")
                return@Column
            }
            Card(colors = CardDefaults.cardColors(containerColor = Panel), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("DA REVISIONARE  ${index + 1}/${queue.size}", color = Accent, fontSize = 11.sp, fontWeight = FontWeight.Black)
                    Text(track.title, color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(track.artist, color = Color.White, fontSize = 16.sp)
                    Text("${track.album}  •  ${formatDuration(track.durationMs)}", color = Muted, fontSize = 13.sp)
                    Text("Cartella: ${track.folder}", color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Button(
                        onClick = { onPlay(track) }, modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Black)
                    ) { Text(if (current?.id == track.id && playing) "PAUSA" else "ASCOLTA", fontWeight = FontWeight.Black) }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ReviewButton("SCARTA", Danger, Modifier.weight(1f)) { onDecision(track, ReviewState.DISCARD) }
                ReviewButton("DOPO", Color(0xFFFFC857), Modifier.weight(1f)) { onDecision(track, ReviewState.LATER) }
                ReviewButton("TIENI", Accent, Modifier.weight(1f)) { onDecision(track, ReviewState.KEEP) }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { index = (index + 1).coerceAtMost(queue.lastIndex) }, modifier = Modifier.weight(1f)) { Text("SALTA BRANO") }
                OutlinedButton(onClick = {
                    val next = queue.indices.drop(index + 1).firstOrNull { candidateIndex ->
                        !queue[candidateIndex].artist.equals(track.artist, true)
                    }
                    index = next ?: queue.lastIndex
                }, modifier = Modifier.weight(1f)) { Text("SALTA ARTISTA") }
            }
            OutlinedButton(onClick = { showPlaylistDialog = true }, modifier = Modifier.fillMaxWidth()) { Text("+ AGGIUNGI A PLAYLIST") }
        }
        if (showPlaylistDialog) track?.let { playlistTrack ->
            AddToPlaylistDialog(playlistTrack, loadPlaylists().keys.toList(), { showPlaylistDialog = false }) { name ->
                onAddToPlaylist(name, playlistTrack); showPlaylistDialog = false
            }
        }
    }

    @Composable
    private fun PlaylistsScreen(
        tracks: List<AudioTrack>, playlists: Map<String, Set<Long>>, current: AudioTrack?, playing: Boolean,
        onPlay: (AudioTrack) -> Unit, onCreate: (String) -> Unit, onRemove: (String, Long) -> Unit
    ) {
        var selected by remember { mutableStateOf<String?>(null) }
        var creating by remember { mutableStateOf(false) }
        Column(Modifier.fillMaxSize()) {
            Button(onClick = { creating = true }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Black)) {
                Text("CREA PLAYLIST", fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.height(8.dp))
            if (playlists.isEmpty()) EmptyMessage("Non hai ancora creato playlist")
            else if (selected == null) {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    items(playlists.keys.sorted()) { name ->
                        Card(Modifier.fillMaxWidth().clickable { selected = name }, colors = CardDefaults.cardColors(containerColor = Panel)) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) { Text(name, color = Color.White, fontWeight = FontWeight.Bold); Text("${playlists[name]?.size ?: 0} brani", color = Muted) }
                                Text("APRІ", color = Accent, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else {
                TextButton(onClick = { selected = null }) { Text("‹ TUTTE LE PLAYLIST", color = Accent) }
                Text(selected!!, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(7.dp))
                val playlistTracks = tracks.filter { it.id in (playlists[selected] ?: emptySet()) }
                if (playlistTracks.isEmpty()) EmptyMessage("Playlist vuota: aggiungi brani dalla revisione")
                else LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    items(playlistTracks, key = { it.id }) { track ->
                        TrackRow(track, current?.id == track.id, playing, { onPlay(track) }, { onRemove(selected!!, track.id) })
                    }
                }
            }
        }
        if (creating) NameDialog({ creating = false }) { onCreate(it); creating = false }
    }

    @Composable
    private fun FolderPicker(folders: List<String>, selected: String?, onSelect: (String?) -> Unit) {
        var expanded by remember { mutableStateOf(false) }
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(selected?.let { "CARTELLA: $it" } ?: "TUTTE LE CARTELLE", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(text = { Text("Tutte le cartelle") }, onClick = { onSelect(null); expanded = false })
                folders.forEach { name -> DropdownMenuItem(text = { Text(name) }, onClick = { onSelect(name); expanded = false }) }
            }
        }
    }

    @Composable
    private fun ReviewButton(label: String, color: Color, modifier: Modifier, action: () -> Unit) {
        Button(onClick = action, modifier = modifier, colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Black), contentPadding = PaddingValues(vertical = 13.dp)) {
            Text(label, fontWeight = FontWeight.Black)
        }
    }

    @Composable
    private fun TrackList(tracks: List<AudioTrack>, current: AudioTrack?, playing: Boolean, onPlay: (AudioTrack) -> Unit) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            items(tracks, key = { it.id }) { track -> TrackRow(track, current?.id == track.id, playing, { onPlay(track) }) }
        }
    }

    @Composable
    private fun TrackRow(track: AudioTrack, selected: Boolean, playing: Boolean, onClick: () -> Unit, onRemove: (() -> Unit)? = null) {
        Card(Modifier.fillMaxWidth().clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = if (selected) Color(0xFF222B1B) else Panel)) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (selected && playing) "Ⅱ" else "▶", color = if (selected) Accent else Color.White)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(track.title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${track.artist}  •  ${track.folder}", color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (onRemove != null) TextButton(onClick = onRemove) { Text("×", color = Danger, fontSize = 20.sp) }
                else Text(formatDuration(track.durationMs), color = Muted, fontSize = 12.sp)
            }
        }
    }

    @Composable
    private fun NowPlaying(track: AudioTrack, playing: Boolean, toggle: () -> Unit) {
        Card(colors = CardDefaults.cardColors(containerColor = Accent)) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(track.title, color = Black, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(track.artist, color = Black.copy(alpha = .72f), fontSize = 12.sp)
                }
                Button(onClick = toggle, colors = ButtonDefaults.buttonColors(containerColor = Black, contentColor = Color.White)) { Text(if (playing) "PAUSA" else "PLAY") }
            }
        }
    }

    @Composable
    private fun EmptyMessage(message: String) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(message, color = Muted) }
    }

    @Composable
    private fun PermissionPanel(onGrant: () -> Unit) {
        Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("La tua libreria, finalmente sotto controllo.", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text("FALCO legge e organizza localmente i file audio presenti sul dispositivo.", color = Muted)
                Button(onClick = onGrant, colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Black)) { Text("CONSENTI ACCESSO", fontWeight = FontWeight.Black) }
            }
        }
    }

    @Composable
    private fun NameDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
        var name by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = onDismiss, title = { Text("Nuova playlist") }, text = {
            OutlinedTextField(name, { name = it }, label = { Text("Nome") }, singleLine = true)
        }, confirmButton = { TextButton(onClick = { if (name.isNotBlank()) onSave(name) }) { Text("CREA") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("ANNULLA") } })
    }

    @Composable
    private fun AddToPlaylistDialog(track: AudioTrack, names: List<String>, onDismiss: () -> Unit, onAdd: (String) -> Unit) {
        var newName by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = onDismiss, title = { Text("Aggiungi ${track.title}") }, text = {
            Column {
                names.sorted().forEach { name -> TextButton(onClick = { onAdd(name) }, modifier = Modifier.fillMaxWidth()) { Text(name) } }
                OutlinedTextField(newName, { newName = it }, label = { Text("Oppure nuova playlist") }, singleLine = true)
            }
        }, confirmButton = { TextButton(onClick = { if (newName.isNotBlank()) onAdd(newName.trim()) }) { Text("AGGIUNGI") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("ANNULLA") } })
    }

    @Composable
    private fun darkFieldColors() = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Accent,
        focusedLabelColor = Accent, unfocusedLabelColor = Muted
    )

    private suspend fun loadTracks(): List<AudioTrack> = withContext(Dispatchers.IO) {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val folderColumn = if (Build.VERSION.SDK_INT >= 29) MediaStore.Audio.Media.RELATIVE_PATH else MediaStore.Audio.Media.DATA
        val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.DURATION, folderColumn)
        val found = mutableListOf<AudioTrack>()
        contentResolver.query(collection, projection, "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > 20000", null, "${MediaStore.Audio.Media.DATE_ADDED} DESC")?.use { cursor ->
            val id = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val title = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artist = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val album = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val duration = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val folder = cursor.getColumnIndexOrThrow(folderColumn)
            while (cursor.moveToNext()) {
                val trackId = cursor.getLong(id)
                val rawFolder = cursor.getString(folder).orEmpty()
                val cleanFolder = if (Build.VERSION.SDK_INT >= 29) rawFolder.trimEnd('/').substringAfterLast('/').ifBlank { "Memoria principale" }
                else rawFolder.substringBeforeLast('/', "").substringAfterLast('/').ifBlank { "Memoria principale" }
                found += AudioTrack(trackId, cursor.getString(title) ?: "Senza titolo", cleanMeta(cursor.getString(artist), "Artista sconosciuto"), cleanMeta(cursor.getString(album), "Album sconosciuto"), cleanFolder, cursor.getLong(duration), ContentUris.withAppendedId(collection, trackId))
            }
        }
        found
    }

    private fun cleanMeta(value: String?, fallback: String) = value?.takeUnless { it == "<unknown>" || it.isBlank() } ?: fallback

    private fun loadReviewStates(): Map<Long, ReviewState> = prefs.all.mapNotNull { (key, value) ->
        if (!key.startsWith("review_")) null else key.removePrefix("review_").toLongOrNull()?.let { id -> runCatching { id to ReviewState.valueOf(value as String) }.getOrNull() }
    }.toMap()

    private fun saveReviewStates(states: Map<Long, ReviewState>) = prefs.edit().apply { states.forEach { (id, state) -> putString("review_$id", state.name) } }.apply()

    private fun loadPlaylists(): Map<String, Set<Long>> = prefs.all.mapNotNull { (key, value) ->
        if (!key.startsWith("playlist_")) null else key.removePrefix("playlist_") to ((value as? Set<*>)?.mapNotNull { it.toString().toLongOrNull() }?.toSet() ?: emptySet<Long>())
    }.toMap()

    private fun savePlaylists(playlists: Map<String, Set<Long>>) = prefs.edit().apply {
        playlists.forEach { (name, ids) -> putStringSet("playlist_$name", ids.map(Long::toString).toSet()) }
    }.apply()

    private fun formatDuration(ms: Long): String = "%d:%02d".format(ms / 60000, (ms / 1000) % 60)
}
