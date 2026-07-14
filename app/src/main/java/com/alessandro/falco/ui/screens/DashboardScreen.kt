package com.alessandro.falco.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alessandro.falco.ui.UiState

@Composable fun DashboardScreen(state: UiState, addFolder: () -> Unit, scan: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Header("FALCO", "Fast Audio Library Catalog Organizer") { IconButton(scan, enabled = !state.scanning) { Icon(Icons.Default.Refresh, "Scansiona") } }
        if (state.scanning) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("${state.scanProgress?.scanned ?: 0} • ${state.scanProgress?.current.orEmpty()}", Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 20.dp)) }
        if (!state.loading && state.tracks.isEmpty() && !state.scanning) {
            Box(Modifier.weight(1f).fillMaxWidth()) { Card(Modifier.padding(20.dp).fillMaxWidth()) { Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { Text("La libreria parte dalle tue cartelle", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black); Text("Scegli una o più cartelle musicali. FALCO conserverà l’accesso e catalogherà i file localmente, senza cloud obbligatorio.", color = MaterialTheme.colorScheme.onSurfaceVariant); Button(addFolder) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("SCEGLI CARTELLA") } } } }
        } else {
            val stats = listOf("Brani" to state.stats.count.toString(), "Durata" to duration(state.stats.totalDuration), "Spazio" to bytes(state.stats.totalBytes), "Artisti" to state.stats.artists.toString(), "Generi" to state.stats.genres.toString(), "Duplicati" to state.duplicates.sumOf { it.tracks.size }.toString())
            LazyVerticalGrid(GridCells.Fixed(2), contentPadding = PaddingValues(20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { items(stats) { (label, value) -> Card { Column(Modifier.padding(18.dp)) { Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black); Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant) } } } }
        }
    }
}
