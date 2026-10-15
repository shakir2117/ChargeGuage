package com.chargegauge.data

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import com.chargegauge.domain.BatteryCondition
import com.chargegauge.domain.ChargeStatus
import com.chargegauge.domain.PlugType
import com.chargegauge.domain.PublicBattery
import kotlin.math.roundToInt

class SystemBatteryDataSource(private val context: Context) {
    fun read(): PublicBattery {
        val intent = readStickyBattery()
        val manager = context.getSystemService(BatteryManager::class.java)
        val level = intent.intOrNull(BatteryManager.EXTRA_LEVEL)
        val scale = intent.intOrNull(BatteryManager.EXTRA_SCALE) ?: 100
        val percent = if (level != null && scale > 0) {
            (level * 100f / scale).roundToInt().coerceIn(0, 100)
        } else {
            null
        }
        val temperature = intent.intOrNull(BatteryManager.EXTRA_TEMPERATURE)?.div(10.0)
        val voltage = intent.intOrNull(BatteryManager.EXTRA_VOLTAGE)?.takeIf { it > 0 }
        val statusCode = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val plugCode = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val healthCode = intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1
        val cycle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            intent?.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1)?.takeIf { it >= 0 }
        } else {
            null
        }
        val current = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            .takeIf { it != Int.MIN_VALUE }
        val counter = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
            .takeIf { it != Int.MIN_VALUE && it > 0 }
        val timeToFull = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            manager.computeChargeTimeRemaining().takeIf { it >= 0L }
        } else {
            null
        }
        return PublicBattery(
            levelPercent = percent,
            status = chargeStatus(statusCode),
            plugged = plugType(plugCode),
            health = batteryCondition(healthCode),
            technology = intent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY)?.takeIf { it.isNotBlank() },
            temperatureC = temperature,
            voltageMv = voltage,
            currentMicroamps = current,
            chargeCounterUah = counter,
            cycleCount = cycle,
            timeToFullMillis = timeToFull,
        )
    }

    private fun readStickyBattery(): Intent? {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        return if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(null, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(null, filter)
        }
    }

    private fun Intent?.intOrNull(key: String): Int? {
        val value = this?.getIntExtra(key, Int.MIN_VALUE) ?: return null
        return value.takeIf { it != Int.MIN_VALUE }
    }

    private fun chargeStatus(code: Int): ChargeStatus = when (code) {
        BatteryManager.BATTERY_STATUS_CHARGING -> ChargeStatus.CHARGING
        BatteryManager.BATTERY_STATUS_DISCHARGING -> ChargeStatus.DISCHARGING
        BatteryManager.BATTERY_STATUS_NOT_CHARGING -> ChargeStatus.NOT_CHARGING
        BatteryManager.BATTERY_STATUS_FULL -> ChargeStatus.FULL
        else -> ChargeStatus.UNKNOWN
    }

    private fun plugType(code: Int): PlugType = when (code) {
        BatteryManager.BATTERY_PLUGGED_AC -> PlugType.AC
        BatteryManager.BATTERY_PLUGGED_USB -> PlugType.USB
        BatteryManager.BATTERY_PLUGGED_WIRELESS -> PlugType.WIRELESS
        BatteryManager.BATTERY_PLUGGED_DOCK -> PlugType.DOCK
        else -> PlugType.NONE
    }

    private fun batteryCondition(code: Int): BatteryCondition = when (code) {
        BatteryManager.BATTERY_HEALTH_GOOD -> BatteryCondition.GOOD
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> BatteryCondition.OVERHEAT
        BatteryManager.BATTERY_HEALTH_DEAD -> BatteryCondition.DEAD
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> BatteryCondition.OVER_VOLTAGE
        BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> BatteryCondition.FAILURE
        BatteryManager.BATTERY_HEALTH_COLD -> BatteryCondition.COLD
        else -> BatteryCondition.UNKNOWN
    }
}
