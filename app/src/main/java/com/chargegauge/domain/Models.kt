package com.chargegauge.domain

enum class ChargeStatus {
    UNKNOWN,
    CHARGING,
    DISCHARGING,
    NOT_CHARGING,
    FULL,
}

enum class PlugType {
    NONE,
    AC,
    USB,
    WIRELESS,
    DOCK,
}

enum class BatteryCondition {
    UNKNOWN,
    GOOD,
    OVERHEAT,
    DEAD,
    OVER_VOLTAGE,
    FAILURE,
    COLD,
}

enum class ReadingSource {
    SYSTEM,
    FUEL_GAUGE,
    NONE,
}

enum class ShizukuAvailability {
    NOT_INSTALLED,
    STOPPED,
    DENIED,
    READY,
    ERROR,
}

data class PublicBattery(
    val levelPercent: Int?,
    val status: ChargeStatus,
    val plugged: PlugType,
    val health: BatteryCondition,
    val technology: String?,
    val temperatureC: Double?,
    val voltageMv: Int?,
    val currentMicroamps: Int?,
    val chargeCounterUah: Int?,
    val cycleCount: Int?,
    val timeToFullMillis: Long?,
)

data class PrivilegedExtras(
    val batteryCurrentMicroamps: Int?,
    val batteryVoltageMicrovolts: Int?,
    val chargeFullRaw: Long?,
    val chargeFullDesignRaw: Long?,
    val cycleCount: Int?,
    val fuelGaugeHealthIndex: Double?,
    val inputName: String?,
    val inputVoltageMicrovolts: Int?,
    val inputCurrentMicroamps: Int?,
    val limitVoltageMicrovolts: Int?,
    val limitCurrentMicroamps: Int?,
)

data class BatterySnapshot(
    val timestampMillis: Long,
    val levelPercent: Int?,
    val status: ChargeStatus,
    val plugged: PlugType,
    val condition: BatteryCondition,
    val technology: String?,
    val temperatureC: Double?,
    val batteryVoltageMv: Int?,
    val batteryVoltageSource: ReadingSource,
    val batteryCurrentMa: Double?,
    val batteryCurrentSource: ReadingSource,
    val batteryPowerW: Double?,
    val systemVoltageMv: Int?,
    val systemCurrentMa: Double?,
    val fuelVoltageMv: Int?,
    val fuelCurrentMa: Double?,
    val remainingMah: Double?,
    val cycleCount: Int?,
    val cycleSource: ReadingSource,
    val timeToFullMillis: Long?,
    val designCapacityMah: Double?,
    val fullCapacityMah: Double?,
    val healthPercent: Double?,
    val fuelGaugeHealthIndex: Double?,
    val inputName: String?,
    val inputVoltageMv: Int?,
    val inputCurrentMa: Double?,
    val inputPowerW: Double?,
    val chargerLimitWatts: Double?,
    val shizuku: ShizukuAvailability,
    val fuelGaugeReadFailed: Boolean,
    val powerPath: PowerPath,
) {
    val isPlugged: Boolean get() = plugged != PlugType.NONE

    companion object {
        val Empty = BatterySnapshot(
            timestampMillis = 0L,
            levelPercent = null,
            status = ChargeStatus.UNKNOWN,
            plugged = PlugType.NONE,
            condition = BatteryCondition.UNKNOWN,
            technology = null,
            temperatureC = null,
            batteryVoltageMv = null,
            batteryVoltageSource = ReadingSource.NONE,
            batteryCurrentMa = null,
            batteryCurrentSource = ReadingSource.NONE,
            batteryPowerW = null,
            systemVoltageMv = null,
            systemCurrentMa = null,
            fuelVoltageMv = null,
            fuelCurrentMa = null,
            remainingMah = null,
            cycleCount = null,
            cycleSource = ReadingSource.NONE,
            timeToFullMillis = null,
            designCapacityMah = null,
            fullCapacityMah = null,
            healthPercent = null,
            fuelGaugeHealthIndex = null,
            inputName = null,
            inputVoltageMv = null,
            inputCurrentMa = null,
            inputPowerW = null,
            chargerLimitWatts = null,
            shizuku = ShizukuAvailability.NOT_INSTALLED,
            fuelGaugeReadFailed = false,
            powerPath = PowerPath.UNKNOWN,
        )
    }
}
