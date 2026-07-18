package com.alessandro.falco.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alessandro.falco.ui.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun PerformanceBar(state: UiState, cancelAnalysis: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars)) { Surface(Modifier.fillMaxWidth().height(34.dp).clickable { open = true }, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Memory, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Text("CPU ${state.performance.cpu}%", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Text("RAM ${state.performance.ramMb} MB", style = MaterialTheme.typography.labelMedium)
            Text("${state.performance.batteryC.toInt()}°C", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.weight(1f))
            Text(if (state.libraryAnalyzing) "MAEST ${state.effectiveParallelism}×" else state.performance.thermal, style = MaterialTheme.typography.labelMedium, color = if (state.performance.thermal == "Severo") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        }
    } }
    if (open) ModalBottomSheet(onDismissRequest = { open = false }) { Column(Modifier.fillMaxWidth().padding(20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Task Manager FALCO", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
        TaskRow("CPU processo", "${state.performance.cpu}% del telefono")
        TaskRow("Memoria PSS", "${state.performance.ramMb} MB")
        TaskRow("Batteria", "${state.performance.batteryC.toInt()} °C")
        TaskRow("Stato termico", state.performance.thermal)
        TaskRow("Modalità richiesta", "Turbo ${state.analysisParallelism}×")
        TaskRow("Parallelismo effettivo", "${state.effectiveParallelism}×")
        TaskRow("Riduzione automatica", if (state.automaticPerformanceScaling) "Attiva" else "Disattivata")
        if (state.libraryAnalyzing) {
            LinearProgressIndicator(progress = { state.libraryAnalysisDone.toFloat() / state.libraryAnalysisTotal.coerceAtLeast(1) }, Modifier.fillMaxWidth())
            Text("${state.libraryAnalysisDone}/${state.libraryAnalysisTotal} • ${state.libraryAnalysisTrack}")
            TaskRow("Fine stimata", eta(state.analysisEtaMs))
            OutlinedButton(cancelAnalysis, Modifier.fillMaxWidth()) { Text("INTERROMPI ANALISI") }
        } else Text("Nessuna analisi in esecuzione", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("CPU indica il carico del solo processo FALCO, normalizzato sulla capacità totale del dispositivo.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } }
}
private fun eta(ms: Long): String { if (ms <= 0) return "Calcolo…"; val m = ms / 60_000; return if (m >= 60) "${m / 60}h ${m % 60}m" else "${m.coerceAtLeast(1)} min" }
@Composable private fun TaskRow(label: String, value: String) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label); Text(value, fontWeight = FontWeight.Bold) } }
