package com.chargegauge.ui.now

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chargegauge.domain.BatterySnapshot
import com.chargegauge.domain.ChargeStatus
import com.chargegauge.domain.Readout
import com.chargegauge.ui.components.Fact
import com.chargegauge.ui.components.MeterCard

@Composable
fun NowScreen(
    snapshot: BatterySnapshot,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (snapshot.timestampMillis == 0L) {
            Text("Reading the battery…", style = MaterialTheme.typography.titleMedium)
            return@Column
        }
        Text(
            "${Readout.statusLabel(snapshot.status)} · ${Readout.plugLabel(snapshot.plugged)}",
            style = MaterialTheme.typography.titleMedium,
        )
        PowerHero(snapshot)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MeterCard(
                label = "Voltage",
                value = Readout.volts(snapshot.batteryVoltageMv),
                source = snapshot.batteryVoltageSource,
                modifier = Modifier.weight(1f),
            )
            MeterCard(
                label = "Current",
                value = Readout.amps(snapshot.batteryCurrentMa),
                source = snapshot.batteryCurrentSource,
                modifier = Modifier.weight(1f),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(Readout.percent(snapshot.levelPercent), style = MaterialTheme.typography.titleLarge)
            LinearProgressIndicator(
                progress = { (snapshot.levelPercent ?: 0) / 100f },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Fact("Temperature", Readout.temperature(snapshot.temperatureC), Modifier.weight(1f))
                    Fact("Time to full", Readout.duration(snapshot.timeToFullMillis), Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Fact("Condition", Readout.conditionLabel(snapshot.condition), Modifier.weight(1f))
                    Fact("Chemistry", snapshot.technology ?: "—", Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Fact(
                        "Cycles",
                        snapshot.cycleCount?.toString() ?: "—",
                        Modifier.weight(1f),
                    )
                    Fact("Remaining", Readout.mah(snapshot.remainingMah), Modifier.weight(1f))
                }
                if (snapshot.cycleCount != null) {
                    Text(
                        "Cycles from ${Readout.sourceLabel(snapshot.cycleSource).lowercase()}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        ChargerCard(snapshot)
        if (snapshot.batteryCurrentMa == null) {
            Text(
                "This phone is not reporting current through the normal battery API.",
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = onOpenSettings) { Text("Set up Shizuku for the fuel gauge") }
        }
        Text(
            "Watts are battery-side power, volts times amps. They sit below the number printed on a charger.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PowerHero(snapshot: BatterySnapshot) {
    val caption = when {
        snapshot.batteryPowerW == null -> "Power is not available yet"
        snapshot.batteryPowerW >= 0.0 && snapshot.status != ChargeStatus.DISCHARGING -> "Into the battery"
        else -> "Out of the battery"
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                Readout.watts(snapshot.batteryPowerW),
                style = MaterialTheme.typography.displayLarge,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                "W",
                modifier = Modifier.padding(top = 18.dp),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(caption, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ChargerCard(snapshot: BatterySnapshot) {
    val hasLive = snapshot.inputPowerW != null || snapshot.inputVoltageMv != null
    val hasLimit = snapshot.chargerLimitWatts != null
    if (!hasLive && !hasLimit) return
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Charger side", style = MaterialTheme.typography.titleMedium)
            if (hasLive) {
                Text(
                    snapshot.inputName?.let { "Port $it" } ?: "Port reading",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "${Readout.watts(snapshot.inputPowerW)} W · ${Readout.volts(snapshot.inputVoltageMv)} · ${Readout.amps(snapshot.inputCurrentMa)}",
                    fontFamily = FontFamily.Monospace,
                )
            }
            if (hasLimit) {
                Text(
                    "Negotiated limit ${Readout.watts(snapshot.chargerLimitWatts)} W. This is the cap the phone agreed to, not live power.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
