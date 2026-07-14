package com.alessandro.falco.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alessandro.falco.ui.UiState
import kotlin.math.roundToInt

@Composable fun AiLearningScreen(state: UiState) {
    val stats = state.aiLearning
    val maestCount = state.tracks.count { it.maestCache.isNotBlank() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Header("AI personale", "Quanto FALCO sta imparando dalle tue scelte") }
        item { Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard("${stats.samples}", "Esempi appresi", Modifier.weight(1f)); MetricCard("${stats.decisions}", "Decisioni", Modifier.weight(1f)); MetricCard("${stats.rejected}", "Scarti appresi", Modifier.weight(1f))
        } }
        item { Card(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Misure verificabili", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
            LearningRow("Accordo genere", stats.genreAccuracy?.let { "$it%" } ?: "Servono scelte")
            LearningRow("Accordo ultime 30", stats.recentGenreAccuracy?.let { "$it%" } ?: "—")
            LearningRow("Prime 30 precedenti", stats.previousGenreAccuracy?.let { "$it%" } ?: "—")
            LearningRow("Errore medio Energy", stats.energyError?.let { "${String.format(java.util.Locale.US, "%.1f", it)} livelli" } ?: "—")
            val coverage = if (state.tracks.isEmpty()) 0f else maestCount.toFloat() / state.tracks.size
            Text("Copertura MAEST • $maestCount/${state.tracks.size}"); LinearProgressIndicator(progress = { coverage }, Modifier.fillMaxWidth())
            Text("L’accordo confronta la previsione mostrata prima dello swipe con la tua scelta successiva. Non è una percentuale inventata dal modello.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } } }
        item { Card(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row { Icon(Icons.Default.Psychology, null); Spacer(Modifier.width(8.dp)); Text("Stato apprendimento", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black) }
            Text(when { stats.samples < 10 -> "Inizio: FALCO ha ancora pochi esempi personali."; stats.samples < 50 -> "Sta costruendo il tuo profilo: i suggerimenti iniziano a diventare personali."; stats.samples < 200 -> "Profilo utile: somiglianze e probabilità di scarto hanno una base concreta."; else -> "Profilo maturo: FALCO dispone di una buona cronologia delle tue scelte." })
            stats.recentGenreAccuracy?.let { recent -> stats.previousGenreAccuracy?.let { previous -> Text(if (recent > previous) "Miglioramento recente: +${recent - previous} punti." else if (recent < previous) "Accordo recente in calo di ${previous - recent} punti: probabilmente stai ampliando la libreria." else "Accordo stabile nelle ultime scelte.", color = MaterialTheme.colorScheme.primary) } }
        } } }
        if (stats.genres.isNotEmpty()) item { Distribution("Generi insegnati", stats.genres.map { it.first to it.second }, stats.samples) }
        if (stats.energies.isNotEmpty()) item { Distribution("Energy insegnata", stats.energies.map { "E${it.first}" to it.second }, stats.energies.sumOf { it.second }) }
    }
}

@Composable private fun MetricCard(value: String, label: String, modifier: Modifier) { Card(modifier) { Column(Modifier.padding(12.dp)) { Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black); Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
@Composable private fun LearningRow(label: String, value: String) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label); Text(value, fontWeight = FontWeight.Bold) } }
@Composable private fun Distribution(title: String, values: List<Pair<String, Int>>, total: Int) { Card(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black); values.forEach { (label, count) -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label); Text("$count • ${(count * 100f / total.coerceAtLeast(1)).roundToInt()}%", color = MaterialTheme.colorScheme.onSurfaceVariant) } } } } }
