package com.chargegauge

import android.app.Application
import com.chargegauge.data.BatteryRepository
import com.chargegauge.data.SessionRecorder
import com.chargegauge.data.SettingsStore
import com.chargegauge.data.ShizukuGateway
import com.chargegauge.data.SystemBatteryDataSource
import com.chargegauge.data.db.ChargeDatabase
import com.chargegauge.export.CsvExporter
import com.chargegauge.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

class ChargeGaugeApp : Application() {
    lateinit var container: AppContainer
        private set

    @Volatile
    var uiVisible: Boolean = false

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        scope.launch {
            container.repository.snapshot
                .filter { it.timestampMillis > 0L }
                .collect { snapshot -> WidgetUpdater.render(this@ChargeGaugeApp, snapshot) }
        }
        scope.launch {
            val snapshot = container.repository.refreshOnce()
            container.sessions.closeIfUnplugged(snapshot)
        }
    }
}

class AppContainer(context: Application) {
    val database = ChargeDatabase.create(context)
    val settings = SettingsStore(context)
    val gateway = ShizukuGateway(context)
    val repository = BatteryRepository(
        system = SystemBatteryDataSource(context),
        gateway = gateway,
        dao = database.chargeDao(),
        settings = settings,
    )
    val sessions = SessionRecorder(database.chargeDao())
    val exporter = CsvExporter(context, database.chargeDao())
}
