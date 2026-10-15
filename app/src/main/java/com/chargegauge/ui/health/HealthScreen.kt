package com.chargegauge.ui.health

import android.app.Application
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chargegauge.ChargeGaugeApp
import com.chargegauge.data.db.CapacitySnapshotEntity
import com.chargegauge.domain.BatterySnapshot
import com.chargegauge.domain.BatterystatsParser
import com.chargegauge.domain.Readout
import com.chargegauge.domain.ShizukuAvailability
import com.chargegauge.ui.components.Fact
import com.chargegauge.ui.components.SeriesChart
import com.chargegauge.ui.sessions.AndroidViewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun HealthScreen(
    snapshot: BatterySnapshot,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val viewModel: HealthViewModel = viewModel(
        factory = AndroidViewModelFactory(context.applicationContext as Application),
    )
    val history by viewModel.history.collectAsStateWithLifecycle()
    val drain by viewModel.drain.collectAsStateWithLifecycle()
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Condition", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(Readout.conditionLabel(snapshot.condition), style = MaterialTheme.typography.headlineMedium)
        Text(
            "Android's own health flag. It usually stays on Good until something is already wrong.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            Readout.health(snapshot.healthPercent),
            style = MaterialTheme.typography.displaySmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text("Full capacity compared with design capacity")
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Fact("Design", Readout.mah(snapshot.designCapacityMah), Modifier.weight(1f))
            Fact("Full now", Readout.mah(snapshot.fullCapacityMah), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Fact("Cycles", snapshot.cycleCount?.toString() ?: "—", Modifier.weight(1f))
            Fact(
                "Gauge index",
                Readout.health(snapshot.fuelGaugeHealthIndex),
                Modifier.weight(1f),
            )
        }
        if (snapshot.healthPercent == null) {
            Text(
                "Design capacity is hidden from normal apps. Shizuku can read it when the kernel publishes charge_full and charge_full_design.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onOpenSettings) { Text("Shizuku access") }
        }
        Text("Capacity over time", style = MaterialTheme.typography.titleMedium)
        if (history.size < 2) {
            Text(
                "ChargeGauge stores a capacity point about once a day after Shizuku has read it. One point is not a trend yet.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            SeriesChart(history.mapNotNull { point -> point.healthPercent?.let { point.timestamp to it } })
            Text(
                "${Readout.health(history.first().healthPercent)} → ${Readout.health(history.last().healthPercent)}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Text("App drain since last charge", style = MaterialTheme.typography.titleMedium)
        Text(
            "These are Android's estimates, not measured watts. They stay on this phone.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when (val state = drain) {
            DrainUi.Idle -> Button(onClick = viewModel::loadDrain) { Text("Load estimates") }
            DrainUi.Loading -> CircularProgressIndicator()
            is DrainUi.NeedsPrivilege -> {
                Text(privilegeMessage(state.availability))
                Button(onClick = onOpenSettings) { Text("Open settings") }
            }
            DrainUi.Failed -> {
                Text("The battery stats dump could not be read.")
                Button(onClick = viewModel::loadDrain) { Text("Try again") }
            }
            is DrainUi.Ready -> {
                state.computedMah?.let { Text("Computed drain ${Readout.mah(it)}") }
                if (state.rows.isEmpty()) {
                    Text("No per-app rows were found in the dump.")
                } else {
                    val max = state.rows.maxOf { it.mah }.coerceAtLeast(0.1)
                    state.rows.forEach { row ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(row.label, modifier = Modifier.weight(1f))
                                Text(Readout.mah(row.mah), fontFamily = FontFamily.Monospace)
                            }
                            LinearProgressIndicator(
                                progress = { (row.mah / max).toFloat().coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
                Button(onClick = viewModel::loadDrain) { Text("Refresh") }
            }
        }
    }
}

private fun privilegeMessage(availability: ShizukuAvailability): String = when (availability) {
    ShizukuAvailability.NOT_INSTALLED -> "Install Shizuku, then start it. Per-app drain needs a shell the normal app does not have."
    ShizukuAvailability.STOPPED -> "Start Shizuku, then come back."
    ShizukuAvailability.DENIED -> "Allow ChargeGauge in Shizuku."
    else -> "Shizuku is not ready."
}

class HealthViewModel(app: Application) : AndroidViewModel(app) {
    private val container = (app as ChargeGaugeApp).container
    private val packageManager: PackageManager = app.packageManager
    val history: StateFlow<List<CapacitySnapshotEntity>> =
        container.database.chargeDao().observeCapacity()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val drainState = kotlinx.coroutines.flow.MutableStateFlow<DrainUi>(DrainUi.Idle)
    val drain: StateFlow<DrainUi> = drainState

    fun loadDrain() {
        viewModelScope.launch {
            drainState.value = DrainUi.Loading
            val availability = container.gateway.availability()
            if (availability != ShizukuAvailability.READY) {
                drainState.value = DrainUi.NeedsPrivilege(availability)
                return@launch
            }
            val dump = withContext(Dispatchers.IO) { container.gateway.readBatterystats() }
            if (dump == null) {
                drainState.value = DrainUi.Failed
                return@launch
            }
            val report = BatterystatsParser.parse(dump)
            val rows = report.entries.take(15).map { entry ->
                DrainRow(labelFor(entry.uid, entry.label), entry.mah)
            }
            drainState.value = DrainUi.Ready(report.computedDrainMah, rows)
        }
    }

    private fun labelFor(uid: Int?, fallback: String): String {
        if (uid == null) return fallback
        if (uid == 1000) return "Android system"
        val packages = packageManager.getPackagesForUid(uid).orEmpty()
        if (packages.isEmpty()) return fallback
        return packages.map { packageName ->
            try {
                val info = packageManager.getApplicationInfo(packageName, 0)
                packageManager.getApplicationLabel(info).toString()
            } catch (_: PackageManager.NameNotFoundException) {
                packageName
            }
        }.distinct().joinToString(", ")
    }

    companion object {
        fun factory(app: Application): ViewModelProvider.Factory = AndroidViewModelFactory(app)
    }
}

data class DrainRow(val label: String, val mah: Double)

sealed interface DrainUi {
    data object Idle : DrainUi
    data object Loading : DrainUi
    data class NeedsPrivilege(val availability: ShizukuAvailability) : DrainUi
    data object Failed : DrainUi
    data class Ready(val computedMah: Double?, val rows: List<DrainRow>) : DrainUi
}
