package com.chargegauge.domain

import kotlin.math.abs

object PrivilegedExtrasBuilder {
    private val batteryNames = setOf("battery", "bms", "bat", "maxfg", "fg")

    fun build(
        sysfs: Map<String, Map<String, Double>>,
        dumpsys: Map<String, String>,
    ): PrivilegedExtras? {
        if (sysfs.isEmpty() && dumpsys.isEmpty()) return null
        val batteryName = pickBattery(sysfs)
        val battery = batteryName?.let { sysfs[it] }.orEmpty()
        val inputName = pickInput(sysfs, batteryName)
        val input = inputName?.let { sysfs[it] }.orEmpty()

        val cycle = intValue(battery["cycle_count"])
            ?: intValue(battery["battery_cycle"])
            ?: dumpsys.longValue("cycle count")?.toInt()
        val healthIndex = PowerMath.normalizeHealthIndex(
            battery["health_index"]
                ?: battery["fg_asoc"]
                ?: dumpsys["msavedbatteryasoc"]?.toDoubleOrNull(),
        )
        val limitCurrent = dumpsys.longValue("max charging current")?.toInt()
        val limitVoltage = dumpsys.longValue("max charging voltage")?.toInt()

        val extras = PrivilegedExtras(
            batteryCurrentMicroamps = intValue(battery["current_now"]),
            batteryVoltageMicrovolts = intValue(battery["voltage_now"]),
            chargeFullRaw = longValue(battery["charge_full"]),
            chargeFullDesignRaw = longValue(battery["charge_full_design"]),
            cycleCount = cycle?.takeIf { it >= 0 },
            fuelGaugeHealthIndex = healthIndex,
            inputName = inputName,
            inputVoltageMicrovolts = intValue(input["voltage_now"]),
            inputCurrentMicroamps = intValue(input["current_now"]),
            limitVoltageMicrovolts = limitVoltage?.takeIf { it > 0 },
            limitCurrentMicroamps = limitCurrent?.takeIf { it > 0 },
        )
        val hasSignal = extras.batteryCurrentMicroamps != null ||
            extras.chargeFullRaw != null ||
            extras.chargeFullDesignRaw != null ||
            extras.cycleCount != null ||
            extras.inputVoltageMicrovolts != null ||
            extras.inputCurrentMicroamps != null ||
            extras.chargerLimitPresent()
        return extras.takeIf { hasSignal }
    }

    private fun PrivilegedExtras.chargerLimitPresent(): Boolean {
        return limitVoltageMicrovolts != null && limitCurrentMicroamps != null
    }

    private fun pickBattery(sysfs: Map<String, Map<String, Double>>): String? {
        sysfs["battery"]?.let { return "battery" }
        return sysfs.keys.firstOrNull { it.lowercase() in batteryNames }
            ?: sysfs.entries.firstOrNull { it.value.containsKey("charge_full_design") }?.key
    }

    private fun pickInput(
        sysfs: Map<String, Map<String, Double>>,
        batteryName: String?,
    ): String? {
        val candidates = sysfs.filterKeys { name ->
            name != batteryName && name.lowercase() !in batteryNames
        }
        val ranked = candidates
            .filter { (_, values) -> values["voltage_now"] != null || values["current_now"] != null }
            .maxByOrNull { (name, values) ->
                val current = abs(values["current_now"] ?: 0.0)
                val bonus = if (isChargerPort(name)) 1_000_000_000.0 else 0.0
                bonus + current
            }
        return ranked?.key
    }

    private fun isChargerPort(name: String): Boolean {
        val lower = name.lowercase()
        return lower.contains("usb") ||
            lower.contains("wireless") ||
            lower.contains("ac") ||
            lower.contains("dc") ||
            lower.contains("pc_port")
    }

    private fun intValue(raw: Double?): Int? {
        val longValue = longValue(raw) ?: return null
        if (longValue > Int.MAX_VALUE || longValue < Int.MIN_VALUE) return null
        return longValue.toInt()
    }

    private fun longValue(raw: Double?): Long? {
        if (raw == null || raw.isNaN()) return null
        return raw.toLong()
    }

    private fun Map<String, String>.longValue(key: String): Long? {
        val text = this[key] ?: return null
        return Regex("""-?\d+""").find(text)?.value?.toLongOrNull()
    }
}
