package com.chargegauge.domain

import kotlin.math.abs

object PowerMath {
    fun microampsToMa(raw: Int): Double? {
        if (raw == Int.MIN_VALUE) return null
        return microampsToMa(raw.toDouble())
    }

    fun microampsToMa(raw: Double): Double? {
        if (raw.isNaN() || raw.isInfinite()) return null
        val milliamps = raw / 1000.0
        if (abs(milliamps) > 20_000.0) return null
        return milliamps
    }

    /**
     * Android documents positive current as charge entering the battery.
     * Flip the sign only when it disagrees with a definite charge or discharge state.
     * [scale] corrects phones that report current in the wrong unit.
     */
    fun normalizeCurrentMa(rawMicroamps: Int, status: ChargeStatus, scale: Double = 1.0): Double? {
        if (rawMicroamps == Int.MIN_VALUE) return null
        val milliamps = microampsToMa(rawMicroamps.toDouble() * scale) ?: return null
        return when {
            status == ChargeStatus.CHARGING && milliamps < 0 -> -milliamps
            status == ChargeStatus.DISCHARGING && milliamps > 0 -> -milliamps
            else -> milliamps
        }
    }

    fun voltageToMv(raw: Int): Int? {
        if (raw <= 0) return null
        return if (raw > 100_000) raw / 1000 else raw
    }

    /**
     * Blends the latest current into the previous one. A sign change starts over
     * so charging and discharging are not averaged together.
     */
    fun smoothCurrentMa(previous: Double?, sample: Double?, alpha: Double = 0.35): Double? {
        if (sample == null) return null
        if (previous == null || previous * sample < 0.0) return sample
        return previous + alpha * (sample - previous)
    }

    /** While a charge is underway, blend samples. Stopping or starting a charge shows the new sample at once. */
    fun displayedCurrentMa(
        previous: Double?,
        sample: Double?,
        charging: Boolean,
        wasCharging: Boolean,
    ): Double? {
        if (sample == null) return null
        if (!charging || !wasCharging) return sample
        return smoothCurrentMa(previous, sample)
    }

    fun watts(voltageMv: Int?, currentMa: Double?): Double? {
        if (voltageMv == null || currentMa == null || voltageMv <= 0) return null
        return (voltageMv / 1000.0) * (currentMa / 1000.0)
    }

    fun healthPercent(fullRaw: Long?, designRaw: Long?): Double? {
        if (fullRaw == null || designRaw == null || fullRaw <= 0L || designRaw <= 0L) return null
        val percent = fullRaw.toDouble() / designRaw.toDouble() * 100.0
        if (percent < 1.0 || percent > 120.0) return null
        return percent
    }

    fun capacityToMah(raw: Long?): Double? {
        if (raw == null || raw <= 0L) return null
        return if (raw > 20_000L) raw / 1000.0 else raw.toDouble()
    }

    fun energyWh(
        previousTimestamp: Long?,
        timestamp: Long,
        watts: Double?,
    ): Double {
        if (previousTimestamp == null || watts == null || watts <= 0.0) return 0.0
        val elapsed = timestamp - previousTimestamp
        if (elapsed <= 0L || elapsed > MAX_ENERGY_GAP_MILLIS) return 0.0
        return watts * (elapsed / 3_600_000.0)
    }

    fun normalizeHealthIndex(raw: Double?): Double? {
        if (raw == null || raw <= 0.0) return null
        return when {
            raw <= 100.0 -> raw
            raw <= 10_000.0 -> raw / 100.0
            else -> null
        }
    }

    const val MAX_ENERGY_GAP_MILLIS = 90_000L
}
