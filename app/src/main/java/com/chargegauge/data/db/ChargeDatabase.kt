package com.chargegauge.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ChargeSessionEntity::class,
        SampleEntity::class,
        CapacitySnapshotEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class ChargeDatabase : RoomDatabase() {
    abstract fun chargeDao(): ChargeDao

    companion object {
        fun create(context: Context): ChargeDatabase {
            return Room.databaseBuilder(context, ChargeDatabase::class.java, "chargegauge.db")
                .build()
        }
    }
}
