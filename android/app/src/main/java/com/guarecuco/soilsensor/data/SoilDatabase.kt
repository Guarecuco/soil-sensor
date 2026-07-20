package com.guarecuco.soilsensor.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [SoilReadingEntity::class], version = 2, exportSchema = false)
abstract class SoilDatabase : RoomDatabase() {
    abstract fun soilReadingDao(): SoilReadingDao

    companion object {
        @Volatile private var instance: SoilDatabase? = null

        fun getInstance(context: Context): SoilDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    SoilDatabase::class.java,
                    "soil_sensor.db",
                )
                    // Pre-release app, no installed base to migrate yet.
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build().also { instance = it }
            }
    }
}
