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

    fun withMemory(memory: AlertMemory): AppSettings = copy(
        slowSinceMillis = memory.slowSinceMillis,
        lastHeatMillis = memory.lastHeatMillis,
        lastSlowMillis = memory.lastSlowMillis,
        lastHealthMillis = memory.lastHealthMillis,
    )
}
