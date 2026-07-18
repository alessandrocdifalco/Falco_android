package com.alessandro.falco.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alessandro.falco.data.TrackEntity
import com.alessandro.falco.ui.UiState

@Composable fun AnalysisScreen(state: UiState, select: (TrackEntity?) -> Unit) { Column(Modifier.fillMaxSize()) { Header("Analisi", "Probabili duplicati per titolo, artista e durata"); if (state.duplicates.isEmpty()) Text("Nessun probabile duplicato rilevato.", Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) else LazyColumn(contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 90.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { items(state.duplicates) { group -> Card { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("${group.tracks.size} copie probabili", fontWeight = FontWeight.Bold); group.tracks.forEach { track -> TextButton({ select(track) }, Modifier.fillMaxWidth()) { Column(Modifier.fillMaxWidth()) { Text(track.title); Text("${track.artist} • ${bytes(track.sizeBytes)} • ${track.relativePath}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } } } } } } } } }
