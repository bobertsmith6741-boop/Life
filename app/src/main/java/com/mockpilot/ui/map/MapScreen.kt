package com.mockpilot.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mockpilot.model.GeoPoint
import com.mockpilot.model.TeleportPolicy

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    onOpenSetup: () -> Unit,
    vm: MapViewModel = hiltViewModel(),
) {
    val engineState by vm.engineState.collectAsStateWithLifecycle()
    val sample by vm.lastSample.collectAsStateWithLifecycle()
    val startedAt by vm.startedAt.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val selectedLabel by vm.selectedLabel.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val searching by vm.searching.collectAsStateWithLifecycle()
    val teleportPolicy by vm.teleportPolicy.collectAsStateWithLifecycle()

    var snackbar by remember { mutableStateOf<String?>(null) }
    var showSave by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            snackbar = when (event) {
                is com.mockpilot.model.EngineEvent.TeleportRefused ->
                    "Teleport refused (${event.distanceM.toInt()} m jump)"
                is com.mockpilot.model.EngineEvent.TravelStarted ->
                    "Traveling ${event.distanceM.toInt()} m (~${event.etaSeconds}s)"
                is com.mockpilot.model.EngineEvent.GpsLostStarted ->
                    "GPS lost for ${event.seconds}s, then resume"
                is com.mockpilot.model.EngineEvent.Info -> event.message
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        // Search bar + setup shortcut.
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = vm::onQueryChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("Search address or place") },
                trailingIcon = {
                    IconButton(onClick = vm::search) {
                        Icon(Icons.Filled.Search, contentDescription = "Search")
                    }
                },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { vm.search() }),
            )
            IconButton(onClick = onOpenSetup) {
                Icon(Icons.Filled.Settings, contentDescription = "Setup")
            }
        }

        Box(Modifier.weight(1f)) {
            OsmMap(
                modifier = Modifier.fillMaxSize(),
                center = selected ?: DEFAULT_CENTER,
                pins = selected?.let { listOf(MapPin(it, selectedLabel)) } ?: emptyList(),
                followPoint = sample?.point,
                onLongPress = { vm.dropPin(it) },
            )

            if (results.isNotEmpty() || searching) {
                Card(
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(12.dp)
                        .height(220.dp),
                ) {
                    if (searching) {
                        Text("Searching…", Modifier.padding(16.dp))
                    } else {
                        LazyColumn {
                            items(results) { r ->
                                TextButton(
                                    onClick = { vm.pick(r) },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(r.label, maxLines = 2)
                                }
                            }
                        }
                    }
                }
            }

            snackbar?.let { msg ->
                LaunchedEffect(msg) { kotlinx.coroutines.delay(2500); snackbar = null }
                Card(Modifier.align(Alignment.BottomCenter).padding(12.dp)) {
                    Text(msg, Modifier.padding(12.dp))
                }
            }
        }

        // Bottom control panel.
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            StatusRow(teleportPolicy, vm::setTeleportPolicy)

            com.mockpilot.ui.common.StatusCard(engineState, sample, startedAt)

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { if (vm.isRunning) vm.stop() else vm.start() },
                    modifier = Modifier.weight(1f).height(52.dp),
                    enabled = selected != null || vm.isRunning,
                    colors = if (vm.isRunning) {
                        ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    } else {
                        ButtonDefaults.buttonColors()
                    },
                ) {
                    Text(if (vm.isRunning) "STOP" else "START", style = MaterialTheme.typography.titleMedium)
                }
                OutlinedButton(
                    onClick = { showSave = true },
                    enabled = selected != null,
                    modifier = Modifier.height(52.dp),
                ) { Text("Save place") }
            }
        }
    }

    if (showSave) {
        var name by remember { mutableStateOf(selectedLabel) }
        AlertDialog(
            onDismissRequest = { showSave = false },
            title = { Text("Save place") },
            text = {
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Name") })
            },
            confirmButton = {
                TextButton(onClick = { vm.savePlace(name); showSave = false }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showSave = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun StatusRow(policy: TeleportPolicy, onPolicy: (TeleportPolicy) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Teleport guard", style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TeleportChip("Travel", TeleportPolicy.TRAVEL, policy, onPolicy)
            TeleportChip("GPS lost", TeleportPolicy.GPS_LOST, policy, onPolicy)
            TeleportChip("Refuse", TeleportPolicy.REFUSE, policy, onPolicy)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TeleportChip(
    label: String,
    value: TeleportPolicy,
    selected: TeleportPolicy,
    onSelect: (TeleportPolicy) -> Unit,
) {
    FilterChip(
        selected = value == selected,
        onClick = { onSelect(value) },
        label = { Text(label) },
    )
}

private val DEFAULT_CENTER = GeoPoint(40.7580, -73.9855) // Times Square, as a neutral default
