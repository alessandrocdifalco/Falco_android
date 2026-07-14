package com.alessandro.falco.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.alessandro.falco.data.TrackEntity
import com.alessandro.falco.ui.*

@Composable fun LibraryScreen(state: UiState, query: (String) -> Unit, filters: (Filters) -> Unit, sort: (SortMode) -> Unit, select: (TrackEntity?) -> Unit, play: (TrackEntity) -> Unit, favorite: (TrackEntity) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Header("Libreria", "${state.visible.size} di ${state.tracks.size} brani")
        OutlinedTextField(state.query, query, Modifier.fillMaxWidth().padding(horizontal = 16.dp), leadingIcon = { Icon(Icons.Default.Search, null) }, trailingIcon = { if (state.query.isNotBlank()) IconButton({ query("") }) { Icon(Icons.Default.Clear, null) } }, placeholder = { Text("Titolo, artista, album, genere o tag") }, singleLine = true)
        FilterBar(state, filters, sort)
        if (state.visible.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth()) { Text(if (state.tracks.isEmpty()) "Aggiungi una cartella dalle Impostazioni" else "Nessun brano corrisponde ai filtri", Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        else LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(12.dp, 4.dp, 12.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) { items(state.visible, key = { it.id }) { t -> TrackRow(t, { play(t) }, { select(t) }, { favorite(t) }) } }
    }
}

@Composable private fun FilterBar(state: UiState, update: (Filters) -> Unit, sort: (SortMode) -> Unit) {
    var menu by remember { mutableStateOf<String?>(null) }
    val choices = mapOf("Genere" to state.tracks.map { it.genre }.distinct().sorted(), "Artista" to state.tracks.map { it.artist }.distinct().sorted(), "Formato" to state.tracks.map { it.format }.distinct().sorted(), "Anno" to state.tracks.mapNotNull { it.year }.distinct().sortedDescending().map(Int::toString), "Rating" to (1..5).map { "$it+ stelle" }, "Stato" to com.alessandro.falco.data.WorkStatus.entries.map { it.label })
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(state.filters.favorites, { update(state.filters.copy(favorites = !state.filters.favorites)) }, { Text("Preferiti") }, leadingIcon = { Icon(Icons.Default.Favorite, null) })
        choices.forEach { (name, values) -> Box { FilterChip(menu == name, { menu = name }, { Text(name) }); DropdownMenu(menu == name, { menu = null }) { DropdownMenuItem({ Text("Tutti") }, { update(clearFilter(state.filters, name)); menu = null }); values.forEach { value -> DropdownMenuItem({ Text(value) }, { update(setFilter(state.filters, name, value)); menu = null }) } } } }
        Box { FilterChip(false, { menu = "Ordina" }, { Text("Ordina: ${state.sort.label}") }, leadingIcon = { Icon(Icons.Default.Sort, null) }); DropdownMenu(menu == "Ordina", { menu = null }) { SortMode.entries.forEach { value -> DropdownMenuItem({ Text(value.label) }, { sort(value); menu = null }) } } }
        if (state.filters != Filters()) AssistChip({ update(Filters()) }, { Text("Azzera") }, leadingIcon = { Icon(Icons.Default.Clear, null) })
    }
}

private fun clearFilter(f: Filters, name: String) = when (name) { "Genere" -> f.copy(genre = null); "Artista" -> f.copy(artist = null); "Formato" -> f.copy(format = null); "Anno" -> f.copy(year = null); "Rating" -> f.copy(rating = null); else -> f.copy(status = null) }
private fun setFilter(f: Filters, name: String, v: String) = when (name) { "Genere" -> f.copy(genre = v); "Artista" -> f.copy(artist = v); "Formato" -> f.copy(format = v); "Anno" -> f.copy(year = v.toIntOrNull()); "Rating" -> f.copy(rating = v.substringBefore('+').toInt()); else -> f.copy(status = com.alessandro.falco.data.WorkStatus.entries.first { it.label == v }.name) }
