package com.chargegauge.ui.settings

import android.app.Application
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chargegauge.ChargeGaugeApp
import com.chargegauge.data.AppSettings
import com.chargegauge.domain.ShizukuAvailability
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val app = context.applicationContext as ChargeGaugeApp
    val settings by app.container.settings.settings.collectAsStateWithLifecycle(AppSettings())
    val shizuku by app.container.gateway.states.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val exportSessions = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch { app.container.exporter.writeSessions(uri) }
    }
    val exportSamples = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch { app.container.exporter.writeSamples(uri) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text("Shizuku", style = MaterialTheme.typography.titleLarge)
        Text(shizukuExplanation(shizuku), style = MaterialTheme.typography.bodyLarge)
        Text(
            "ChargeGauge uses Shizuku only for readings a normal app cannot get: full and design capacity, fuel-gauge current when the system current is missing, charger-side voltage and current, and per-app drain estimates. It never changes charging.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when (shizuku) {
            ShizukuAvailability.READY -> Text("Shizuku is allowed.", color = MaterialTheme.colorScheme.primary)
            ShizukuAvailability.NOT_INSTALLED -> Text("Shizuku is not installed.")
            ShizukuAvailability.STOPPED -> Button(onClick = { app.container.gateway.openShizukuApp() }) {
                Text("Open Shizuku")
            }
            ShizukuAvailability.DENIED -> Button(onClick = { app.container.gateway.requestPermission() }) {
                Text("Allow ChargeGauge")
            }
            ShizukuAvailability.ERROR -> Text("The last privileged read failed. It will be tried again.")
        }

        ToggleRow(
            title = "Record while charging",
            body = "Shows a charging notification with live watts and keeps the session going after you leave the app.",
            checked = settings.monitorWhileCharging,
            onChecked = { enabled ->
                scope.launch { app.container.settings.update { it.copy(monitorWhileCharging = enabled) } }
            },
        )
        IntervalSlider(settings.sampleIntervalSec) { seconds ->
            scope.launch { app.container.settings.update { it.copy(sampleIntervalSec = seconds) } }
        }
        OutlinedButton(
            onClick = {
                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            },
        ) { Text("Battery optimization settings") }

        Text("Alerts", style = MaterialTheme.typography.titleLarge)
        ToggleRow(
            title = "High temperature",
            body = "Warn when the battery reaches ${settings.heatAlertC.toInt()}°C while plugged in.",
            checked = settings.heatAlertEnabled,
            onChecked = { enabled ->
                scope.launch { app.container.settings.update { it.copy(heatAlertEnabled = enabled) } }
            },
        )
        ValueSlider(
            label = "${settings.heatAlertC.toInt()}°C",
            value = settings.heatAlertC.toFloat(),
            range = 35f..60f,
            steps = 24,
        ) { value ->
            scope.launch { app.container.settings.update { it.copy(heatAlertC = value.toDouble()) } }
        }
        ToggleRow(
            title = "Slow charging",
            body = "Warn when power stays under ${trim(settings.slowAlertWatts)} W for ${settings.slowAlertMinutes} minutes below ${settings.slowAlertLevelBelow}%.",
            checked = settings.slowAlertEnabled,
            onChecked = { enabled ->
                scope.launch { app.container.settings.update { it.copy(slowAlertEnabled = enabled) } }
            },
        )
        ValueSlider(
            label = "Under ${trim(settings.slowAlertWatts)} W",
            value = settings.slowAlertWatts.toFloat(),
            range = 1f..15f,
            steps = 13,
        ) { value ->
            scope.launch { app.container.settings.update { it.copy(slowAlertWatts = value.toDouble()) } }
        }
        ValueSlider(
            label = "For ${settings.slowAlertMinutes} min",
            value = settings.slowAlertMinutes.toFloat(),
            range = 1f..15f,
            steps = 13,
        ) { value ->
            scope.launch { app.container.settings.update { it.copy(slowAlertMinutes = value.toInt()) } }
        }
        ToggleRow(
            title = "Low capacity",
            body = "Warn when full capacity drops below ${settings.healthAlertPercent.toInt()}% of design capacity.",
            checked = settings.healthAlertEnabled,
            onChecked = { enabled ->
                scope.launch { app.container.settings.update { it.copy(healthAlertEnabled = enabled) } }
            },
        )
        ValueSlider(
            label = "Below ${settings.healthAlertPercent.toInt()}%",
            value = settings.healthAlertPercent.toFloat(),
            range = 50f..95f,
            steps = 44,
        ) { value ->
            scope.launch { app.container.settings.update { it.copy(healthAlertPercent = value.toDouble()) } }
        }

        Text("Export", style = MaterialTheme.typography.titleLarge)
        Text(
            "CSV files stay where you save them. Nothing is uploaded.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = { exportSessions.launch("chargegauge-sessions.csv") }, modifier = Modifier.fillMaxWidth()) {
            Text("Export sessions")
        }
        OutlinedButton(onClick = { exportSamples.launch("chargegauge-samples.csv") }, modifier = Modifier.fillMaxWidth()) {
            Text("Export samples")
        }
        Text(
            "ChargeGauge only reads the battery. It does not stop charging, change a charge limit, or calibrate anything.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun ToggleRow(
    title: String,
    body: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun IntervalSlider(seconds: Int, onCommit: (Int) -> Unit) {
    var value by remember(seconds) { mutableFloatStateOf(seconds.toFloat()) }
    Column {
        Text("Sample every ${value.toInt()} s", style = MaterialTheme.typography.titleMedium)
        Slider(
            value = value,
            onValueChange = { value = it },
            onValueChangeFinished = { onCommit(value.toInt()) },
            valueRange = 1f..10f,
            steps = 8,
        )
    }
}

@Composable
private fun ValueSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onCommit: (Float) -> Unit,
) {
    var local by remember(value) { mutableFloatStateOf(value) }
    Column {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Slider(
            value = local,
            onValueChange = { local = it },
            onValueChangeFinished = { onCommit(local) },
            valueRange = range,
            steps = steps,
        )
    }
}

private fun shizukuExplanation(state: ShizukuAvailability): String = when (state) {
    ShizukuAvailability.NOT_INSTALLED -> "Install the Shizuku app and start it with wireless debugging. ChargeGauge works without it, with less detail."
    ShizukuAvailability.STOPPED -> "Shizuku is installed but not running. Start it, then allow ChargeGauge."
    ShizukuAvailability.DENIED -> "Shizuku is running. Allow ChargeGauge so it can read the fuel gauge."
    ShizukuAvailability.READY -> "Privileged reads are available. Public battery numbers are still used whenever Android already provides them."
    ShizukuAvailability.ERROR -> "Shizuku is allowed, but a read failed."
}

private fun trim(value: Double): String {
    return if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(value)
}
