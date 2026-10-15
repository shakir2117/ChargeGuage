package com.chargegauge.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.chargegauge.ChargeGaugeApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PowerConnectionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pending = goAsync()
        val app = context.applicationContext as ChargeGaugeApp
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings = app.container.settings.current()
                if (!settings.monitorWhileCharging) return@launch
                when (intent?.action) {
                    Intent.ACTION_POWER_DISCONNECTED -> {
                        val snapshot = app.container.repository.refreshOnce()
                        app.container.sessions.closeIfUnplugged(snapshot)
                        MonitorService.stop(context)
                    }
                    Intent.ACTION_POWER_CONNECTED, Intent.ACTION_BOOT_COMPLETED -> {
                        val snapshot = app.container.repository.refreshOnce()
                        if (snapshot.isPlugged) MonitorService.start(context)
                    }
                }
            } catch (error: Throwable) {
                Log.w(TAG, "Could not update charging monitor", error)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "PowerConnection"
    }
}
