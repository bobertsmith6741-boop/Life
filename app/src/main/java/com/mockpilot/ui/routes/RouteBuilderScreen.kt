package com.mockpilot.ui.routes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mockpilot.ui.map.MapPin
import com.mockpilot.ui.map.OsmMap
import java.util.Locale

@Composable
fun RouteBuilderScreen(vm: RouteViewModel = hiltViewModel()) {
    val waypoints by vm.waypoints.collectAsStateWithLifecycle()
    val speed by vm.targetSpeed.collectAsStateWithLifecycle()
    val loop by vm.loop.collectAsStateWithLifecycle()
    val engineState by vm.engineState.collectAsStateWithLifecycle()
    val sample by vm.lastSample.collectAsStateWithLifecycle()
    val startedAt by vm.startedAt.collectAsStateWithLifecycle()
    val savedRoutes by vm.savedRoutes.collectAsStateWithLifecycle()

    var showSave by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            OsmMap(
                modifier = Modifier.fillMaxSize(),
                center = waypoints.firstOrNull(),
                pins = waypoints.mapIndexed { i, p -> MapPin(p, "WP ${i + 1}", numbered = i + 1) },
                path = waypoints,
                followPoint = sample?.point,
                onTap = { vm.addWaypoint(it) },
            )
            Card(Modifier.align(Alignment.TopCenter).padding(8.dp)) {
                Text(
                    "Tap the map to add waypoints (${waypoints.size})",
                    Modifier.padding(10.dp),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }

        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Target speed: ${String.format(Locale.US, "%.0f", speed)} m/s " +
                        "(${String.format(Locale.US, "%.0f", speed * 3.6)} km/h)",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Slider(
                value = speed.toFloat(),
                onValueChange = { vm.setSpeed(it.toDouble()) },
                valueRange = 1f..33f, // ~walking to ~120 km/h
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Loop route", Modifier.weight(1f))
                Switch(checked = loop, onCheckedChange = { vm.setLoop(it) })
            }

            com.mockpilot.ui.common.StatusCard(engineState, sample, startedAt)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { if (vm.isRunning) vm.stop() else vm.preview() },
                    enabled = waypoints.size >= 2 || vm.isRunning,
                    modifier = Modifier.weight(1f),
                ) { Text(if (vm.isRunning) "STOP" else "Preview / Run") }
                OutlinedButton(onClick = { vm.removeLast() }, enabled = waypoints.isNotEmpty()) { Text("Undo") }
                OutlinedButton(onClick = { vm.clear() }, enabled = waypoints.isNotEmpty()) { Text("Clear") }
                OutlinedButton(onClick = { showSave = true }, enabled = waypoints.size >= 2) { Text("Save") }
            }

            if (savedRoutes.isNotEmpty()) {
                Text("Saved routes", style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(savedRoutes, key = { it.id }) { r ->
                        AssistChip(
                            onClick = { vm.load(r) },
                            label = { Text("${r.name} (${r.waypoints.size})") },
                        )
                    }
                }
            }
        }
    }

    if (showSave) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showSave = false },
            title = { Text("Save route") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }) },
            confirmButton = { TextButton(onClick = { vm.save(name); showSave = false }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { showSave = false }) { Text("Cancel") } },
        )
    }
}
