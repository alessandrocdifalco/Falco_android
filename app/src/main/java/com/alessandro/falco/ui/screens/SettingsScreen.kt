package com.alessandro.falco.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alessandro.falco.ui.UiState

@Composable fun SettingsScreen(state: UiState, folders: Set<String>, add: () -> Unit, scan: () -> Unit, remove: (String) -> Unit, downloadMaest: () -> Unit, removeMaest: () -> Unit, analyzeLibrary: () -> Unit, cancelAnalysis: () -> Unit) { Column(Modifier.fillMaxSize()) { Header("Impostazioni", "Cartelle, catalogazione e AI locale"); LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    item { Card { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { Row { Icon(Icons.Default.AutoAwesome, null); Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text("MAEST locale (sperimentale)", style = MaterialTheme.typography.titleMedium); Text(state.maest.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }; if (state.maest.downloading) LinearProgressIndicator(progress = { state.maest.progress / 100f }, modifier = Modifier.fillMaxWidth()); if (state.maest.installed) OutlinedButton(removeMaest, Modifier.fillMaxWidth()) { Text("RIMUOVI MODELLO") } else Button(downloadMaest, Modifier.fillMaxWidth(), enabled = !state.maest.downloading) { Text("SCARICA MODELLO MAEST") }; Text("Il modello resta nella memoria privata. I brani e WebDAV non vengono modificati.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
    if (state.maest.installed) item { Card { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { Icon(Icons.Default.BatchPrediction, null); Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text("Pre-analisi della libreria", style = MaterialTheme.typography.titleMedium); Text(if (state.libraryAnalyzing) state.libraryAnalysisTrack.ifBlank { "Preparazione…" } else "Waveform e generi pronti prima della revisione", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        if (state.libraryAnalyzing) { val total = state.libraryAnalysisTotal.coerceAtLeast(1); LinearProgressIndicator(progress = { state.libraryAnalysisDone.toFloat() / total }, Modifier.fillMaxWidth()); Text("${state.libraryAnalysisDone} / ${state.libraryAnalysisTotal} • continua anche lasciando questa schermata", style = MaterialTheme.typography.bodySmall); OutlinedButton(cancelAnalysis, Modifier.fillMaxWidth()) { Text("INTERROMPI") } }
        else Button(analyzeLibrary, Modifier.fillMaxWidth()) { Icon(Icons.Default.OfflineBolt, null); Spacer(Modifier.width(6.dp)); Text("PRE-ANALIZZA BRANI") }
        Text("Scarica e decodifica solo per l’analisi. Nessun file locale o WebDAV viene modificato.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } } }
    item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(add) { Icon(Icons.Default.CreateNewFolder, null); Spacer(Modifier.width(6.dp)); Text("AGGIUNGI") }; OutlinedButton(scan, enabled = !state.scanning) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(6.dp)); Text("SCANSIONA") } } }
    if (state.scanning) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
    item { Text("Cartelle autorizzate", style = MaterialTheme.typography.titleMedium) }
    if (folders.isEmpty()) item { Text("Nessuna cartella selezionata", color = MaterialTheme.colorScheme.onSurfaceVariant) }
    items(folders.toList()) { folder -> Card { Row(Modifier.fillMaxWidth().padding(12.dp)) { Icon(Icons.Default.Folder, null); Spacer(Modifier.width(10.dp)); Text(folder, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis); IconButton({ remove(folder) }) { Icon(Icons.Default.Delete, "Rimuovi") } } } }
    item { Spacer(Modifier.height(90.dp)) }
} } }
