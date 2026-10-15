package com.chargegauge.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ChargeDao {
    @Query("SELECT * FROM sessions WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    suspend fun openSession(): ChargeSessionEntity?

    @Query("SELECT * FROM sessions ORDER BY startedAt DESC")
    fun observeSessions(): Flow<List<ChargeSessionEntity>>

    @Query("SELECT * FROM sessions WHERE id = :id")
    fun observeSession(id: Long): Flow<ChargeSessionEntity?>

    @Query("SELECT * FROM samples WHERE sessionId = :id ORDER BY timestamp ASC")
    fun observeSamples(id: Long): Flow<List<SampleEntity>>

    @Query("SELECT * FROM samples WHERE sessionId = :id ORDER BY timestamp DESC LIMIT 1")
    suspend fun lastSample(id: Long): SampleEntity?

    @Insert
    suspend fun insertSession(session: ChargeSessionEntity): Long

    @Update
    suspend fun updateSession(session: ChargeSessionEntity)

    @Insert
    suspend fun insertSample(sample: SampleEntity)

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteSession(id: Long)

    @Query("SELECT * FROM sessions ORDER BY startedAt ASC")
    suspend fun allSessions(): List<ChargeSessionEntity>

    @Query("SELECT * FROM samples ORDER BY timestamp ASC")
    suspend fun allSamples(): List<SampleEntity>

    @Insert
    suspend fun insertCapacity(snapshot: CapacitySnapshotEntity)

    @Query("SELECT * FROM capacity_snapshots ORDER BY timestamp ASC")
    fun observeCapacity(): Flow<List<CapacitySnapshotEntity>>

    @Query("SELECT * FROM capacity_snapshots ORDER BY timestamp DESC LIMIT 1")
    suspend fun latestCapacity(): CapacitySnapshotEntity?
}
