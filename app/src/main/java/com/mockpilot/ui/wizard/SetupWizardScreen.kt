package com.mockpilot.ui.wizard

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay

@Composable
fun SetupWizardScreen(
    onDone: () -> Unit,
    vm: WizardViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val mockSelected by vm.mockSelected.collectAsStateWithLifecycle()
    val exactAlarms by vm.exactAlarms.collectAsStateWithLifecycle()

    // Live-poll the permission state so the checkmark flips the moment the user returns.
    LaunchedEffect(Unit) {
        while (true) {
            vm.refresh()
            delay(1500)
        }
    }

    fun open(action: String, uri: Uri? = null) {
        runCatching {
            val intent = Intent(action).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (uri != null) data = uri
            }
            context.startActivity(intent)
        }.onFailure {
            runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Setup Wizard", style = MaterialTheme.typography.headlineMedium)

        // Live status banner.
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (mockSelected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.errorContainer,
            ),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    if (mockSelected) "✓ Mock location is permitted for MockPilot"
                    else "✗ MockPilot is not yet the mock-location app",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    "This updates live — leave and come back after selecting the app.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Step(
            number = 1,
            title = "Enable Developer Options",
            body = "Open Settings → About phone and tap “Build number” seven times until you see " +
                "“You are now a developer”.",
        ) {
            OutlinedButton(onClick = { open(Settings.ACTION_DEVICE_INFO_SETTINGS) }) {
                Text("Open About phone")
            }
        }

        Step(
            number = 2,
            title = "Select MockPilot as the mock location app",
            body = "In Developer Options, find “Select mock location app” and choose MockPilot.",
        ) {
            OutlinedButton(onClick = { open(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS) }) {
                Text("Open Developer Options")
            }
        }

        Step(
            number = 3,
            title = "Allow notifications",
            body = "The foreground service shows an ongoing notification while emitting (required " +
                "by Android). Allow it so the service stays alive.",
        ) {
            OutlinedButton(onClick = {
                open(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null),
                )
            }) { Text("Open app settings") }
        }

        Step(
            number = 4,
            title = "Ignore battery optimizations",
            body = "So the engine keeps ticking under Doze, exempt MockPilot from battery " +
                "optimization.",
        ) {
            OutlinedButton(onClick = { open(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS) }) {
                Text("Battery settings")
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Step(
                number = 5,
                title = "Allow exact alarms (for the Scheduler)",
                body = if (exactAlarms) "Already granted — schedule transitions will be punctual."
                else "Grant “Alarms & reminders” so scheduled transitions fire on time.",
            ) {
                OutlinedButton(onClick = { open(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM) }) {
                    Text("Exact alarm settings")
                }
            }
        }

        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Done") }
    }
}

@Composable
private fun Step(
    number: Int,
    title: String,
    body: String,
    action: @Composable () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("$number.", style = MaterialTheme.typography.titleMedium)
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            Text(body, style = MaterialTheme.typography.bodyMedium)
            action()
        }
    }
}
