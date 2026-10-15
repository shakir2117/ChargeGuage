package com.chargegauge.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.chargegauge.ChargeGaugeApp
import com.chargegauge.MainActivity
import com.chargegauge.R
import com.chargegauge.domain.AlertType
import com.chargegauge.domain.AlertEvaluator
import com.chargegauge.domain.BatterySnapshot
import com.chargegauge.domain.Readout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MonitorService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loop: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground(placeholderNotification())
        acquireWakeLock()
        if (loop?.isActive != true) {
            loop = scope.launch { monitor() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        scope.cancel()
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    private suspend fun monitor() {
        val container = (application as ChargeGaugeApp).container
        while (true) {
            val settings = container.settings.current()
            if (!settings.monitorWhileCharging) {
                stopSelf()
                return
            }
            val snapshot = container.repository.refreshOnce()
            container.sessions.onSnapshot(snapshot, settings.sampleIntervalSec * 1000L)
            if (!snapshot.isPlugged) {
                stopSelf()
                return
            }
            val tick = AlertEvaluator.tick(
                snapshot = snapshot,
                settings = settings.toAlertSettings(),
                memory = settings.toMemory(),
                now = snapshot.timestampMillis,
            )
            if (tick.memory != settings.toMemory()) {
                container.settings.update { it.withMemory(tick.memory) }
            }
            tick.alerts.forEach { alert ->
                val id = when (alert.type) {
                    AlertType.HEAT -> HEAT_NOTIFICATION
                    AlertType.SLOW -> SLOW_NOTIFICATION
                    AlertType.HEALTH -> HEALTH_NOTIFICATION
                }
                notify(id, alert.title, alert.text, ongoing = false, channel = ALERT_CHANNEL)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(MONITOR_NOTIFICATION, monitorNotification(snapshot))
            delay(settings.sampleIntervalSec * 1000L)
        }
    }

    private fun startInForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                MONITOR_NOTIFICATION,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(MONITOR_NOTIFICATION, notification)
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val power = getSystemService(PowerManager::class.java)
        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "chargegauge:monitor").apply {
            setReferenceCounted(false)
            acquire(12 * 60 * 60 * 1000L)
        }
    }

    private fun createChannels() {
        val manager = getSystemService(NotificationManager::class.java)
        val monitor = NotificationChannel(
            MONITOR_CHANNEL,
            "Charging monitor",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Live watts, volts, and amps while the phone is plugged in"
        }
        val alerts = NotificationChannel(
            ALERT_CHANNEL,
            "Battery alerts",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Heat, slow charging, and capacity warnings"
        }
        manager.createNotificationChannel(monitor)
        manager.createNotificationChannel(alerts)
    }

    private fun placeholderNotification(): Notification {
        return NotificationCompat.Builder(this, MONITOR_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_bolt)
            .setContentTitle("Reading battery")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent())
            .build()
    }

    private fun monitorNotification(snapshot: BatterySnapshot): Notification {
        val title = if (snapshot.batteryPowerW == null) {
            "Power unavailable"
        } else {
            "${Readout.watts(snapshot.batteryPowerW)} W"
        }
        val text = listOf(
            Readout.volts(snapshot.batteryVoltageMv),
            Readout.amps(snapshot.batteryCurrentMa),
            Readout.percent(snapshot.levelPercent),
            Readout.plugLabel(snapshot.plugged),
        ).joinToString(" · ")
        return NotificationCompat.Builder(this, MONITOR_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_bolt)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent())
            .build()
    }

    private fun notify(id: Int, title: String, text: String, ongoing: Boolean, channel: String) {
        val notification = NotificationCompat.Builder(this, channel)
            .setSmallIcon(R.drawable.ic_stat_bolt)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(ongoing)
            .setAutoCancel(!ongoing)
            .setContentIntent(openAppIntent())
            .build()
        getSystemService(NotificationManager::class.java).notify(id, notification)
    }

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        @Volatile
        var isRunning: Boolean = false
            private set

        private const val MONITOR_CHANNEL = "monitor"
        private const val ALERT_CHANNEL = "alerts"
        private const val MONITOR_NOTIFICATION = 1
        private const val HEAT_NOTIFICATION = 2
        private const val SLOW_NOTIFICATION = 3
        private const val HEALTH_NOTIFICATION = 4

        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, MonitorService::class.java))
            } catch (error: Throwable) {
                Log.w("MonitorService", "Could not start charging monitor", error)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MonitorService::class.java))
        }
    }
}
