package com.alessandro.falco

import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

private val FalcoBlack = Color(0xFF0B0D10)
private val FalcoPanel = Color(0xFF15191E)
private val FalcoAccent = Color(0xFFB8FF3D)
private val FalcoMuted = Color(0xFF98A1AA)

data class AudioTrack(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val uri: android.net.Uri
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(color = FalcoBlack, modifier = Modifier.fillMaxSize()) {
                    FalcoApp()
                }
            }
        }
    }

    @Composable
    private fun FalcoApp() {
        val permission = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        var hasPermission by remember {
            mutableStateOf(ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED)
        }
        var tracks by remember { mutableStateOf(emptyList<AudioTrack>()) }
        var query by remember { mutableStateOf("") }
        var currentTrack by remember { mutableStateOf<AudioTrack?>(null) }
        var isPlaying by remember { mutableStateOf(false) }
        var player by remember { mutableStateOf<MediaPlayer?>(null) }

        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted -> hasPermission = granted }

        DisposableEffect(Unit) {
            onDispose { player?.release() }
        }

        LaunchedEffect(hasPermission) {
            if (hasPermission) tracks = loadTracks()
        }

        val visibleTracks = remember(tracks, query) {
            if (query.isBlank()) tracks
            else tracks.filter {
                it.title.contains(query, true) ||
                    it.artist.contains(query, true) ||
                    it.album.contains(query, true)
            }
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .background(FalcoAccent, RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text("F", color = FalcoBlack, fontWeight = FontWeight.Black, fontSize = 22.sp)
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("FALCO", color = Color.White, fontWeight = FontWeight.Black, fontSize = 25.sp)
                    Text("FAST AUDIO LIBRARY", color = FalcoMuted, fontSize = 11.sp, letterSpacing = 1.3.sp)
                }
                Spacer(Modifier.weight(1f))
                Text("${tracks.size} BRANI", color = FalcoAccent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }

            Spacer(Modifier.height(18.dp))

            if (!hasPermission) {
                PermissionPanel { permissionLauncher.launch(permission) }
            } else {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Cerca titolo, artista o album") }
                )
                Spacer(Modifier.height(12.dp))

                if (visibleTracks.isEmpty()) {
                    EmptyLibrary()
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        items(visibleTracks, key = { it.id }) { track ->
                            TrackRow(track, currentTrack?.id == track.id, isPlaying) {
                                if (currentTrack?.id == track.id) {
                                    if (isPlaying) player?.pause() else player?.start()
                                    isPlaying = !isPlaying
                                } else {
                                    player?.release()
                                    player = MediaPlayer().apply {
                                        setDataSource(this@MainActivity, track.uri)
                                        setOnCompletionListener { isPlaying = false }
                                        prepare()
                                        start()
                                    }
                                    currentTrack = track
                                    isPlaying = true
                                }
                            }
                        }
                    }
                }

                currentTrack?.let { track ->
                    Spacer(Modifier.height(10.dp))
                    NowPlaying(track, isPlaying) {
                        if (isPlaying) player?.pause() else player?.start()
                        isPlaying = !isPlaying
                    }
                }
            }
        }
    }

    @Composable
    private fun PermissionPanel(onGrant: () -> Unit) {
        Card(colors = CardDefaults.cardColors(containerColor = FalcoPanel)) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("La tua libreria, finalmente sotto controllo.", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text(
                    "FALCO deve poter leggere i file audio presenti sul dispositivo. I brani restano sul telefono: nessun cloud obbligatorio, nessun server da mantenere in vita con offerte votive.",
                    color = FalcoMuted
                )
                Button(
                    onClick = onGrant,
                    colors = ButtonDefaults.buttonColors(containerColor = FalcoAccent, contentColor = FalcoBlack)
                ) { Text("CONSENTI ACCESSO", fontWeight = FontWeight.Black) }
            }
        }
    }

    @Composable
    private fun EmptyLibrary() {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Nessun brano trovato", color = Color.White, fontWeight = FontWeight.Bold)
                Text("Copia musica sul dispositivo e riapri FALCO.", color = FalcoMuted)
            }
        }
    }

    @Composable
    private fun TrackRow(track: AudioTrack, selected: Boolean, playing: Boolean, onClick: () -> Unit) {
        Card(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
            colors = CardDefaults.cardColors(containerColor = if (selected) Color(0xFF222B1B) else FalcoPanel)
        ) {
            Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .background(if (selected) FalcoAccent else Color(0xFF252B31), RoundedCornerShape(8.dp))
                        .padding(horizontal = 11.dp, vertical = 9.dp)
                ) {
                    Text(if (selected && playing) "Ⅱ" else "▶", color = if (selected) FalcoBlack else Color.White)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(track.title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${track.artist}  •  ${track.album}", color = FalcoMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(formatDuration(track.durationMs), color = FalcoMuted, fontSize = 12.sp)
            }
        }
    }

    @Composable
    private fun NowPlaying(track: AudioTrack, playing: Boolean, onToggle: () -> Unit) {
        Card(colors = CardDefaults.cardColors(containerColor = FalcoAccent)) {
            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("IN RIPRODUZIONE", color = FalcoBlack.copy(alpha = .65f), fontSize = 10.sp, fontWeight = FontWeight.Black)
                    Text(track.title, color = FalcoBlack, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(track.artist, color = FalcoBlack.copy(alpha = .75f), fontSize = 12.sp)
                }
                Button(
                    onClick = onToggle,
                    colors = ButtonDefaults.buttonColors(containerColor = FalcoBlack, contentColor = Color.White)
                ) { Text(if (playing) "PAUSA" else "PLAY") }
            }
        }
    }

    private suspend fun loadTracks(): List<AudioTrack> = withContext(Dispatchers.IO) {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > 20000"
        val result = mutableListOf<AudioTrack>()

        contentResolver.query(
            collection,
            projection,
            selection,
            null,
            "${MediaStore.Audio.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                result += AudioTrack(
                    id = id,
                    title = cursor.getString(titleColumn) ?: "Senza titolo",
                    artist = cursor.getString(artistColumn)?.takeUnless { it == "<unknown>" } ?: "Artista sconosciuto",
                    album = cursor.getString(albumColumn)?.takeUnless { it == "<unknown>" } ?: "Album sconosciuto",
                    durationMs = cursor.getLong(durationColumn),
                    uri = ContentUris.withAppendedId(collection, id)
                )
            }
        }
        result
    }

    private fun formatDuration(ms: Long): String {
        val totalSeconds = ms / 1000
        return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
    }
}
