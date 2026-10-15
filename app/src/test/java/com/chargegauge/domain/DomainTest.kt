package com.chargegauge.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainTest {
    @Test
    fun fullBatteryOnTheAdapterIsBypass() {
        val path = PowerPathDetector.detect(
            plugged(status = ChargeStatus.FULL, level = 100, currentMa = 20.0),
        )
        assertEquals(PowerPath.BYPASS_FULL, path)
    }

    @Test
    fun chargeLimitBelowFullIsBypass() {
        val resting = PowerPathDetector.detect(
            plugged(status = ChargeStatus.NOT_CHARGING, level = 80, currentMa = 12.0),
        )
        val reportedFull = PowerPathDetector.detect(
            plugged(status = ChargeStatus.FULL, level = 80, currentMa = 0.0),
        )
        assertEquals(PowerPath.BYPASS_LIMIT, resting)
        assertEquals(PowerPath.BYPASS_LIMIT, reportedFull)
        val residual = PowerPathDetector.detect(
            plugged(status = ChargeStatus.NOT_CHARGING, level = 80, currentMa = 190.0),
        )
        assertEquals(PowerPath.BYPASS_LIMIT, residual)
    }

    @Test
    fun activeChargeAndThermalPauseStayDistinct() {
        val charging = PowerPathDetector.detect(
            plugged(status = ChargeStatus.CHARGING, level = 80, currentMa = 1800.0),
        )
        val missing = PowerPathDetector.detect(
            plugged(status = ChargeStatus.CHARGING, level = 40, currentMa = 0.0),
        )
        val hot = PowerPathDetector.detect(
            plugged(
                status = ChargeStatus.NOT_CHARGING,
                level = 80,
                currentMa = 0.0,
                condition = BatteryCondition.OVERHEAT,
            ),
        )
        assertEquals(PowerPath.CHARGING, charging)
        assertEquals(PowerPath.CHARGING, missing)
        assertEquals(PowerPath.PAUSED, hot)
        assertFalse(PowerPath.CHARGING.isAdapterPowered)
        assertTrue(PowerPath.BYPASS_LIMIT.isAdapterPowered)
    }

    @Test
    fun smoothingFollowsCurrentWithoutMixingChargeAndDischarge() {
        val first = PowerMath.smoothCurrentMa(null, 1000.0)
        val second = PowerMath.smoothCurrentMa(first, 2000.0)
        assertEquals(1350.0, second!!, 0.01)
        val flipped = PowerMath.smoothCurrentMa(second, -500.0)
        assertEquals(-500.0, flipped!!, 0.01)
        val stopped = PowerMath.displayedCurrentMa(second, 190.0, charging = false, wasCharging = true)
        assertEquals(190.0, stopped!!, 0.01)
    }

    @Test
    fun wattsAreVoltsTimesAmps() {
        assertEquals(18.0, PowerMath.watts(4500, 4000.0)!!, 0.001)
        assertNull(PowerMath.watts(null, 4000.0))
    }

    @Test
    fun powerScaleCorrectsACurrentReportedInTheWrongUnit() {
        val asMilliAmps = PowerMath.normalizeCurrentMa(1_500, ChargeStatus.CHARGING, scale = 1000.0)
        assertEquals(1500.0, asMilliAmps!!, 0.01)
        val tooLarge = PowerMath.normalizeCurrentMa(1_500_000, ChargeStatus.CHARGING, scale = 0.001)
        assertEquals(1.5, tooLarge!!, 0.01)
        val unchanged = PowerMath.normalizeCurrentMa(1_500_000, ChargeStatus.CHARGING, scale = 1.0)
        assertEquals(1500.0, unchanged!!, 0.01)
    }

    @Test
    fun chargingSignIsCorrectedWhenTheKernelDisagrees() {
        val milliamps = PowerMath.normalizeCurrentMa(-1_500_000, ChargeStatus.CHARGING)
        assertEquals(1500.0, milliamps!!, 0.001)
        val discharge = PowerMath.normalizeCurrentMa(800_000, ChargeStatus.DISCHARGING)
        assertEquals(-800.0, discharge!!, 0.001)
    }

    @Test
    fun energySkipsMissingPowerAndLongGaps() {
        assertEquals(0.0, PowerMath.energyWh(null, 2_000, 10.0), 0.0)
        assertEquals(10.0 / 3600.0, PowerMath.energyWh(0, 1_000, 10.0), 0.0001)
        assertEquals(10.0 * 31_000 / 3_600_000.0, PowerMath.energyWh(0, 31_000, 10.0), 0.0001)
        assertEquals(0.0, PowerMath.energyWh(0, 120_000, 10.0), 0.0)
        assertEquals(0.0, PowerMath.energyWh(0, 3_700_000, 10.0), 0.0)
    }

    @Test
    fun healthUsesFullOverDesignAndRejectsBrokenUnits() {
        val percent = PowerMath.healthPercent(3_874_000, 4_326_000)
        assertEquals(89.55, percent!!, 0.01)
        assertNull(PowerMath.healthPercent(4_500, 4_500_000))
    }

    @Test
    fun wattsUseOneSourcePair() {
        val system = publicBattery(currentMicroamps = 2_000_000)
        val extra = extras(current = 1_500_000)
        val merged = SnapshotMerger.merge(system, extra, ShizukuAvailability.READY, false, 10)
        assertEquals(1500.0, merged.batteryCurrentMa!!, 0.01)
        assertEquals(ReadingSource.FUEL_GAUGE, merged.batteryCurrentSource)
        assertEquals(ReadingSource.FUEL_GAUGE, merged.batteryVoltageSource)
        assertEquals(4400, merged.batteryVoltageMv)
        assertEquals(6.6, merged.batteryPowerW!!, 0.01)
        assertEquals(4500, merged.systemVoltageMv)
        assertEquals(2000.0, merged.systemCurrentMa!!, 0.01)
        assertEquals(4400, merged.fuelVoltageMv)
        assertEquals(1500.0, merged.fuelCurrentMa!!, 0.01)

        val androidPair = SnapshotMerger.merge(
            system,
            extra.copy(batteryVoltageMicrovolts = null),
            ShizukuAvailability.READY,
            false,
            10,
        )
        assertEquals(ReadingSource.SYSTEM, androidPair.batteryVoltageSource)
        assertEquals(ReadingSource.SYSTEM, androidPair.batteryCurrentSource)
        assertEquals(9.0, androidPair.batteryPowerW!!, 0.01)

        val missing = SnapshotMerger.merge(
            system.copy(currentMicroamps = null),
            extra,
            ShizukuAvailability.READY,
            false,
            10,
        )
        assertEquals(ReadingSource.FUEL_GAUGE, missing.batteryCurrentSource)
        assertEquals(1500.0, missing.batteryCurrentMa!!, 0.01)
    }

    @Test
    fun zeroCurrentWhileChargingFallsBackToTheFuelGauge() {
        val merged = SnapshotMerger.merge(
            publicBattery(currentMicroamps = 0, level = 40),
            extras(current = 1_800_000),
            ShizukuAvailability.READY,
            false,
            10,
        )
        assertEquals(ReadingSource.FUEL_GAUGE, merged.batteryCurrentSource)
        assertEquals(ReadingSource.FUEL_GAUGE, merged.batteryVoltageSource)
        assertEquals(7.92, merged.batteryPowerW!!, 0.01)
    }

    @Test
    fun capacityAndChargerSideComeOnlyFromPrivilegedData() {
        val text = """
            ---SYSFS---
            battery voltage_now 4321000
            battery current_now 1500000
            battery charge_full 3874000
            battery charge_full_design 4326000
            battery cycle_count 941
            battery health_index 100
            usb voltage_now 5000000
            usb current_now 1800000
            ---DUMPSYS---
            Current Battery Service state:
              Max charging current: 5000000
              Max charging voltage: 9000000
        """.trimIndent()
        val built = PrivilegedExtrasBuilder.build(
            SysfsDumpParser.parse(text),
            DumpsysBatteryParser.parse(text),
        )
        val merged = SnapshotMerger.merge(
            publicBattery(currentMicroamps = null, voltageMv = 4321, cycle = null),
            built,
            ShizukuAvailability.READY,
            false,
            10,
        )
        assertEquals(89, merged.healthPercent!!.toInt())
        assertEquals(941, merged.cycleCount)
        assertEquals(ReadingSource.FUEL_GAUGE, merged.cycleSource)
        assertEquals("usb", merged.inputName)
        assertEquals(9.0, merged.inputPowerW!!, 0.05)
        assertEquals(45.0, merged.chargerLimitWatts!!, 0.1)
        assertEquals(100.0, merged.fuelGaugeHealthIndex!!, 0.01)
    }

    @Test
    fun systemCycleCountIsKeptWhenAndroidAlreadyHasIt() {
        val merged = SnapshotMerger.merge(
            publicBattery(cycle = 12),
            extras(current = 1_000).copy(cycleCount = 99),
            ShizukuAvailability.READY,
            false,
            10,
        )
        assertEquals(12, merged.cycleCount)
        assertEquals(ReadingSource.SYSTEM, merged.cycleSource)
    }

    @Test
    fun batterystatsParserReadsUidRows() {
        val dump = """
            Estimated power use (mAh):
              Capacity: 4380, Computed drain: 210.5, actual drain: 200
              Screen: 80.0
              Uid u0a123: 40.5 (cpu=20)
              Uid 1000: 10

            Next section
              ignored: 5
        """.trimIndent()
        val report = BatterystatsParser.parse(dump)
        assertEquals(210.5, report.computedDrainMah!!, 0.01)
        assertEquals(3, report.entries.size)
        assertEquals("Screen", report.entries[0].label)
        assertEquals(10123, report.entries[1].uid)
        assertEquals(1000, report.entries[2].uid)
    }

    @Test
    fun slowAlertWaitsForTheConfiguredDuration() {
        val settings = AlertSettings(
            heatEnabled = false,
            heatCelsius = 45.0,
            slowEnabled = true,
            slowWatts = 5.0,
            slowLevelBelow = 90,
            slowAfterMillis = 180_000,
            healthEnabled = false,
            healthPercent = 80.0,
        )
        val charging = snapshot(power = 2.0, level = 50, status = ChargeStatus.CHARGING, plugged = PlugType.USB)
        val first = AlertEvaluator.tick(charging, settings, AlertMemory(), 0)
        assertTrue(first.alerts.isEmpty())
        val second = AlertEvaluator.tick(charging, settings, first.memory, 180_000)
        assertEquals(AlertType.SLOW, second.alerts.single().type)
        val third = AlertEvaluator.tick(charging, settings, second.memory, 180_001)
        assertTrue(third.alerts.isEmpty())
    }

    @Test
    fun heatAlertFiresOncePerCooldown() {
        val settings = AlertSettings(true, 45.0, false, 5.0, 90, 60_000, false, 80.0)
        val hot = snapshot(power = 10.0, level = 50, status = ChargeStatus.CHARGING, plugged = PlugType.USB)
            .copy(temperatureC = 46.2)
        val first = AlertEvaluator.tick(hot, settings, AlertMemory(), 1_000)
        assertEquals(AlertType.HEAT, first.alerts.single().type)
        val second = AlertEvaluator.tick(hot, settings, first.memory, 2_000)
        assertTrue(second.alerts.isEmpty())
    }

    private fun publicBattery(
        currentMicroamps: Int? = 1_000_000,
        level: Int = 60,
        voltageMv: Int? = 4500,
        cycle: Int? = 4,
    ) = PublicBattery(
        levelPercent = level,
        status = ChargeStatus.CHARGING,
        plugged = PlugType.USB,
        health = BatteryCondition.GOOD,
        technology = "Li-ion",
        temperatureC = 31.0,
        voltageMv = voltageMv,
        currentMicroamps = currentMicroamps,
        chargeCounterUah = 2_500_000,
        cycleCount = cycle,
        timeToFullMillis = 3_600_000,
    )

    private fun extras(current: Int) = PrivilegedExtras(
        batteryCurrentMicroamps = current,
        batteryVoltageMicrovolts = 4_400_000,
        chargeFullRaw = 4_000_000,
        chargeFullDesignRaw = 4_500_000,
        cycleCount = 20,
        fuelGaugeHealthIndex = 90.0,
        inputName = "usb",
        inputVoltageMicrovolts = 5_000_000,
        inputCurrentMicroamps = 1_000_000,
        limitVoltageMicrovolts = null,
        limitCurrentMicroamps = null,
    )

    private fun plugged(
        status: ChargeStatus,
        level: Int,
        currentMa: Double?,
        condition: BatteryCondition = BatteryCondition.GOOD,
    ) = BatterySnapshot.Empty.copy(
        timestampMillis = 1L,
        status = status,
        plugged = PlugType.USB,
        levelPercent = level,
        batteryCurrentMa = currentMa,
        condition = condition,
    )

    private fun snapshot(
        power: Double,
        level: Int,
        status: ChargeStatus,
        plugged: PlugType,
    ) = BatterySnapshot.Empty.copy(
        timestampMillis = 1,
        levelPercent = level,
        status = status,
        plugged = plugged,
        batteryPowerW = power,
        temperatureC = 30.0,
    )
}
