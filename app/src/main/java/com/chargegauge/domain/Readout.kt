package com.chargegauge.domain

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

object Readout {
    private val clock: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, HH:mm", Locale.getDefault())
    private val iso: DateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME

    fun watts(value: Double?): String {
        if (value == null) return "—"
        return String.format(Locale.US, "%.2f", value)
    }

    fun volts(millivolts: Int?): String {
        if (millivolts == null) return "—"
        return String.format(Locale.US, "%.2f V", millivolts / 1000.0)
    }

    fun amps(milliamps: Double?): String {
        if (milliamps == null) return "—"
        val amps = milliamps / 1000.0
        val pattern = if (abs(amps) < 1.0) "%.3f A" else "%.2f A"
        return String.format(Locale.US, pattern, amps)
    }

    fun percent(value: Int?): String = if (value == null) "—" else "$value%"

    fun health(value: Double?): String = if (value == null) "—" else "${value.toInt()}%"

    fun temperature(celsius: Double?): String {
        if (celsius == null) return "—"
        return String.format(Locale.US, "%.1f°C", celsius)
    }

    fun duration(millis: Long?): String {
        if (millis == null || millis < 0L) return "—"
        val minutes = millis / 60_000L
        val hours = minutes / 60L
        val remain = minutes % 60L
        return if (hours > 0) "${hours}h ${remain}m" else "${remain}m"
    }

    fun mah(value: Double?): String {
        if (value == null) return "—"
        return String.format(Locale.US, "%,.0f mAh", value)
    }

    fun wattHours(value: Double?): String {
        if (value == null) return "—"
        return String.format(Locale.US, "%.2f Wh", value)
    }

    fun clock(millis: Long): String {
        return clock.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
    }

    fun iso(millis: Long): String {
        return iso.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
    }

    fun pathLabel(path: PowerPath, levelPercent: Int?): String = when (path) {
        PowerPath.BYPASS_FULL -> "Running from the adapter"
        PowerPath.BYPASS_LIMIT -> "Charge limit ${percent(levelPercent)}"
        PowerPath.PAUSED -> "Charging paused"
        PowerPath.CHARGING -> "Charging"
        PowerPath.DISCHARGING -> "Discharging"
        PowerPath.UNPLUGGED -> "Unplugged"
        PowerPath.UNKNOWN -> "Reading"
    }

    fun statusLabel(status: ChargeStatus): String = when (status) {
        ChargeStatus.CHARGING -> "Charging"
        ChargeStatus.DISCHARGING -> "Discharging"
        ChargeStatus.NOT_CHARGING -> "Not charging"
        ChargeStatus.FULL -> "Full"
        ChargeStatus.UNKNOWN -> "Unknown"
    }

    fun plugLabel(plug: PlugType): String = when (plug) {
        PlugType.AC -> "AC"
        PlugType.USB -> "USB"
        PlugType.WIRELESS -> "Wireless"
        PlugType.DOCK -> "Dock"
        PlugType.NONE -> "Unplugged"
    }

    fun conditionLabel(condition: BatteryCondition): String = when (condition) {
        BatteryCondition.GOOD -> "Good"
        BatteryCondition.OVERHEAT -> "Overheating"
        BatteryCondition.DEAD -> "Dead"
        BatteryCondition.OVER_VOLTAGE -> "Over voltage"
        BatteryCondition.FAILURE -> "Failure"
        BatteryCondition.COLD -> "Cold"
        BatteryCondition.UNKNOWN -> "Unknown"
    }

    fun sourceLabel(source: ReadingSource): String = when (source) {
        ReadingSource.SYSTEM -> "System"
        ReadingSource.FUEL_GAUGE -> "Fuel gauge"
        ReadingSource.NONE -> "Unavailable"
    }
}
