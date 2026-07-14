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

@Composable fun SettingsScreen(state: UiState, folders: Set<String>, add: () -> Unit, scan: () -> Unit, remove: (String) -> Unit) { Column(Modifier.fillMaxSize()) { Header("Impostazioni", "Cartelle e catalogazione"); Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(add) { Icon(Icons.Default.CreateNewFolder, null); Spacer(Modifier.width(6.dp)); Text("AGGIUNGI") }; OutlinedButton(scan, enabled = !state.scanning) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(6.dp)); Text("SCANSIONA") } }; if (state.scanning) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp)); Text("Cartelle autorizzate", Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium); LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { if (folders.isEmpty()) item { Text("Nessuna cartella selezionata", color = MaterialTheme.colorScheme.onSurfaceVariant) }; items(folders.toList()) { folder -> Card { Row(Modifier.fillMaxWidth().padding(12.dp)) { Icon(Icons.Default.Folder, null); Spacer(Modifier.width(10.dp)); Text(folder, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis); IconButton({ remove(folder) }) { Icon(Icons.Default.Delete, "Rimuovi") } } } } } } }
