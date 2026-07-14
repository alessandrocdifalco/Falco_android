package com.alessandro.falco.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alessandro.falco.ui.UiState
import com.alessandro.falco.webdav.*

@Composable fun WebDavScreen(state: UiState, save: (WebDavConfig) -> Unit, test: (WebDavConfig) -> Unit, browse: (String) -> Unit, scan: (String, Boolean) -> Unit, play: (WebDavItem) -> Unit) {
    var configure by remember(state.webDavConfig.ready) { mutableStateOf(!state.webDavConfig.ready) }
    Column(Modifier.fillMaxSize()) {
        Header("WebDAV", if (configure) "Configurazione server" else state.webDavConfig.name) { IconButton({ configure = !configure }) { Icon(if (configure) Icons.Default.Folder else Icons.Default.Settings, null) } }
        if (configure) WebDavConfiguration(state, save, test) else WebDavExplorer(state, browse, scan, play)
    }
}

@Composable private fun WebDavConfiguration(state: UiState, save: (WebDavConfig) -> Unit, test: (WebDavConfig) -> Unit) {
    var config by remember(state.webDavConfig) { mutableStateOf(state.webDavConfig) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp, 0.dp, 18.dp, 100.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Card { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Dns, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(10.dp)); Text("Configurazione server", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            OutlinedTextField(config.name, { config = config.copy(name = it) }, label = { Text("Nome connessione") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(config.serverUrl, { config = config.copy(serverUrl = it) }, label = { Text("URL server") }, placeholder = { Text("https://server.example.com/webdav") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(config.username, { config = config.copy(username = it) }, label = { Text("Username") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(config.password, { config = config.copy(password = it) }, label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedButton({ test(config) }, enabled = config.ready && !state.webDavBusy) { Icon(Icons.Default.WifiFind, null); Spacer(Modifier.width(6.dp)); Text("TESTA") }; Button({ save(config) }, enabled = config.ready && !state.webDavBusy) { Text("SALVA") } }
        } } }
        state.webDavMessage?.let { message -> item { Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = .14f))) { Text(message, Modifier.padding(14.dp)) } } }
        item { Text("FALCO usa il collegamento direttamente dal telefono e accede in sola lettura. Le credenziali vengono cifrate sul dispositivo e non passano da Lovable o Supabase.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable private fun WebDavExplorer(state: UiState, browse: (String) -> Unit, scan: (String, Boolean) -> Unit, play: (WebDavItem) -> Unit) {
    var recursive by remember { mutableStateOf(true) }
    LaunchedEffect(state.webDavConfig, state.webDavItems) { if (state.webDavConfig.ready && state.webDavItems.isEmpty()) browse("/") }
    Column(Modifier.fillMaxSize()) {
        Card(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(state.webDavPath, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state.webDavPath != "/") IconButton({ browse(parent(state.webDavPath)) }) { Icon(Icons.Default.ArrowUpward, "Cartella superiore") }
                Row(Modifier.weight(1f).clickable { recursive = !recursive }, verticalAlignment = Alignment.CenterVertically) { Checkbox(recursive, { recursive = it }); Text("Includi sottocartelle") }
                Button({ scan(state.webDavPath, recursive) }, enabled = !state.webDavBusy) { Icon(Icons.Default.Radar, null); Spacer(Modifier.width(5.dp)); Text("SCANSIONA") }
            }
        } }
        if (state.webDavBusy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 10.dp))
        state.webDavMessage?.let { Text(it, Modifier.padding(horizontal = 18.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 100.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            val sorted = state.webDavItems.sortedWith(compareByDescending<WebDavItem> { it.directory }.thenBy { it.name.lowercase() })
            items(sorted, key = { it.url }) { item -> Card(Modifier.fillMaxWidth().clickable(enabled = item.directory) { browse(item.path) }) { Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (item.directory) Icons.Default.Folder else Icons.Default.AudioFile, null, tint = if (item.directory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis); if (!item.directory) Text(bytes(item.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (item.directory) Icon(Icons.Default.ChevronRight, null) else IconButton({ play(item) }) { Icon(Icons.Default.PlayArrow, "Ascolta") }
            } } }
        }
    }
}

private fun parent(path: String): String { val parts = path.trim('/').split('/').filter { it.isNotBlank() }; return if (parts.size <= 1) "/" else "/" + parts.dropLast(1).joinToString("/") + "/" }
