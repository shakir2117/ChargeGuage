package com.chargegauge.data

import com.chargegauge.data.db.CapacitySnapshotEntity
import com.chargegauge.data.db.ChargeDao
import com.chargegauge.domain.BatterySnapshot
import com.chargegauge.domain.ChargeStatus
import com.chargegauge.domain.PowerMath
import com.chargegauge.domain.PrivilegedExtras
import com.chargegauge.domain.ShizukuAvailability
import com.chargegauge.domain.SnapshotMerger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.abs

class BatteryRepository(
    private val system: SystemBatteryDataSource,
    private val gateway: ShizukuGateway,
    private val dao: ChargeDao,
) {
    private val mutex = Mutex()
    private val snapshotState = MutableStateFlow(BatterySnapshot.Empty)
    val snapshot: StateFlow<BatterySnapshot> = snapshotState.asStateFlow()

    private var cachedExtras: PrivilegedExtras? = null
    private var cachedAtMillis: Long = 0L

    suspend fun refreshOnce(): BatterySnapshot = withContext(Dispatchers.IO) {
        mutex.withLock {
            val public = system.read()
            val shizuku = gateway.availability()
            val now = System.currentTimeMillis()
            val weakCurrent = needsFreshFuelGauge(public.currentMicroamps, public.status, public.levelPercent)
            val maxAge = if (weakCurrent) FAST_CACHE_MILLIS else SLOW_CACHE_MILLIS
            var readFailed = false
            if (shizuku == ShizukuAvailability.READY &&
                (cachedExtras == null || now - cachedAtMillis > maxAge)
            ) {
                val extras = gateway.readExtras()
                if (extras != null) {
                    cachedExtras = extras
                    cachedAtMillis = now
                } else if (cachedExtras == null) {
                    readFailed = true
                }
            }
            if (shizuku != ShizukuAvailability.READY) {
                cachedExtras = null
                readFailed = false
            }
            val snapshot = SnapshotMerger.merge(
                public = public,
                extra = if (shizuku == ShizukuAvailability.READY) cachedExtras else null,
                shizuku = shizuku,
                fuelGaugeReadFailed = readFailed,
                now = now,
            )
            snapshotState.value = snapshot
            maybeRecordCapacity(snapshot)
            snapshot
        }
    }

    private fun needsFreshFuelGauge(
        currentMicroamps: Int?,
        status: ChargeStatus,
        levelPercent: Int?,
    ): Boolean {
        if (currentMicroamps == null) return true
        val milliamps = PowerMath.microampsToMa(currentMicroamps) ?: return true
        return status == ChargeStatus.CHARGING &&
            abs(milliamps) < 1.0 &&
            (levelPercent ?: 0) < 95
    }

    private suspend fun maybeRecordCapacity(snapshot: BatterySnapshot) {
        val health = snapshot.healthPercent ?: return
        val latest = dao.latestCapacity()
        if (latest != null) {
            val elapsed = snapshot.timestampMillis - latest.timestamp
            val delta = abs((latest.healthPercent ?: health) - health)
            if (elapsed < CAPACITY_GAP_MILLIS && delta < 0.5) return
        }
        dao.insertCapacity(
            CapacitySnapshotEntity(
                timestamp = snapshot.timestampMillis,
                designMah = snapshot.designCapacityMah,
                fullMah = snapshot.fullCapacityMah,
                healthPercent = health,
                cycleCount = snapshot.cycleCount,
            ),
        )
    }

    private companion object {
        const val FAST_CACHE_MILLIS = 2_000L
        const val SLOW_CACHE_MILLIS = 60_000L
        const val CAPACITY_GAP_MILLIS = 20L * 60L * 60L * 1000L
    }
}
