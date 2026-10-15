package com.chargegauge.data

import com.chargegauge.data.db.ChargeDao
import com.chargegauge.data.db.ChargeSessionEntity
import com.chargegauge.data.db.SampleEntity
import com.chargegauge.domain.BatterySnapshot
import com.chargegauge.domain.PowerMath
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SessionRecorder(private val dao: ChargeDao) {
    private val mutex = Mutex()
    private var loaded = false
    private var open: ChargeSessionEntity? = null
    private var lastTimestamp: Long? = null
    private var energyWh: Double = 0.0
    private var peakWatts: Double = 0.0
    private var sampleCount: Int = 0
    private var maxTempC: Double? = null

    suspend fun onSnapshot(snapshot: BatterySnapshot, intervalMillis: Long) {
        mutex.withLock {
            ensureLoaded()
            if (!snapshot.isPlugged) {
                close(snapshot)
                return
            }
            val session = open ?: start(snapshot)
            val now = snapshot.timestampMillis
            val previous = lastTimestamp
            if (previous != null && now - previous < (intervalMillis * 0.8).toLong()) return
            energyWh += PowerMath.energyWh(previous, now, snapshot.batteryPowerW)
            val watts = snapshot.batteryPowerW
            if (watts != null && watts > peakWatts) peakWatts = watts
            val temperature = snapshot.temperatureC
            if (temperature != null && (maxTempC == null || temperature > maxTempC!!)) {
                maxTempC = temperature
            }
            sampleCount += 1
            lastTimestamp = now
            val hours = (now - session.startedAt) / 3_600_000.0
            val average = if (hours > 0.0 && energyWh > 0.0) energyWh / hours else null
            val updated = session.copy(
                endLevel = snapshot.levelPercent,
                peakWatts = peakWatts.takeIf { it > 0.0 },
                averageWatts = average,
                energyWh = energyWh,
                maxTempC = maxTempC,
                sampleCount = sampleCount,
            )
            open = updated
            dao.insertSample(
                SampleEntity(
                    sessionId = updated.id,
                    timestamp = now,
                    voltageMv = snapshot.batteryVoltageMv,
                    currentMa = snapshot.batteryCurrentMa,
                    watts = snapshot.batteryPowerW,
                    level = snapshot.levelPercent,
                    tempC = snapshot.temperatureC,
                ),
            )
            dao.updateSession(updated)
        }
    }

    suspend fun closeIfUnplugged(snapshot: BatterySnapshot) {
        if (!snapshot.isPlugged) onSnapshot(snapshot, intervalMillis = 1L)
    }

    private suspend fun ensureLoaded() {
        if (loaded) return
        loaded = true
        val existing = dao.openSession() ?: return
        val last = dao.lastSample(existing.id)
        val anchor = last?.timestamp ?: existing.startedAt
        if (System.currentTimeMillis() - anchor > GAP_MILLIS) {
            dao.updateSession(
                existing.copy(
                    endedAt = anchor,
                    endLevel = last?.level ?: existing.endLevel,
                ),
            )
            return
        }
        open = existing
        energyWh = existing.energyWh
        peakWatts = existing.peakWatts ?: 0.0
        sampleCount = existing.sampleCount
        maxTempC = existing.maxTempC
        lastTimestamp = last?.timestamp
    }

    private suspend fun start(snapshot: BatterySnapshot): ChargeSessionEntity {
        val created = ChargeSessionEntity(
            startedAt = snapshot.timestampMillis,
            endedAt = null,
            startLevel = snapshot.levelPercent,
            endLevel = snapshot.levelPercent,
            plugType = snapshot.plugged.name,
            peakWatts = null,
            averageWatts = null,
            energyWh = 0.0,
            maxTempC = snapshot.temperatureC,
            sampleCount = 0,
        )
        val stored = created.copy(id = dao.insertSession(created))
        open = stored
        energyWh = 0.0
        peakWatts = 0.0
        sampleCount = 0
        maxTempC = snapshot.temperatureC
        lastTimestamp = null
        return stored
    }

    private suspend fun close(snapshot: BatterySnapshot) {
        val session = open ?: return
        dao.updateSession(
            session.copy(
                endedAt = snapshot.timestampMillis,
                endLevel = snapshot.levelPercent ?: session.endLevel,
            ),
        )
        open = null
        lastTimestamp = null
        energyWh = 0.0
        peakWatts = 0.0
        sampleCount = 0
        maxTempC = null
    }

    private companion object {
        const val GAP_MILLIS = 10L * 60L * 1000L
    }
}
