package com.guarecuco.soilsensor.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * uptimeSec is the firmware's device-boot-relative clock, unique within a boot
 * session but not across reboots (it resets to 0). Combined with the reading
 * values and the source device address, this is effectively unique per
 * physical sample, so it is used as a natural dedupe key instead of tracking
 * a fragile "last synced index" against the device's ring buffer (whose slot
 * numbering shifts as old samples get evicted).
 *
 * deviceAddress lets multiple physical sensors share one local database
 * without their histories mixing together.
 */
@Entity(
    tableName = "soil_readings",
    indices = [Index(value = ["deviceAddress", "uptimeSec", "moistureRaw", "tempCentiC"], unique = true)],
)
data class SoilReadingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deviceAddress: String,
    val timestampMillis: Long,
    val uptimeSec: Long,
    val moistureRaw: Int,
    val tempCentiC: Int,
)
