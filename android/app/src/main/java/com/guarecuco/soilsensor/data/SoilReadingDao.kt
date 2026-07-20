package com.guarecuco.soilsensor.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SoilReadingDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(reading: SoilReadingEntity): Long

    @Query("SELECT * FROM soil_readings WHERE deviceAddress = :deviceAddress ORDER BY timestampMillis ASC")
    fun observeForDevice(deviceAddress: String): Flow<List<SoilReadingEntity>>

    @Query("SELECT DISTINCT deviceAddress FROM soil_readings")
    fun observeKnownDevices(): Flow<List<String>>
}
