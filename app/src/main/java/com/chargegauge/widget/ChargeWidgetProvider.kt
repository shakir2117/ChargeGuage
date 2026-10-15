package com.chargegauge.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.chargegauge.ChargeGaugeApp
import com.chargegauge.MainActivity
import com.chargegauge.R
import com.chargegauge.domain.BatterySnapshot
import com.chargegauge.domain.Readout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ChargeWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val app = context.applicationContext as ChargeGaugeApp
        CoroutineScope(Dispatchers.IO).launch {
            val snapshot = app.container.repository.refreshOnce()
            WidgetUpdater.render(context, snapshot)
        }
    }
}

object WidgetUpdater {
    fun render(context: Context, snapshot: BatterySnapshot) {
        val manager = AppWidgetManager.getInstance(context)
        val component = ComponentName(context, ChargeWidgetProvider::class.java)
        val ids = manager.getAppWidgetIds(component)
        if (ids.isEmpty()) return
        val views = remoteViews(context, snapshot)
        ids.forEach { id -> manager.updateAppWidget(id, views) }
    }

    private fun remoteViews(context: Context, snapshot: BatterySnapshot): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_charge)
        val watts = when (snapshot.powerPath) {
            com.chargegauge.domain.PowerPath.BYPASS_FULL -> "Adapter"
            com.chargegauge.domain.PowerPath.BYPASS_LIMIT -> "Limit ${Readout.percent(snapshot.levelPercent)}"
            com.chargegauge.domain.PowerPath.PAUSED -> "Paused"
            else -> if (snapshot.timestampMillis == 0L || snapshot.batteryPowerW == null) {
                "— W"
            } else {
                "${Readout.watts(snapshot.batteryPowerW)} W"
            }
        }
        views.setTextViewText(R.id.widget_watts, watts)
        views.setTextViewText(
            R.id.widget_detail,
            listOf(
                Readout.volts(snapshot.batteryVoltageMv),
                Readout.amps(snapshot.batteryCurrentMa),
                Readout.percent(snapshot.levelPercent),
            ).joinToString("   "),
        )
        val status = if (snapshot.powerPath.isAdapterPowered) {
            Readout.pathLabel(snapshot.powerPath, snapshot.levelPercent)
        } else {
            Readout.plugLabel(snapshot.plugged)
        }
        views.setTextViewText(R.id.widget_status, status)
        val pending = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        views.setOnClickPendingIntent(R.id.widget_root, pending)
        return views
    }
}
