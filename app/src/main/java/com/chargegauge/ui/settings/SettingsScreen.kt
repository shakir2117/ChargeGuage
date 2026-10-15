package com.chargegauge.ui.settings

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
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
import com.chargegauge.data.allowedPowerScale
import com.chargegauge.domain.ShizukuAvailability
import com.chargegauge.ui.theme.MATERIAL_YOU
import com.chargegauge.ui.theme.gaugePalettes
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val app = context.applicationContext as ChargeGaugeApp
    val settings by app.container.settings.settings.collectAsStateWithLifecycle(AppSettings())
    val shizuku by app.container.gateway.states.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var showInfo by remember { mutableStateOf(false) }
    val exportSessions = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch { app.container.exporter.writeSessions(uri) }
    }
    val exportSamples = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch { app.container.exporter.writeSamples(uri) }
    }
    val save: (AppSettings) -> Unit = { next ->
        scope.launch { app.container.settings.update { next } }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { showInfo = true }) {
                Icon(Icons.Outlined.Info, contentDescription = null)
                Text("Info", modifier = Modifier.padding(start = 6.dp))
            }
        }

        SettingsGroup(title = "Theme") {
            SettingSwitch(
                title = "AMOLED",
                checked = settings.amoled,
                onChecked = { save(settings.copy(amoled = it)) },
            )
            ColorPicker(settings, save)
        }

        SettingsGroup(title = "Shizuku") {
            Text(
                shizukuStatus(shizuku),
                color = if (shizuku == ShizukuAvailability.READY) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            when (shizuku) {
                ShizukuAvailability.STOPPED -> Button(onClick = { app.container.gateway.openShizukuApp() }) {
                    Text("Open Shizuku")
                }
                ShizukuAvailability.DENIED -> Button(onClick = { app.container.gateway.requestPermission() }) {
                    Text("Allow ChargeGauge")
                }
                else -> Unit
            }
        }

        SettingsGroup(title = "Monitoring") {
            SettingSwitch(
                title = "Record while charging",
                checked = settings.monitorWhileCharging,
                onChecked = { save(settings.copy(monitorWhileCharging = it)) },
            )
            IntervalSlider(settings.sampleIntervalSec) { seconds ->
                save(settings.copy(sampleIntervalSec = seconds))
            }
            SettingSwitch(
                title = "Power scale",
                checked = settings.powerScaleEnabled,
                onChecked = { save(settings.copy(powerScaleEnabled = it)) },
            )
            if (settings.powerScaleEnabled) {
                PowerScaleChips(settings, save)
            }
            OutlinedButton(
                onClick = {
                    context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Battery optimization") }
        }

        SettingsGroup(title = "Alerts") {
            SettingSwitch(
                title = "High temperature",
                checked = settings.heatAlertEnabled,
                onChecked = { save(settings.copy(heatAlertEnabled = it)) },
            )
            if (settings.heatAlertEnabled) {
                ValueSlider(
                    label = "${settings.heatAlertC.toInt()}°C",
                    value = settings.heatAlertC.toFloat(),
                    range = 35f..60f,
                    steps = 24,
                ) { save(settings.copy(heatAlertC = it.toDouble())) }
            }
            SettingSwitch(
                title = "Slow charging",
                checked = settings.slowAlertEnabled,
                onChecked = { save(settings.copy(slowAlertEnabled = it)) },
            )
            if (settings.slowAlertEnabled) {
                ValueSlider(
                    label = "Under ${trim(settings.slowAlertWatts)} W",
                    value = settings.slowAlertWatts.toFloat(),
                    range = 1f..15f,
                    steps = 13,
                ) { save(settings.copy(slowAlertWatts = it.toDouble())) }
                ValueSlider(
                    label = "For ${settings.slowAlertMinutes} min",
                    value = settings.slowAlertMinutes.toFloat(),
                    range = 1f..15f,
                    steps = 13,
                ) { save(settings.copy(slowAlertMinutes = it.toInt())) }
            }
            SettingSwitch(
                title = "Low capacity",
                checked = settings.healthAlertEnabled,
                onChecked = { save(settings.copy(healthAlertEnabled = it)) },
            )
            if (settings.healthAlertEnabled) {
                ValueSlider(
                    label = "Below ${settings.healthAlertPercent.toInt()}%",
                    value = settings.healthAlertPercent.toFloat(),
                    range = 50f..95f,
                    steps = 44,
                ) { save(settings.copy(healthAlertPercent = it.toDouble())) }
            }
        }

        SettingsGroup(title = "Export") {
            Button(
                onClick = { exportSessions.launch("chargegauge-sessions.csv") },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Export sessions") }
            OutlinedButton(
                onClick = { exportSamples.launch("chargegauge-samples.csv") },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Export samples") }
        }
    }

    if (showInfo) {
        SettingsInfoDialog(onDismiss = { showInfo = false })
    }
}

@Composable
private fun SettingsGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorPicker(
    settings: AppSettings,
    save: (AppSettings) -> Unit,
) {
    val materialYouOk = Build.VERSION.SDK_INT >= 31
    val selectedPalette = when {
        settings.themeId == MATERIAL_YOU && materialYouOk -> null
        settings.themeId == MATERIAL_YOU -> gaugePalettes.first().id
        else -> settings.themeId
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = materialYouOk && settings.themeId == MATERIAL_YOU,
            enabled = materialYouOk,
            onClick = { save(settings.copy(themeId = MATERIAL_YOU)) },
            label = { Text("Material You") },
        )
        gaugePalettes.forEach { palette ->
            FilterChip(
                selected = selectedPalette == palette.id,
                onClick = { save(settings.copy(themeId = palette.id)) },
                label = { Text(palette.label) },
                leadingIcon = {
                    Box(
                        Modifier
                            .size(12.dp)
                            .background(palette.swatch, CircleShape),
                    )
                },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PowerScaleChips(
    settings: AppSettings,
    save: (AppSettings) -> Unit,
) {
    val selected = allowedPowerScale(settings.powerScale)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(0.001 to "0.001×", 1.0 to "1×", 1000.0 to "1000×").forEach { (scale, label) ->
            FilterChip(
                selected = selected == scale,
                onClick = { save(settings.copy(powerScale = scale)) },
                label = { Text(label) },
            )
        }
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

@Composable
private fun SettingsInfoDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text("Settings") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                InfoBlock(
                    "Theme",
                    "Material You follows the phone wallpaper. It needs Android 12. On older phones, pick Amber, Teal, Blue, Green, or Violet. AMOLED uses a pure black background in dark mode. Cards stay one step above black so they still separate from the background.",
                )
                InfoBlock(
                    "Shizuku",
                    "Optional. ChargeGauge uses it only for numbers Android hides from normal apps: full and design capacity, fuel-gauge current when the system current is missing, charger-side voltage and current, and per-app drain estimates. Install Shizuku, start it with wireless debugging, then allow ChargeGauge. The app never changes charging. It still works without Shizuku, with less detail.",
                )
                InfoBlock(
                    "Monitoring",
                    "Record while charging shows a notification and keeps the session after you leave. On this screen, samples follow the interval you set. In the background, ChargeGauge reads about every 30 seconds and lets the phone sleep between readings. The fuel gauge is read at most once a minute in the background.",
                )
                InfoBlock(
                    "Power scale",
                    "Off uses Android’s current as reported. Turn it on if watts stay near zero, or look about a thousand times too high. 1000× means the phone stored milliamps. 0.001× means the number is a thousand times too large. 1× leaves Android’s number unchanged. Fuel-gauge current is not scaled.",
                )
                InfoBlock(
                    "Battery optimization",
                    "Opens the system list where you can leave ChargeGauge unrestricted, so the charging notification is less likely to be stopped.",
                )
                InfoBlock(
                    "High temperature",
                    "Warns when the battery reaches the chosen temperature while plugged in. It will not repeat more than once every 30 minutes.",
                )
                InfoBlock(
                    "Slow charging",
                    "Warns when power stays under the chosen watts for the chosen minutes, while the level is still under 90% and the battery is actually charging. It stays quiet when charging has stopped at a limit.",
                )
                InfoBlock(
                    "Low capacity",
                    "Warns when full capacity, from the fuel gauge, drops below the chosen percent of design capacity. This needs Shizuku on most phones.",
                )
                InfoBlock(
                    "Export",
                    "Sessions and samples are written to a CSV file you choose. Nothing is uploaded. ChargeGauge only reads the battery. It does not stop charging, change a charge limit, or calibrate the battery.",
                )
                InfoBlock(
                    "Watts",
                    "Watts are battery-side power, volts times amps. They sit below the number printed on a charger. Gauge volts are paired with gauge amps when Shizuku has both. Otherwise the Android pair is used.",
                )
            }
        },
    )
}

@Composable
private fun InfoBlock(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(body, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun shizukuStatus(state: ShizukuAvailability): String = when (state) {
    ShizukuAvailability.NOT_INSTALLED -> "Not installed"
    ShizukuAvailability.STOPPED -> "Installed, not running"
    ShizukuAvailability.DENIED -> "Needs permission"
    ShizukuAvailability.READY -> "Allowed"
    ShizukuAvailability.ERROR -> "Read failed"
}

private fun trim(value: Double): String {
    return if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(value)
}
