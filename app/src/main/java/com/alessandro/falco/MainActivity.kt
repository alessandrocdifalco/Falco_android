package com.alessandro.falco

import android.Manifest
import android.content.ContentUris
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

data class AudioTrack(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val uri: Uri
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(color = Black, modifier = Modifier.fillMaxSize()) { FalcoApp() }
            }
        }
    }

    @Composable
    private fun FalcoApp() {
        val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE
        var granted by remember {
            mutableStateOf(ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED)
        }
        var tracks by remember { mutableStateOf(emptyList<AudioTrack>()) }
        var query by remember { mutableStateOf("") }
        var current by remember { mutableStateOf<AudioTrack?>(null) }
        var playing by remember { mutableStateOf(false) }
        var player by remember { mutableStateOf<MediaPlayer?>(null) }

        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
        DisposableEffect(Unit) { onDispose { player?.release() } }
        LaunchedEffect(granted) { if (granted) tracks = loadTracks() }

        val shown = remember(tracks, query) {
            tracks.filter {
                query.isBlank() || it.title.contains(query, true) ||
                    it.artist.contains(query, true) || it.album.contains(query, true)
            }
        }

        Column(Modifier.fillMaxSize().padding(18.dp)) {
            Header(tracks.size)
            Spacer(Modifier.height(18.dp))

            if (!granted) {
                PermissionPanel { launcher.launch(permission) }
                return@Column
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Cerca titolo, artista o album") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Accent,
                    focusedLabelColor = Accent,
                    unfocusedLabelColor = Muted
                )
            )
            Spacer(Modifier.height(12.dp))

            if (shown.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Nessun brano trovato", color = Color.White, fontWeight = FontWeight.Bold)
                        Text("Copia musica sul dispositivo e riapri FALCO.", color = Muted)
                    }
                }
            } else {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    items(shown, key = { it.id }) { track ->
                        TrackRow(track, current?.id == track.id, playing) {
                            if (current?.id == track.id) {
                                if (playing) player?.pause() else player?.start()
                                playing = !playing
                            } else {
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
                        }
                    }
                }
            }

            current?.let { track ->
                Spacer(Modifier.height(10.dp))
                NowPlaying(track, playing) {
                    if (playing) player?.pause() else player?.start()
                    playing = !playing
                }
            }
        }
    }

    @Composable
    private fun Header(count: Int) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.background(Accent, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 6.dp)) {
                Text("F", color = Black, fontWeight = FontWeight.Black, fontSize = 22.sp)
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text("FALCO", color = Color.White, fontWeight = FontWeight.Black, fontSize = 25.sp)
                Text("FAST AUDIO LIBRARY", color = Muted, fontSize = 11.sp, letterSpacing = 1.3.sp)
            }
            Spacer(Modifier.weight(1f))
            Text("$count BRANI", color = Accent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
    }

    @Composable
    private fun PermissionPanel(onGrant: () -> Unit) {
        Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("La tua libreria, finalmente sotto controllo.", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text("FALCO legge i file audio presenti sul dispositivo. I brani restano sul telefono: niente cloud obbligatorio e niente server da mantenere in vita con offerte votive.", color = Muted)
                Button(onClick = onGrant, colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Black)) {
                    Text("CONSENTI ACCESSO", fontWeight = FontWeight.Black)
                }
            }
        }
    }

    @Composable
    private fun TrackRow(track: AudioTrack, selected: Boolean, playing: Boolean, onClick: () -> Unit) {
        Card(
            Modifier.fillMaxWidth().clickable(onClick = onClick),
            colors = CardDefaults.cardColors(containerColor = if (selected) Color(0xFF222B1B) else Panel)
        ) {
            Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.background(if (selected) Accent else Color(0xFF252B31), RoundedCornerShape(8.dp)).padding(horizontal = 11.dp, vertical = 9.dp)) {
                    Text(if (selected && playing) "Ⅱ" else "▶", color = if (selected) Black else Color.White)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(track.title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${track.artist}  •  ${track.album}", color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(formatDuration(track.durationMs), color = Muted, fontSize = 12.sp)
            }
        }
    }

    @Composable
    private fun NowPlaying(track: AudioTrack, playing: Boolean, toggle: () -> Unit) {
        Card(colors = CardDefaults.cardColors(containerColor = Accent)) {
            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("IN RIPRODUZIONE", color = Black.copy(alpha = .65f), fontSize = 10.sp, fontWeight = FontWeight.Black)
                    Text(track.title, color = Black, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(track.artist, color = Black.copy(alpha = .75f), fontSize = 12.sp)
                }
                Button(onClick = toggle, colors = ButtonDefaults.buttonColors(containerColor = Black, contentColor = Color.White)) {
                    Text(if (playing) "PAUSA" else "PLAY")
                }
            }
        }
    }

    private suspend fun loadTracks(): List<AudioTrack> = withContext(Dispatchers.IO) {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION
        )
        val found = mutableListOf<AudioTrack>()
        contentResolver.query(
            collection, projection,
            "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > 20000",
            null, "${MediaStore.Audio.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val id = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val title = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artist = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val album = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val duration = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            while (cursor.moveToNext()) {
                val trackId = cursor.getLong(id)
                found += AudioTrack(
                    trackId,
                    cursor.getString(title) ?: "Senza titolo",
                    cursor.getString(artist)?.takeUnless { it == "<unknown>" } ?: "Artista sconosciuto",
                    cursor.getString(album)?.takeUnless { it == "<unknown>" } ?: "Album sconosciuto",
                    cursor.getLong(duration),
                    ContentUris.withAppendedId(collection, trackId)
                )
            }
        }
        found
    }

    private fun formatDuration(ms: Long): String = "%d:%02d".format(ms / 60000, (ms / 1000) % 60)
}
