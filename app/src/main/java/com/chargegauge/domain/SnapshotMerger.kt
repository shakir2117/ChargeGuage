package com.chargegauge.domain

import kotlin.math.abs

object SnapshotMerger {
    fun merge(
        public: PublicBattery,
        extra: PrivilegedExtras?,
        shizuku: ShizukuAvailability,
        fuelGaugeReadFailed: Boolean,
        now: Long,
    ): BatterySnapshot {
        val systemCurrent = public.currentMicroamps?.let {
            PowerMath.normalizeCurrentMa(it, public.status)
        }
        val fuelCurrent = extra?.batteryCurrentMicroamps?.let {
            PowerMath.normalizeCurrentMa(it, public.status)
        }
        val suspectZero = systemCurrent != null &&
            abs(systemCurrent) < 1.0 &&
            public.status == ChargeStatus.CHARGING &&
            (public.levelPercent ?: 0) < 95
        val (currentMa, currentSource) = when {
            systemCurrent != null && !suspectZero -> systemCurrent to ReadingSource.SYSTEM
            fuelCurrent != null -> fuelCurrent to ReadingSource.FUEL_GAUGE
            systemCurrent != null -> systemCurrent to ReadingSource.SYSTEM
            else -> null to ReadingSource.NONE
        }

        val fuelVoltageMv = extra?.batteryVoltageMicrovolts?.let(PowerMath::voltageToMv)
        val voltageMv = fuelVoltageMv ?: public.voltageMv
        val voltageSource = when {
            fuelVoltageMv != null -> ReadingSource.FUEL_GAUGE
            public.voltageMv != null -> ReadingSource.SYSTEM
            else -> ReadingSource.NONE
        }

        val (cycleCount, cycleSource) = when {
            public.cycleCount != null -> public.cycleCount to ReadingSource.SYSTEM
            extra?.cycleCount != null -> extra.cycleCount to ReadingSource.FUEL_GAUGE
            else -> null to ReadingSource.NONE
        }

        val inputMv = extra?.inputVoltageMicrovolts?.let(PowerMath::voltageToMv)
        val inputMa = extra?.inputCurrentMicroamps
            ?.let(PowerMath::microampsToMa)
            ?.let { abs(it) }
        val limitMv = extra?.limitVoltageMicrovolts?.let(PowerMath::voltageToMv)
        val limitMa = extra?.limitCurrentMicroamps
            ?.let(PowerMath::microampsToMa)
            ?.let { abs(it) }

        return BatterySnapshot(
            timestampMillis = now,
            levelPercent = public.levelPercent,
            status = public.status,
            plugged = public.plugged,
            condition = public.health,
            technology = public.technology,
            temperatureC = public.temperatureC,
            batteryVoltageMv = voltageMv,
            batteryVoltageSource = voltageSource,
            batteryCurrentMa = currentMa,
            batteryCurrentSource = currentSource,
            batteryPowerW = PowerMath.watts(voltageMv, currentMa),
            systemVoltageMv = public.voltageMv,
            systemCurrentMa = systemCurrent,
            fuelVoltageMv = fuelVoltageMv,
            fuelCurrentMa = fuelCurrent,
            remainingMah = PowerMath.capacityToMah(public.chargeCounterUah?.toLong()),
            cycleCount = cycleCount,
            cycleSource = cycleSource,
            timeToFullMillis = public.timeToFullMillis,
            designCapacityMah = PowerMath.capacityToMah(extra?.chargeFullDesignRaw),
            fullCapacityMah = PowerMath.capacityToMah(extra?.chargeFullRaw),
            healthPercent = PowerMath.healthPercent(extra?.chargeFullRaw, extra?.chargeFullDesignRaw),
            fuelGaugeHealthIndex = extra?.fuelGaugeHealthIndex,
            inputName = extra?.inputName,
            inputVoltageMv = inputMv,
            inputCurrentMa = inputMa,
            inputPowerW = PowerMath.watts(inputMv, inputMa),
            chargerLimitWatts = PowerMath.watts(limitMv, limitMa),
            shizuku = shizuku,
            fuelGaugeReadFailed = fuelGaugeReadFailed,
            powerPath = PowerPath.UNKNOWN,
        )
    }
}
