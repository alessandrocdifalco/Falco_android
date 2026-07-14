package com.alessandro.falco.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alessandro.falco.data.*
import com.alessandro.falco.ui.UiState
import kotlin.math.abs

@Composable fun ReviewScreen(state: UiState, preview: (TrackEntity) -> Unit, toggle: (TrackEntity) -> Unit, seek: (TrackEntity, Long) -> Unit, skip: (TrackEntity, Long) -> Unit, review: (TrackEntity, String, String, Int, Set<String>) -> Unit, undo: () -> Unit, loadWaveform: (TrackEntity) -> Unit) {
    val current = state.tracks.firstOrNull { it.workStatus == "DA_VALUTARE" || it.workStatus == "DA_TAGGARE" }
    var dragX by remember { mutableFloatStateOf(0f) }; var dragY by remember { mutableFloatStateOf(0f) }
    var genre by remember(current?.id) { mutableStateOf(current?.genre?.takeIf { value -> FalcoTaxonomy.genres.any { it.id == value } }.orEmpty()) }; var rating by remember(current?.id) { mutableIntStateOf(current?.rating ?: 0) }
    var tags by remember(current?.id) { mutableStateOf(current?.customTags?.split(',')?.filter { it.isNotBlank() }?.toSet().orEmpty()) }
    LaunchedEffect(current?.id) { current?.let(loadWaveform) }
    fun decide(status: String) { current?.let { review(it, status, genre, rating, tags) } }
    Column(Modifier.fillMaxSize()) {
        Header("Revisione", "${state.tracks.count { it.workStatus == "DA_VALUTARE" }} brani da ascoltare") { if (state.lastReviewed != null) IconButton(undo) { Icon(Icons.Default.Undo, "Annulla") } }
        if (current == null) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Revisione completata", style = MaterialTheme.typography.headlineSmall) }; return@Column }
        Card(Modifier.padding(horizontal = 16.dp).weight(1f).fillMaxWidth()) { Column(Modifier.fillMaxSize().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            // Only the artwork/title surface owns the Tinder gesture. Audio navigation below remains independent.
            Box(Modifier.fillMaxWidth().weight(1f).graphicsLayer { translationX = dragX; translationY = dragY * .18f; rotationZ = dragX / 65f }.pointerInput(current.id) {
                detectDragGestures(onDragEnd = { val action = when { dragX > 85 -> "KEEP"; dragX < -85 -> "REJECT"; dragY < -120 -> "MAYBE"; else -> null }; dragX = 0f; dragY = 0f; action?.let(::decide) }) { change, amount -> change.consume(); dragX += amount.x; dragY += amount.y }
            }) {
                if (abs(dragX) > 24f) Text(if (dragX > 0) "TIENI" else "SCARTA", color = if (dragX > 0) Color(0xFF69E38B) else Color(0xFFFF6B78), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, modifier = Modifier.align(if (dragX > 0) Alignment.TopStart else Alignment.TopEnd).padding(8.dp))
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    CoverArt(state.coverArt, state.coverLoading)
                    Spacer(Modifier.height(6.dp))
                    Text(current.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(current.artist, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
                val currentIsPlaying = state.playing?.id == current.id
                val reviewPosition = if (currentIsPlaying) state.position else 0L
                val activeDuration = if (currentIsPlaying) state.playbackDuration.takeIf { it > 0 } else null
                val reviewDuration = (activeDuration ?: current.durationMs).coerceAtLeast(1)
                WaveSeek(state.waveform, state.waveformLoading, reviewPosition, reviewDuration) { seek(current, it) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    IconButton({ skip(current, -30_000) }) { Icon(Icons.Default.Replay30, "Indietro 30 secondi") }
                    FilledIconButton({ if (state.playing?.id == current.id) toggle(current) else preview(current) }, Modifier.size(58.dp)) { Icon(if (state.playing?.id == current.id && state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null) }
                    IconButton({ skip(current, 30_000) }) { Icon(Icons.Default.Forward30, "Avanti 30 secondi") }
                }
                Text("${duration(reviewPosition)} / ${duration(reviewDuration)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } }
        Column(Modifier.fillMaxWidth().height(260.dp).padding(horizontal = 16.dp, vertical = 4.dp)) {
            if (state.maestAnalyzing) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("MAEST sta ascoltando 30 secondi…", style = MaterialTheme.typography.bodySmall) }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).heightIn(max = 42.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                state.aiSuggestion?.let { ai -> AssistChip(onClick = { ai.genre?.let { genre = it }; ai.rating?.let { rating = it }; tags = tags.filterNot { it.matches(Regex("E[1-5]")) || it.startsWith("SUB:") }.toSet() + "E${ai.energy}" + (ai.subgenre?.let { setOf("SUB:$it") } ?: emptySet()) }, label = { Text("AI: ${ai.genre ?: "impara"}${ai.subgenre?.let { " › $it" } ?: ""} • E${ai.energy}${ai.rating?.let { " • R$it" } ?: ""} • ${ai.confidence}%", maxLines = 1) }, leadingIcon = { Icon(Icons.Default.AutoAwesome, null) }) }
                state.maestPrediction?.let { ai -> AssistChip(onClick = { ai.genre?.let { genre = it }; ai.subgenre?.let { sub -> tags = tags.filterNot { it.startsWith("SUB:") }.toSet() + "SUB:$sub" } }, label = { Text("MAEST: ${ai.styles.take(3).joinToString(" · ") { "${it.label.substringAfter("---")} ${(it.score * 100).toInt()}%" }}", maxLines = 1) }, leadingIcon = { Icon(Icons.Default.Psychology, null) }) }
            }
            state.maestError?.let { Text("MAEST: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            ClassificationPanel(genre, { genre = it }, rating, { rating = it }, tags, { tags = it })
        }
        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            FilledTonalIconButton({ decide("REJECT") }, Modifier.size(58.dp), colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Color(0xFF4A2028))) { Icon(Icons.Default.Close, "Reject") }
            FilledTonalIconButton({ decide("MAYBE") }, Modifier.size(58.dp)) { Icon(Icons.Default.Schedule, "Maybe") }
            FilledIconButton({ decide("KEEP") }, Modifier.size(58.dp)) { Icon(Icons.Default.Check, "Keep") }
        }
    }
}

@Composable private fun CoverArt(bytes: ByteArray?, loading: Boolean) {
    val bitmap = remember(bytes) { bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }?.asImageBitmap() }
    Card(Modifier.size(116.dp), shape = MaterialTheme.shapes.medium) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (bitmap != null) Image(bitmap, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else if (loading) CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
            else Icon(Icons.Default.Album, null, Modifier.size(58.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = .75f))
        }
    }
}

@Composable private fun WaveSeek(peaks: List<Float>, loading: Boolean, position: Long, total: Long, seek: (Long) -> Unit) {
    if (loading) { LinearProgressIndicator(Modifier.fillMaxWidth()); return }
    var windowMs by remember { mutableLongStateOf(60_000L) }
    val actualWindow = windowMs.coerceAtMost(total); val start = (position - actualWindow / 2).coerceIn(0, (total - actualWindow).coerceAtLeast(0)); val end = start + actualWindow
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { listOf(30_000L to "30s", 60_000L to "60s", Long.MAX_VALUE to "Tutto").forEach { (value, label) -> FilterChip(selected = if (value == Long.MAX_VALUE) windowMs >= total else windowMs == value, onClick = { windowMs = if (value == Long.MAX_VALUE) total else value }, label = { Text(label, style = MaterialTheme.typography.labelSmall) }, modifier = Modifier.height(28.dp)) } }
    Canvas(Modifier.fillMaxWidth().height(58.dp).pointerInput(total, start, actualWindow) { detectDragGestures(onDragStart = { seek((start + it.x / size.width * actualWindow).toLong().coerceIn(0, total)) }) { change, _ -> seek((start + change.position.x / size.width * actualWindow).toLong().coerceIn(0, total)) } }) {
        val all = peaks.ifEmpty { listOf(.15f) }; val from = (start.toDouble() / total * all.size).toInt().coerceIn(0, all.lastIndex); val to = (end.toDouble() / total * all.size).toInt().coerceIn(from + 1, all.size); val data = all.subList(from, to); val played = ((position - start).toFloat() / actualWindow).coerceIn(0f, 1f)
        data.forEachIndexed { i, value -> val x = i * size.width / data.size; val wave = value.coerceIn(.025f, 1f) * size.height; drawLine(if (i.toFloat() / data.size <= played) Color(0xFFF9A90A) else Color(0xFF4B5262), Offset(x, (size.height-wave)/2), Offset(x, (size.height+wave)/2), maxOf(1.5f, size.width/data.size*.62f)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun ClassificationPanel(genre: String, setGenre: (String) -> Unit, rating: Int, setRating: (Int) -> Unit, tags: Set<String>, setTags: (Set<String>) -> Unit) {
    var page by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("Base", "Situazione", "Voce", "Carattere").forEachIndexed { index, label ->
                FilterChip(page == index, { page = index }, { Text(label, style = MaterialTheme.typography.labelSmall) }, modifier = Modifier.weight(1f))
            }
        }
        Box(Modifier.fillMaxWidth().height(166.dp)) {
            when (page) {
                0 -> Column {
                    TaxonomyStrip("Genere", FalcoTaxonomy.genres, genre) { setGenre(it); setTags(tags.filterNot { token -> token.startsWith("SUB:") }.toSet() + "SUB:$it") }
                    val selectedSub = tags.firstOrNull { it.startsWith("SUB:") }?.removePrefix("SUB:").orEmpty()
                    TaxonomyStrip("Sottogenere", FalcoTaxonomy.subgenres[genre].orEmpty(), selectedSub) { setTags(tags.filterNot { token -> token.startsWith("SUB:") }.toSet() + "SUB:$it") }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("Energia", Modifier.width(62.dp), fontWeight = FontWeight.Bold); FalcoTaxonomy.energy.forEach { item -> val token = item.id; FilterChip(token in tags, { setTags(tags.filterNot { it.matches(Regex("E[1-5]")) }.toSet() + token) }, { Text(token) }, modifier = Modifier.weight(1f)) } }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("Rating", Modifier.width(62.dp), fontWeight = FontWeight.Bold); (1..5).forEach { n -> IconButton({ setRating(n) }, Modifier.weight(1f)) { Icon(if (n <= rating) Icons.Default.Star else Icons.Default.StarBorder, null, tint = MaterialTheme.colorScheme.primary) } } }
                }
                1 -> Column {
                    TaxonomyMultiStrip("Uso", FalcoTaxonomy.usage, tags, setTags)
                    TaxonomyMultiStrip("Momento", FalcoTaxonomy.tags.filter { it.id in setOf("INTRO_BUONA", "BREAK_LUNGO", "SAFE", "BOMBA", "TROPPO_VELOCE", "DA_RIASCOLTARE", "RADIO_FRIENDLY") }, tags, setTags)
                    Text("Puoi selezionare più situazioni: verranno esportate nei commenti Engine.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                2 -> Column {
                    TaxonomySingleStrip("Voce", FalcoTaxonomy.voice, tags, setTags)
                    TaxonomyMultiStrip("Dettagli", FalcoTaxonomy.tags.filter { it.id in setOf("FAMOUS_VOCAL", "CANTATO", "STRUMENTALE") }, tags, setTags)
                }
                else -> Column {
                    TaxonomyMultiStrip("Mood", FalcoTaxonomy.tags.filter { it.id in setOf("GROOVY", "ELEGANTE", "SOLARE", "SCURO", "SEXY", "NOTTURNO", "CLASSIC_FEEL") }, tags, setTags)
                    TaxonomyMultiStrip("Elementi", FalcoTaxonomy.tags.filter { it.id in setOf("PIANO", "SAX", "FIATI", "CHITARRA", "BASSO_FORTE", "DUB", "PERCUSSIONI") }, tags, setTags)
                    TaxonomyMultiStrip("Versione", FalcoTaxonomy.tags.filter { it.id in setOf("FAMOUS_SAMPLE", "EDIT_UTILE", "REMIX") }, tags, setTags)
                }
            }
        }
    }
}

@Composable private fun TaxonomyStrip(label: String, items: List<TaxonomyItem>, selected: String, choose: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(92.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) { items.forEach { item -> FilterChip(selected == item.id, { choose(item.id) }, { Text(item.label, maxLines = 1, style = MaterialTheme.typography.labelSmall) }) } }
    }
}

@Composable private fun TaxonomyMultiStrip(label: String, items: List<TaxonomyItem>, selected: Set<String>, update: (Set<String>) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(76.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            items.forEach { item -> FilterChip(item.id in selected, { update(if (item.id in selected) selected - item.id else selected + item.id) }, { Text(item.label, maxLines = 1, style = MaterialTheme.typography.labelSmall) }) }
        }
    }
}

@Composable private fun TaxonomySingleStrip(label: String, items: List<TaxonomyItem>, selected: Set<String>, update: (Set<String>) -> Unit) {
    val ids = items.map { it.id }.toSet()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(76.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            items.forEach { item -> FilterChip(item.id in selected, { update(selected - ids + item.id) }, { Text(item.label, maxLines = 1, style = MaterialTheme.typography.labelSmall) }) }
        }
    }
}

@Composable private fun TaxonomyRow(label: String, items: List<TaxonomyItem>, selected: Set<String>, update: (Set<String>) -> Unit, prefix: String) { Text(label, style = MaterialTheme.typography.labelMedium); Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) { items.forEach { item -> val token = if (prefix == "E") item.id else prefix + item.id; FilterChip(token in selected, { update(selected.filterNot { if (prefix == "E") it.matches(Regex("E[1-5]")) else it.startsWith(prefix) }.toSet() + token) }, { Text(item.label) }) } } }
