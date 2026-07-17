package com.alessandro.falco.desktop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.JFileChooser

private val FalcoDark = darkColorScheme(primary = Color(0xFFFFB000), background = Color(0xFF090A0E), surface = Color(0xFF181820))

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "FALCO Desktop") { MaterialTheme(colorScheme = FalcoDark) { FalcoDesktop() } }
}

@Composable private fun FalcoDesktop() {
    val db = remember { CatalogDatabase() }; val player = remember { AudioPlayer() }; val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }; var tracks by remember { mutableStateOf(db.all()) }
    var status by remember { mutableStateOf("Pronto") }; var busy by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf("") }; var user by remember { mutableStateOf("") }; var password by remember { mutableStateOf("") }

    fun refresh() { tracks = db.all(query) }
    fun play(track: Track) { scope.launch {
        busy = true; status = "Preparazione ${track.title}"
        runCatching {
            val path = if (track.source == "LOCAL") Path.of(java.net.URI(track.uri)) else {
                val ext = track.format.lowercase().ifBlank { "audio" }
                val cached = Path.of(System.getProperty("user.home"), ".falco", "cache", "${track.id}.$ext")
                if (!Files.exists(cached)) withContext(Dispatchers.IO) { WebDavClient(WebDavConfig(url, user, password)).download(track.uri, cached) }
                cached
            }
            player.play(path); status = "In riproduzione: ${track.title}"
        }.onFailure { status = "Errore player: ${it.message}" }
        busy = false
    } }

    Row(Modifier.fillMaxSize()) {
        Surface(Modifier.width(245.dp).fillMaxHeight(), color = Color(0xFF121319)) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("FALCO", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
                Text("Windows catalog organizer")
                HorizontalDivider()
                Text("${tracks.size} brani nel catalogo")
                Button(enabled = !busy, onClick = {
                    val chooser = JFileChooser().apply { fileSelectionMode = JFileChooser.DIRECTORIES_ONLY }
                    if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) scope.launch {
                        busy = true; status = "Scansione cartella locale…"
                        withContext(Dispatchers.IO) { LocalScanner.scan(chooser.selectedFile.toPath(), db::upsert) }
                        refresh(); busy = false; status = "Scansione completata"
                    }
                }) { Text("Aggiungi cartella") }
                OutlinedTextField(url, { url = it }, label = { Text("URL WebDAV") })
                OutlinedTextField(user, { user = it }, label = { Text("Utente") })
                OutlinedTextField(password, { password = it }, label = { Text("Password") })
                Button(enabled = !busy && WebDavConfig(url, user, password).ready, onClick = { scope.launch {
                    busy = true; status = "Scansione WebDAV in sola lettura…"
                    runCatching { withContext(Dispatchers.IO) { WebDavClient(WebDavConfig(url, user, password)).scan(db::upsert) } }
                        .onSuccess { refresh(); status = "WebDAV completato" }.onFailure { status = "Errore WebDAV: ${it.message}" }
                    busy = false
                } }) { Text("Scansiona WebDAV") }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(status, style = MaterialTheme.typography.bodySmall)
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight().padding(24.dp)) {
            Text("Libreria", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(query, { query = it; tracks = db.all(it) }, Modifier.fillMaxWidth(), label = { Text("Cerca titolo, artista, album, genere o tag") })
            Spacer(Modifier.height(12.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(tracks, key = { it.id }) { track ->
                    Card(Modifier.fillMaxWidth().clickable { play(track) }) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(track.title, style = MaterialTheme.typography.titleMedium)
                                Text(listOf(track.artist, track.album, track.genre, track.format, track.source).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                            }
                            TextButton(onClick = { db.setStatus(track.id, "REJECT"); refresh() }) { Text("SCARTA") }
                            TextButton(onClick = { db.setStatus(track.id, "MAYBE"); refresh() }) { Text("DOPO") }
                            Button(onClick = { db.setStatus(track.id, "KEEP"); refresh() }) { Text("TIENI") }
                        }
                    }
                }
            }
        }
    }
}

