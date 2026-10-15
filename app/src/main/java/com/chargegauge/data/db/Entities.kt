package com.chargegauge.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class ChargeSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val endedAt: Long?,
    val startLevel: Int?,
    val endLevel: Int?,
    val plugType: String,
    val peakWatts: Double?,
    val averageWatts: Double?,
    val energyWh: Double,
    val maxTempC: Double?,
    val sampleCount: Int,
)

@Entity(
    tableName = "samples",
    foreignKeys = [
        ForeignKey(
            entity = ChargeSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId")],
)
data class SampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val timestamp: Long,
    val voltageMv: Int?,
    val currentMa: Double?,
    val watts: Double?,
    val level: Int?,
    val tempC: Double?,
)

@Entity(tableName = "capacity_snapshots")
data class CapacitySnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val designMah: Double?,
    val fullMah: Double?,
    val healthPercent: Double?,
    val cycleCount: Int?,
)
