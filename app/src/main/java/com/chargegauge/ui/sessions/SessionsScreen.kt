package com.chargegauge.ui.sessions

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chargegauge.ChargeGaugeApp
import com.chargegauge.data.db.ChargeDao
import com.chargegauge.data.db.ChargeSessionEntity
import com.chargegauge.data.db.SampleEntity
import com.chargegauge.domain.PlugType
import com.chargegauge.domain.Readout
import com.chargegauge.ui.components.Fact
import com.chargegauge.ui.components.SeriesChart
import com.chargegauge.ui.components.samplePoints
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Composable
fun SessionsScreen(
    onOpenSession: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val viewModel: SessionsViewModel = viewModel(
        factory = AndroidViewModelFactory(context.applicationContext as Application),
    )
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    if (sessions.isEmpty()) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("No charge sessions yet", style = MaterialTheme.typography.titleLarge)
            Text(
                "Plug in and leave recording on. ChargeGauge saves each charge, including watts, energy, and temperature.",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(sessions, key = { it.id }) { session ->
            SessionRow(session, onClick = { onOpenSession(session.id) })
        }
    }
}

@Composable
private fun SessionRow(session: ChargeSessionEntity, onClick: () -> Unit) {
    val end = session.endedAt
    val duration = if (end != null) end - session.startedAt else System.currentTimeMillis() - session.startedAt
    val plug = runCatching { PlugType.valueOf(session.plugType) }.getOrDefault(PlugType.NONE)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                "${Readout.plugLabel(plug)} · ${Readout.clock(session.startedAt)}",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "${session.startLevel ?: "—"}% → ${session.endLevel ?: "—"}% · ${Readout.duration(duration)}",
            )
            Text(
                "${Readout.wattHours(session.energyWh)} added · peak ${session.peakWatts?.let { "${Readout.watts(it)} W" } ?: "—"}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (end == null) {
                Text("Recording", color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

class SessionsViewModel(app: Application) : AndroidViewModel(app) {
    val sessions: StateFlow<List<ChargeSessionEntity>> =
        (app as ChargeGaugeApp).container.database.chargeDao()
            .observeSessions()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

class AndroidViewModelFactory(private val app: Application) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val model = when (modelClass) {
            SessionsViewModel::class.java -> SessionsViewModel(app)
            com.chargegauge.ui.health.HealthViewModel::class.java ->
                com.chargegauge.ui.health.HealthViewModel(app)
            else -> error("No view model for ${modelClass.name}")
        }
        return model as T
    }
}

@Composable
fun SessionDetailScreen(
    sessionId: Long,
    onDeleted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val viewModel: SessionDetailViewModel = viewModel(
        key = "session-$sessionId",
        factory = SessionDetailViewModel.factory(context.applicationContext as Application, sessionId),
    )
    val session by viewModel.session.collectAsStateWithLifecycle()
    val samples by viewModel.samples.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val current = session
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (current == null) {
            Text("This session is no longer on the phone.")
            return@Column
        }
        val end = current.endedAt
        val duration = if (end != null) end - current.startedAt else System.currentTimeMillis() - current.startedAt
        Text(Readout.clock(current.startedAt), style = MaterialTheme.typography.headlineSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Fact("From", "${current.startLevel ?: "—"}%", Modifier.weight(1f))
            Fact("To", "${current.endLevel ?: "—"}%", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Fact("Energy", Readout.wattHours(current.energyWh), Modifier.weight(1f))
            Fact("Duration", Readout.duration(duration), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Fact("Peak", current.peakWatts?.let { "${Readout.watts(it)} W" } ?: "—", Modifier.weight(1f))
            Fact("Average", current.averageWatts?.let { "${Readout.watts(it)} W" } ?: "—", Modifier.weight(1f))
        }
        Fact("Hottest", Readout.temperature(current.maxTempC))
        Text("Power during this charge", style = MaterialTheme.typography.titleMedium)
        if (samplePoints(samples).isEmpty()) {
            Text("Current was not available, so this session has no watt curve and little or no energy.")
        } else {
            SeriesChart(samplePoints(samples))
        }
        TextButton(onClick = { confirmDelete = true }) { Text("Delete session") }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this session?") },
            text = { Text("The saved samples for this charge will be removed from the phone.") },
            confirmButton = {
                Button(onClick = { viewModel.delete(onDeleted) }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Keep") }
            },
        )
    }
}

class SessionDetailViewModel(
    app: Application,
    private val sessionId: Long,
) : AndroidViewModel(app) {
    private val dao: ChargeDao = (app as ChargeGaugeApp).container.database.chargeDao()
    val session = dao.observeSession(sessionId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val samples: StateFlow<List<SampleEntity>> = dao.observeSamples(sessionId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            dao.deleteSession(sessionId)
            onDone()
        }
    }

    companion object {
        fun factory(app: Application, sessionId: Long): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return SessionDetailViewModel(app, sessionId) as T
                }
            }
        }
    }
}
