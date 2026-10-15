package com.chargegauge.data

import com.chargegauge.domain.AlertMemory
import com.chargegauge.domain.AlertSettings

data class AppSettings(
    val monitorWhileCharging: Boolean = true,
    val sampleIntervalSec: Int = 2,
    val heatAlertEnabled: Boolean = true,
    val heatAlertC: Double = 45.0,
    val slowAlertEnabled: Boolean = true,
    val slowAlertWatts: Double = 5.0,
    val slowAlertLevelBelow: Int = 90,
    val slowAlertMinutes: Int = 3,
    val healthAlertEnabled: Boolean = true,
    val healthAlertPercent: Double = 80.0,
    val powerScaleEnabled: Boolean = false,
    val powerScale: Double = 1.0,
    val themeId: String = "material_you",
    val amoled: Boolean = false,
    val slowSinceMillis: Long? = null,
    val lastHeatMillis: Long = 0L,
    val lastSlowMillis: Long = 0L,
    val lastHealthMillis: Long = 0L,
) {
    fun toAlertSettings(): AlertSettings = AlertSettings(
        heatEnabled = heatAlertEnabled,
        heatCelsius = heatAlertC,
        slowEnabled = slowAlertEnabled,
        slowWatts = slowAlertWatts,
        slowLevelBelow = slowAlertLevelBelow,
        slowAfterMillis = slowAlertMinutes * 60_000L,
        healthEnabled = healthAlertEnabled,
        healthPercent = healthAlertPercent,
    )

    fun toMemory(): AlertMemory = AlertMemory(
        slowSinceMillis = slowSinceMillis,
        lastHeatMillis = lastHeatMillis,
        lastSlowMillis = lastSlowMillis,
        lastHealthMillis = lastHealthMillis,
    )

    fun androidCurrentScale(): Double = if (powerScaleEnabled) allowedPowerScale(powerScale) else 1.0

    fun withMemory(memory: AlertMemory): AppSettings = copy(
        slowSinceMillis = memory.slowSinceMillis,
        lastHeatMillis = memory.lastHeatMillis,
        lastSlowMillis = memory.lastSlowMillis,
        lastHealthMillis = memory.lastHealthMillis,
    )
}

fun allowedPowerScale(scale: Double): Double = when {
    scale < 0.1 -> 0.001
    scale < 10.0 -> 1.0
    else -> 1000.0
}
