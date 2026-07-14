package com.alessandro.falco.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alessandro.falco.data.libraryInsight
import com.alessandro.falco.ui.UiState

@OptIn(ExperimentalLayoutApi::class)
@Composable fun DashboardScreen(state: UiState, addFolder: () -> Unit, scan: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Header("FALCO", "Statistiche reali della tua libreria") { IconButton(scan, enabled = !state.scanning) { Icon(Icons.Default.Refresh, "Scansiona") } }
        if (state.scanning) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("${state.scanProgress?.scanned ?: 0} • ${state.scanProgress?.current.orEmpty()}", Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 20.dp)) }
        if (!state.loading && state.tracks.isEmpty() && !state.scanning) {
            Box(Modifier.weight(1f).fillMaxWidth()) { Card(Modifier.padding(20.dp).fillMaxWidth()) { Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { Text("La libreria parte dalle tue cartelle", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black); Text("Scegli una o più cartelle musicali. FALCO conserverà l’accesso e catalogherà i file localmente, senza cloud obbligatorio.", color = MaterialTheme.colorScheme.onSurfaceVariant); Button(addFolder) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("SCEGLI CARTELLA") } } } }
        } else {
            val duplicates = state.duplicates.sumOf { it.tracks.size }
            val insight = libraryInsight(state.tracks, duplicates)
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 100.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    FlowRow(Modifier.fillMaxWidth(), maxItemsInEachRow = 2, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Brani" to state.stats.count.toString(), "Durata" to duration(state.stats.totalDuration), "Spazio" to bytes(state.stats.totalBytes), "Artisti" to state.stats.artists.toString()).forEach { (label, value) ->
                            Card(Modifier.weight(1f)) { Column(Modifier.padding(14.dp)) { Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black); Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
                        }
                    }
                }
                item { Text("Salute del catalogo", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black) }
                item { Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ProgressMetric("Revisione", insight.reviewProgress)
                    ProgressMetric("Copertura AI MAEST", insight.aiCoverage)
                    ProgressMetric("Tassonomia", insight.tagCoverage)
                    ProgressMetric("Metadati essenziali", insight.metadataCoverage)
                } } }
                item { FlowRow(Modifier.fillMaxWidth(), maxItemsInEachRow = 2, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { insight.metrics.forEach { metric -> Card(Modifier.weight(1f)) { Column(Modifier.padding(14.dp)) { Text(metric.value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black); Text(metric.label); Text(metric.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } } } } }
                if (insight.advice.isNotEmpty()) item { Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) { Text("Cosa manca alla tua libreria", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black); insight.advice.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) } } } }
                item { DistributionCard("Generi principali", insight.genres, state.tracks.size) }
                item { DistributionCard("Formati", insight.formats, state.tracks.size) }
                item { DistributionCard("Rating", insight.ratings, state.tracks.size) }
            }
        }
    }
}

@Composable private fun ProgressMetric(label: String, progress: Float) {
    Column { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label, fontWeight = FontWeight.Bold); Text("${(progress * 100).toInt()}%") }; LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, Modifier.fillMaxWidth()) }
}

@Composable private fun DistributionCard(title: String, values: List<Pair<String, Int>>, total: Int) {
    Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black); values.forEach { (label, count) -> Column { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label); Text("$count • ${(count * 100f / total.coerceAtLeast(1)).toInt()}%", color = MaterialTheme.colorScheme.onSurfaceVariant) }; LinearProgressIndicator(progress = { count.toFloat() / total.coerceAtLeast(1) }, Modifier.fillMaxWidth().height(4.dp)) } } } }
}
