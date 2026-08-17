package com.mockpilot.ui.selftest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mockpilot.selftest.SelfTestCheck

@Composable
fun SelfTestScreen(vm: SelfTestViewModel = hiltViewModel()) {
    val report by vm.report.collectAsStateWithLifecycle()
    val mockSelected by vm.mockSelected.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Detection Self-Test", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Reads back MockPilot's own emitted fix and shows exactly which mock flags a tracking " +
                "app would see. Without the LSPosed module the mock flags will read true — that's " +
                "expected. With the module hooking this app they should read clean.",
            style = MaterialTheme.typography.bodyMedium,
        )

        Button(onClick = vm::run, modifier = Modifier.fillMaxWidth()) { Text("Run self-test") }

        mockSelected?.let { selected ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.errorContainer,
                ),
            ) {
                Text(
                    if (selected) "✓ MockPilot is the selected mock-location app"
                    else "✗ Not selected as mock-location app — see Setup wizard",
                    Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        report?.let { r ->
            if (!r.hadFix) {
                Text("No fix available yet. Start emission on the Map, then re-run.")
            } else {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (r.anyConcern) MaterialTheme.colorScheme.errorContainer
                        else MaterialTheme.colorScheme.primaryContainer,
                    ),
                ) {
                    Text(
                        if (r.anyConcern) "Detectable: at least one mock signal is visible."
                        else "Clean: no mock signals visible in the emitted fix.",
                        Modifier.padding(12.dp),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(r.checks) { check -> CheckRow(check) }
                }
            }
        }
    }
}

@Composable
private fun CheckRow(check: SelfTestCheck) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (check.concern) Icons.Filled.Warning else Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = if (check.concern) Color(0xFFC62828) else Color(0xFF2E7D32),
            )
            Column(Modifier.padding(start = 12.dp)) {
                Row {
                    Text(check.name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "  = ${check.value}",
                        style = MaterialTheme.typography.titleSmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                Text(check.detail, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
