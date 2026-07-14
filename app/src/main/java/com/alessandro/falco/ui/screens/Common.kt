package com.alessandro.falco.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alessandro.falco.data.TrackEntity
import com.alessandro.falco.data.WorkStatus
import com.alessandro.falco.ui.theme.FalcoLime

@Composable fun Header(title: String, subtitle: String? = null, action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black); subtitle?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        action?.invoke()
    }
}

fun duration(ms: Long): String { val minutes = ms / 60000; return if (minutes >= 60) "%dh %02dm".format(minutes / 60, minutes % 60) else "%d:%02d".format(minutes, ms / 1000 % 60) }
fun bytes(value: Long): String = when { value >= 1_073_741_824 -> "%.1f GB".format(value / 1_073_741_824.0); value >= 1_048_576 -> "%.1f MB".format(value / 1_048_576.0); else -> "%.0f KB".format(value / 1024.0) }

@Composable fun TrackRow(track: TrackEntity, play: () -> Unit, details: () -> Unit, favorite: () -> Unit) {
    Card(onClick = details, modifier = Modifier.fillMaxWidth()) { Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        FilledIconButton(onClick = play, colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) { Icon(Icons.Default.PlayArrow, null) }
        Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) {
            Text(track.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${track.artist} • ${track.format} • ${duration(track.durationMs)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        IconButton(onClick = favorite) { Icon(if (track.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null, tint = if (track.favorite) FalcoLime else MaterialTheme.colorScheme.onSurfaceVariant) }
    } }
}

@Composable fun DetailSheet(track: TrackEntity, dismiss: (TrackEntity?) -> Unit, save: (TrackEntity) -> Unit, play: (TrackEntity) -> Unit) {
    var edited by remember(track) { mutableStateOf(track) }
    ModalBottomSheet(onDismissRequest = { dismiss(null) }) { Column(Modifier.fillMaxWidth().padding(20.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(edited.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black); Text(edited.artist, color = MaterialTheme.colorScheme.onSurfaceVariant) }; FilledIconButton(onClick = { play(edited) }) { Icon(Icons.Default.PlayArrow, null) } }
        Text("${edited.album} • ${edited.genre} • ${edited.year ?: "Anno ignoto"}")
        Text("${edited.format} • ${duration(edited.durationMs)} • ${bytes(edited.sizeBytes)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider(); Text("Valutazione", fontWeight = FontWeight.Bold)
        Row { (1..5).forEach { n -> IconButton(onClick = { edited = edited.copy(rating = n) }) { Icon(if (n <= edited.rating) Icons.Default.Star else Icons.Default.StarBorder, null, tint = FalcoLime) } } }
        var statusOpen by remember { mutableStateOf(false) }; Box { OutlinedButton(onClick = { statusOpen = true }) { Text(WorkStatus.valueOf(edited.workStatus).label) }; DropdownMenu(statusOpen, { statusOpen = false }) { WorkStatus.entries.forEach { s -> DropdownMenuItem({ Text(s.label) }, { edited = edited.copy(workStatus = s.name); statusOpen = false }) } } }
        OutlinedTextField(edited.customTags, { edited = edited.copy(customTags = it) }, label = { Text("Tag personalizzati, separati da virgola") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(edited.notes, { edited = edited.copy(notes = it) }, label = { Text("Note") }, minLines = 2, modifier = Modifier.fillMaxWidth())
        Text(edited.relativePath, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = { save(edited); dismiss(null) }, modifier = Modifier.fillMaxWidth()) { Text("SALVA MODIFICHE", fontWeight = FontWeight.Black) }
    } }
}

@Composable fun BoxScope.MiniPlayer(track: TrackEntity, playing: Boolean, position: Long, toggle: (TrackEntity) -> Unit, seek: (Long) -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 4.dp).align(Alignment.BottomCenter)) { Column { Slider(position.toFloat().coerceAtMost(track.durationMs.toFloat()), { seek(it.toLong()) }, valueRange = 0f..track.durationMs.coerceAtLeast(1).toFloat(), modifier = Modifier.fillMaxWidth().height(22.dp)); Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(track.title, fontWeight = FontWeight.Bold, maxLines = 1); Text(track.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; IconButton(onClick = { toggle(track) }) { Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null) } } } }
}
