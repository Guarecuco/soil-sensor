package com.guarecuco.soilsensor.ble

import java.util.UUID

/**
 * Must match app/Profiles/soil_sensor_profile.h in the firmware project:
 * custom 128-bit UUIDs built on TI's base F000XXXX-0451-4000-B000-000000000000.
 */
object BleUuids {
    private fun tiUuid(shortId: Int): UUID =
        UUID.fromString("f000%04x-0451-4000-b000-000000000000".format(shortId))

    val SERVICE: UUID = tiUuid(0xAA00)
    val CURRENT_READING: UUID = tiUuid(0xAA01)
    val HISTORY_COUNT: UUID = tiUuid(0xAA02)
    val HISTORY_INDEX: UUID = tiUuid(0xAA03)
    val HISTORY_RECORD: UUID = tiUuid(0xAA04)
    val HISTORY_BASE_SEQ: UUID = tiUuid(0xAA05)

    val CLIENT_CHARACTERISTIC_CONFIG: UUID =
        UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    const val DEVICE_NAME = "SoilSensor"
}
