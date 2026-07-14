package com.alessandro.falco.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alessandro.falco.data.*
import com.alessandro.falco.ui.UiState
import kotlin.math.abs

@Composable fun ReviewScreen(state: UiState, preview: (TrackEntity) -> Unit, toggle: (TrackEntity) -> Unit, seek: (Long) -> Unit, skip: (Long) -> Unit, review: (TrackEntity, String, String, Int, Set<String>) -> Unit, undo: () -> Unit, loadWaveform: (TrackEntity) -> Unit) {
    val current = state.tracks.firstOrNull { it.workStatus == "DA_VALUTARE" || it.workStatus == "DA_TAGGARE" }
    var classify by remember(current?.id) { mutableStateOf(false) }
    var dragX by remember { mutableFloatStateOf(0f) }; var dragY by remember { mutableFloatStateOf(0f) }
    var genre by remember(current?.id) { mutableStateOf(current?.genre?.takeIf { value -> FalcoTaxonomy.genres.any { it.id == value } }.orEmpty()) }; var rating by remember(current?.id) { mutableIntStateOf(current?.rating ?: 0) }
    var tags by remember(current?.id) { mutableStateOf(current?.customTags?.split(',')?.filter { it.isNotBlank() }?.toSet().orEmpty()) }
    LaunchedEffect(current?.id) { current?.let(loadWaveform) }
    fun decide(status: String) { current?.let { if (status == "KEEP" && !classify) classify = true else { review(it, status, genre, rating, tags); classify = false } } }
    Column(Modifier.fillMaxSize()) {
        Header("Revisione", "${state.tracks.count { it.workStatus == "DA_VALUTARE" }} brani da ascoltare") { if (state.lastReviewed != null) IconButton(undo) { Icon(Icons.Default.Undo, "Annulla") } }
        if (current == null) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Revisione completata", style = MaterialTheme.typography.headlineSmall) }; return@Column }
        Card(Modifier.padding(horizontal = 16.dp).weight(1f).fillMaxWidth().graphicsLayer { translationX = dragX; translationY = dragY; rotationZ = dragX / 45f }.pointerInput(current.id) {
            detectDragGestures(onDragEnd = { when { dragX > 180 -> decide("KEEP"); dragX < -180 -> decide("REJECT"); dragY < -160 -> decide("MAYBE") }; dragX = 0f; dragY = 0f }) { change, amount -> change.consume(); dragX += amount.x; dragY += amount.y }
        }) { Column(Modifier.fillMaxSize().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(current.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(current.artist, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            if (!classify) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { Icon(Icons.Default.GraphicEq, null, Modifier.size(110.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = .7f)) }
                Spacer(Modifier.height(14.dp)); WaveSeek(state.waveform, state.waveformLoading, state.position, (state.playbackDuration.takeIf { it > 0 } ?: current.durationMs).coerceAtLeast(1), seek)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    IconButton({ skip(-30_000) }) { Icon(Icons.Default.Replay30, "Indietro 30 secondi") }
                    FilledIconButton({ if (state.playing?.id == current.id) toggle(current) else preview(current) }, Modifier.size(58.dp)) { Icon(if (state.playing?.id == current.id && state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null) }
                    IconButton({ skip(30_000) }) { Icon(Icons.Default.Forward30, "Avanti 30 secondi") }
                }
                Text("${duration(state.position)} / ${duration(state.playbackDuration.takeIf { it > 0 } ?: current.durationMs)} • preview da 1:00", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            state.aiSuggestion?.let { ai -> AssistChip(onClick = { ai.genre?.let { genre = it }; ai.rating?.let { rating = it }; tags = tags.filterNot { it.matches(Regex("E[1-5]")) || it.startsWith("SUB:") }.toSet() + "E${ai.energy}" + (ai.subgenre?.let { setOf("SUB:$it") } ?: emptySet()) }, label = { Text("AI: ${ai.genre ?: "impara"}${ai.subgenre?.let { " › $it" } ?: ""} • E${ai.energy}${ai.rating?.let { " • R$it" } ?: ""} • ${ai.confidence}%", maxLines = 1) }, leadingIcon = { Icon(Icons.Default.AutoAwesome, null) }) }
            if (state.maestAnalyzing) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("MAEST sta ascoltando 30 secondi…", style = MaterialTheme.typography.bodySmall) }
            state.maestPrediction?.let { ai -> AssistChip(onClick = { ai.genre?.let { genre = it }; ai.subgenre?.let { sub -> tags = tags.filterNot { it.startsWith("SUB:") }.toSet() + "SUB:$sub" } }, label = { Text("MAEST: ${ai.styles.take(3).joinToString(" · ") { "${it.label.substringAfter("---")} ${(it.score * 100).toInt()}%" }}", maxLines = 2) }, leadingIcon = { Icon(Icons.Default.Psychology, null) }) }
            state.maestError?.let { Text("MAEST: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            if (classify) ClassificationPanel(genre, { genre = it }, rating, { rating = it }, tags, { tags = it })
        } }
        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            FilledTonalIconButton({ decide("REJECT") }, Modifier.size(58.dp), colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Color(0xFF4A2028))) { Icon(Icons.Default.Close, "Reject") }
            FilledTonalIconButton({ decide("MAYBE") }, Modifier.size(58.dp)) { Icon(Icons.Default.Schedule, "Maybe") }
            FilledIconButton({ decide("KEEP") }, Modifier.size(58.dp)) { Icon(Icons.Default.Check, "Keep") }
        }
    }
}

@Composable private fun WaveSeek(peaks: List<Float>, loading: Boolean, position: Long, total: Long, seek: (Long) -> Unit) {
    if (loading) { LinearProgressIndicator(Modifier.fillMaxWidth()); return }
    Canvas(Modifier.fillMaxWidth().height(64.dp).pointerInput(total) { detectDragGestures(onDragStart = { seek((it.x / size.width * total).toLong()) }) { change, _ -> seek((change.position.x / size.width * total).toLong().coerceIn(0, total)) } }) {
        val data = peaks.ifEmpty { listOf(.15f) }; val played = position.toFloat() / total
        data.forEachIndexed { i, value -> val x = i * size.width / data.size; val wave = value.coerceIn(.04f, 1f) * size.height; drawLine(if (i.toFloat() / data.size <= played) Color(0xFFF9A90A) else Color(0xFF4B5262), Offset(x, (size.height-wave)/2), Offset(x, (size.height+wave)/2), maxOf(2f, size.width/data.size*.55f)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun ClassificationPanel(genre: String, setGenre: (String) -> Unit, rating: Int, setRating: (Int) -> Unit, tags: Set<String>, setTags: (Set<String>) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text("Genere", fontWeight = FontWeight.Bold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy((-6).dp), maxItemsInEachRow = 3) { FalcoTaxonomy.genres.forEach { item -> FilterChip(genre == item.id, { setGenre(item.id); setTags(tags.filterNot { it.startsWith("SUB:") }.toSet() + "SUB:${item.id}") }, { Text(item.label, style = MaterialTheme.typography.labelSmall) }) } }
        Text(if (genre.isBlank()) "Sottogenere · scegli prima il genere" else "Sottogenere", fontWeight = FontWeight.Bold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy((-6).dp), maxItemsInEachRow = 5) { FalcoTaxonomy.subgenres[genre].orEmpty().forEach { item -> val token = "SUB:${item.id}"; FilterChip(token in tags, { setTags(tags.filterNot { it.startsWith("SUB:") }.toSet() + token) }, { Text(item.label, style = MaterialTheme.typography.labelSmall, maxLines = 1) }) } }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("Energia", Modifier.width(62.dp), fontWeight = FontWeight.Bold); FalcoTaxonomy.energy.forEach { item -> val token = item.id; FilterChip(token in tags, { setTags(tags.filterNot { it.matches(Regex("E[1-5]")) }.toSet() + token) }, { Text(token) }, modifier = Modifier.weight(1f)) } }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("Rating", Modifier.width(62.dp), fontWeight = FontWeight.Bold); (1..5).forEach { n -> IconButton({ setRating(n) }, Modifier.weight(1f)) { Icon(if (n <= rating) Icons.Default.Star else Icons.Default.StarBorder, null, tint = MaterialTheme.colorScheme.primary) } } }
    }
}

@Composable private fun TaxonomyRow(label: String, items: List<TaxonomyItem>, selected: Set<String>, update: (Set<String>) -> Unit, prefix: String) { Text(label, style = MaterialTheme.typography.labelMedium); Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) { items.forEach { item -> val token = if (prefix == "E") item.id else prefix + item.id; FilterChip(token in selected, { update(selected.filterNot { if (prefix == "E") it.matches(Regex("E[1-5]")) else it.startsWith(prefix) }.toSet() + token) }, { Text(item.label) }) } } }
