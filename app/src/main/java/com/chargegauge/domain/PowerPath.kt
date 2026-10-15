package com.chargegauge.domain

import kotlin.math.abs

enum class PowerPath {
    UNKNOWN,
    UNPLUGGED,
    CHARGING,
    DISCHARGING,
    BYPASS_FULL,
    BYPASS_LIMIT,
    PAUSED,
    ;

    val isAdapterPowered: Boolean
        get() = this == BYPASS_FULL || this == BYPASS_LIMIT || this == PAUSED
}

object PowerPathDetector {
    const val IDLE_MILLIAMPS = 150.0

    /** A real charge. A few hundred milliamps can remain after a charge limit stops the battery. */
    const val STRONG_CHARGE_MILLIAMPS = 500.0

    fun detect(snapshot: BatterySnapshot): PowerPath {
        if (snapshot.timestampMillis <= 0L) return PowerPath.UNKNOWN
        if (!snapshot.isPlugged) {
            val drawing = snapshot.batteryCurrentMa != null && snapshot.batteryCurrentMa < 0.0
            return if (drawing || snapshot.status == ChargeStatus.DISCHARGING) {
                PowerPath.DISCHARGING
            } else {
                PowerPath.UNPLUGGED
            }
        }

        val current = snapshot.batteryCurrentMa
        if (current != null && current >= STRONG_CHARGE_MILLIAMPS) return PowerPath.CHARGING
        if (current != null && current <= -IDLE_MILLIAMPS) return PowerPath.DISCHARGING

        val level = snapshot.levelPercent
        val idle = current == null || abs(current) < STRONG_CHARGE_MILLIAMPS
        val thermal = snapshot.condition == BatteryCondition.OVERHEAT ||
            snapshot.condition == BatteryCondition.COLD
        val notCharging = snapshot.status == ChargeStatus.NOT_CHARGING ||
            snapshot.status == ChargeStatus.FULL

        if (thermal && notCharging) return PowerPath.PAUSED
        if (snapshot.status == ChargeStatus.FULL && level != null && level < 99) {
            return PowerPath.BYPASS_LIMIT
        }
        if (snapshot.status == ChargeStatus.FULL || (level != null && level >= 99 && idle && notCharging)) {
            return PowerPath.BYPASS_FULL
        }
        if (snapshot.status == ChargeStatus.NOT_CHARGING && idle) {
            return if (level != null && level >= 99) PowerPath.BYPASS_FULL else PowerPath.BYPASS_LIMIT
        }
        if (snapshot.status == ChargeStatus.CHARGING && idle && level != null && level >= 99) {
            return PowerPath.BYPASS_FULL
        }
        if (snapshot.status == ChargeStatus.DISCHARGING) return PowerPath.DISCHARGING
        return PowerPath.CHARGING
    }
}
