package com.mockpilot.ui.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mockpilot.data.SavedPlace
import com.mockpilot.data.ScheduleEntity
import java.util.Locale

private val DAY_LABELS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

@Composable
fun ScheduleScreen(vm: ScheduleViewModel = hiltViewModel()) {
    val schedules by vm.schedules.collectAsStateWithLifecycle()
    val places by vm.places.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text("Scheduler", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Chain places into a believable daily pattern. Transitions travel between spots " +
                    "instead of teleporting.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            if (!vm.canScheduleExactAlarms()) {
                Card(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Text(
                        "Exact alarms are not permitted — schedules will use inexact timing. " +
                            "Grant “Alarms & reminders” in system settings for punctual transitions.",
                        Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            if (schedules.isEmpty()) {
                Text("No schedules yet. Tap + to add one.", style = MaterialTheme.typography.bodyMedium)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(schedules, key = { it.id }) { s ->
                        ScheduleRow(s, onToggle = { vm.toggle(s) }, onDelete = { vm.delete(s) })
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showAdd = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
        ) { Icon(Icons.Filled.Add, contentDescription = "Add schedule") }
    }

    if (showAdd) {
        AddScheduleDialog(
            places = places,
            onDismiss = { showAdd = false },
            onAdd = { label, place, startMin, endMin, mask ->
                vm.add(label, place, startMin, endMin, mask)
                showAdd = false
            },
        )
    }
}

@Composable
private fun ScheduleRow(s: ScheduleEntity, onToggle: () -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(s.label, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${fmt(s.startMinuteOfDay)}–${fmt(s.endMinuteOfDay)} · ${daysText(s.daysMask)}",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(s.placeName, style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = s.enabled, onCheckedChange = { onToggle() })
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Delete") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun AddScheduleDialog(
    places: List<SavedPlace>,
    onDismiss: () -> Unit,
    onAdd: (String, SavedPlace, Int, Int, Int) -> Unit,
) {
    var label by remember { mutableStateOf("") }
    var selectedPlace by remember { mutableStateOf(places.firstOrNull()) }
    var placeExpanded by remember { mutableStateOf(false) }
    var daysMask by remember { mutableIntStateOf(0b0011111) } // Mon–Fri by default
    val startState = rememberTimePickerState(initialHour = 9, initialMinute = 0, is24Hour = true)
    val endState = rememberTimePickerState(initialHour = 17, initialMinute = 0, is24Hour = true)
    var editingEnd by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card {
            Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("New schedule", style = MaterialTheme.typography.titleLarge)

                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label (e.g. Office)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (places.isEmpty()) {
                    Text(
                        "Save a place first (Map → Save place), then it will appear here.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    ExposedDropdownMenuBox(
                        expanded = placeExpanded,
                        onExpandedChange = { placeExpanded = it },
                    ) {
                        OutlinedTextField(
                            value = selectedPlace?.name ?: "Pick a place",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Place") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = placeExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                        )
                        ExposedDropdownMenu(expanded = placeExpanded, onDismissRequest = { placeExpanded = false }) {
                            places.forEach { p ->
                                DropdownMenuItem(
                                    text = { Text(p.name) },
                                    onClick = { selectedPlace = p; placeExpanded = false },
                                )
                            }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !editingEnd,
                        onClick = { editingEnd = false },
                        label = { Text("Start ${fmt(startState.hour * 60 + startState.minute)}") },
                    )
                    FilterChip(
                        selected = editingEnd,
                        onClick = { editingEnd = true },
                        label = { Text("End ${fmt(endState.hour * 60 + endState.minute)}") },
                    )
                }
                TimePicker(state = if (editingEnd) endState else startState)

                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DAY_LABELS.forEachIndexed { i, day ->
                        val bit = 1 shl i
                        FilterChip(
                            selected = daysMask and bit != 0,
                            onClick = { daysMask = daysMask xor bit },
                            label = { Text(day) },
                        )
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    TextButton(
                        onClick = {
                            val place = selectedPlace ?: return@TextButton
                            onAdd(
                                label,
                                place,
                                startState.hour * 60 + startState.minute,
                                endState.hour * 60 + endState.minute,
                                daysMask,
                            )
                        },
                        enabled = selectedPlace != null && daysMask != 0,
                    ) { Text("Add") }
                }
            }
        }
    }
}

private fun fmt(minuteOfDay: Int): String =
    String.format(Locale.US, "%02d:%02d", minuteOfDay / 60, minuteOfDay % 60)

private fun daysText(mask: Int): String =
    DAY_LABELS.filterIndexed { i, _ -> mask and (1 shl i) != 0 }.joinToString(",")
