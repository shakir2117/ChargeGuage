package com.chargegauge.domain

enum class AlertType { HEAT, SLOW, HEALTH }

data class AlertSettings(
    val heatEnabled: Boolean,
    val heatCelsius: Double,
    val slowEnabled: Boolean,
    val slowWatts: Double,
    val slowLevelBelow: Int,
    val slowAfterMillis: Long,
    val healthEnabled: Boolean,
    val healthPercent: Double,
)

data class AlertMemory(
    val slowSinceMillis: Long? = null,
    val lastHeatMillis: Long = 0L,
    val lastSlowMillis: Long = 0L,
    val lastHealthMillis: Long = 0L,
)

data class RaisedAlert(
    val type: AlertType,
    val title: String,
    val text: String,
)

data class AlertTick(
    val memory: AlertMemory,
    val alerts: List<RaisedAlert>,
)

object AlertEvaluator {
    const val HEAT_COOLDOWN_MILLIS = 30 * 60 * 1000L
    const val SLOW_COOLDOWN_MILLIS = 30 * 60 * 1000L
    const val HEALTH_COOLDOWN_MILLIS = 24 * 60 * 60 * 1000L

    fun tick(
        snapshot: BatterySnapshot,
        settings: AlertSettings,
        memory: AlertMemory,
        now: Long,
    ): AlertTick {
        val alerts = mutableListOf<RaisedAlert>()
        var next = memory

        val temperature = snapshot.temperatureC
        if (settings.heatEnabled && snapshot.isPlugged && temperature != null && temperature >= settings.heatCelsius) {
            if (cooledDown(next.lastHeatMillis, now, HEAT_COOLDOWN_MILLIS)) {
                alerts += RaisedAlert(
                    type = AlertType.HEAT,
                    title = "Battery is hot",
                    text = "Battery temperature is ${formatTemp(temperature)}. Unplug it if it keeps climbing.",
                )
                next = next.copy(lastHeatMillis = now)
            }
        }

        val power = snapshot.batteryPowerW
        val level = snapshot.levelPercent
        val slowNow = settings.slowEnabled &&
            !snapshot.powerPath.isAdapterPowered &&
            snapshot.status == ChargeStatus.CHARGING &&
            power != null &&
            power >= 0.0 &&
            power < settings.slowWatts &&
            level != null &&
            level < settings.slowLevelBelow
        next = if (slowNow) {
            val started = next.slowSinceMillis ?: now
            if (now - started >= settings.slowAfterMillis && cooledDown(next.lastSlowMillis, now, SLOW_COOLDOWN_MILLIS)) {
                alerts += RaisedAlert(
                    type = AlertType.SLOW,
                    title = "Charging is slow",
                    text = "Power has stayed under ${trim(settings.slowWatts)} W. Check the cable and charger.",
                )
                next.copy(slowSinceMillis = started, lastSlowMillis = now)
            } else {
                next.copy(slowSinceMillis = started)
            }
        } else {
            next.copy(slowSinceMillis = null)
        }

        val health = snapshot.healthPercent
        if (settings.healthEnabled && health != null && health < settings.healthPercent) {
            if (cooledDown(next.lastHealthMillis, now, HEALTH_COOLDOWN_MILLIS)) {
                alerts += RaisedAlert(
                    type = AlertType.HEALTH,
                    title = "Battery capacity is down",
                    text = "Full capacity is ${health.toInt()}% of the design capacity.",
                )
                next = next.copy(lastHealthMillis = now)
            }
        }

        return AlertTick(next, alerts)
    }

    private fun cooledDown(lastMillis: Long, now: Long, cooldown: Long): Boolean {
        return lastMillis == 0L || now - lastMillis >= cooldown
    }

    private fun formatTemp(celsius: Double): String = "${"%.1f".format(celsius)}°C"

    private fun trim(value: Double): String {
        return if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(value)
    }
}
