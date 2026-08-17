package com.mockpilot.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mockpilot.model.EngineState
import com.mockpilot.model.LocationSample
import kotlinx.coroutines.delay
import java.util.Locale

/** Live status card: current phase, coordinates, kinematics, and uptime. */
@Composable
fun StatusCard(
    state: EngineState,
    sample: LocationSample?,
    startedAt: Long?,
    modifier: Modifier = Modifier,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startedAt) {
        while (startedAt != null) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    val phase = when (state) {
        is EngineState.Running -> state.subMode
        is EngineState.Traveling -> "Traveling · ${state.remainingMeters.toInt()} m left"
        is EngineState.GpsLost -> "GPS lost · ${state.remainingSeconds}s"
        is EngineState.Error -> "Error: ${state.message}"
        EngineState.Stopped -> "Stopped"
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(phase, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (sample != null) {
                Text(
                    String.format(Locale.US, "%.6f, %.6f", sample.point.latitude, sample.point.longitude),
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Metric("±acc", String.format(Locale.US, "%.1f m", sample.accuracyM))
                    Metric("speed", String.format(Locale.US, "%.1f m/s", sample.speedMps))
                    Metric("bearing", String.format(Locale.US, "%.0f°", sample.bearingDeg))
                    Metric("sats", sample.satelliteCount.toString())
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Metric("alt", String.format(Locale.US, "%.0f m", sample.altitudeM))
                    startedAt?.let { Metric("uptime", formatUptime(now - it)) }
                }
            } else {
                Text("No fix yet", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

fun formatUptime(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%02d:%02d", m, s)
}
