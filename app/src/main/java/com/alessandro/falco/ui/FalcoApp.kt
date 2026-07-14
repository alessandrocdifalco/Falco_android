package com.alessandro.falco.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alessandro.falco.ui.screens.*

private enum class Destination(val label: String, val icon: ImageVector) { Dashboard("Dashboard", Icons.Default.Dashboard), Review("Revisione", Icons.Default.LibraryMusic), WebDav("WebDAV", Icons.Default.Folder), More("Altro", Icons.Default.Menu) }

@Composable fun FalcoApp(vm: FalcoViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var destination by rememberSaveable { mutableStateOf(Destination.Dashboard) }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { it?.let(vm::addFolder) }
    Scaffold(
        bottomBar = {
            NavigationBar(tonalElevation = 0.dp) { Destination.entries.forEach { d -> NavigationBarItem(selected = destination == d, onClick = { destination = d }, icon = { Icon(d.icon, null) }, label = { Text(d.label) }) } }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (destination) {
                Destination.Dashboard -> DashboardScreen(state, { folderPicker.launch(null) }, vm::scan)
                Destination.Review -> ReviewScreen(state, vm::preview, vm::play, vm::seek, vm::skip, vm::review, vm::undoReview, vm::loadWaveform)
                Destination.WebDav -> WebDavScreen(state, vm::saveWebDav, vm::testWebDav, vm::browseWebDav, vm::scanWebDav, vm::playWebDav)
                Destination.More -> SettingsScreen(state, vm.folders(), { folderPicker.launch(null) }, vm::scan, vm::removeFolder, vm::downloadMaest, vm::removeMaest)
            }
            state.selected?.let { DetailSheet(it, vm::select, vm::save, vm::play) }
            state.playing?.let { MiniPlayer(it, state.isPlaying, state.position, vm::play, vm::seek) }
        }
    }
}
