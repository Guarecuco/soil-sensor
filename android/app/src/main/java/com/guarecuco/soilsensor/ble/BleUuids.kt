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

    // Standard BLE SIG Battery Service (0x180F) with the Battery Level
    // (0x2A19) and Battery Power State (0x2A1A) characteristics - real BLE
    // SIG UUIDs, not custom TI-base ones. See firmware's
    // Profiles/battery_service.h.
    private fun sigUuid(shortId: Int): UUID =
        UUID.fromString("%08x-0000-1000-8000-00805f9b34fb".format(shortId))

    val BATTERY_SERVICE: UUID = sigUuid(0x0000180F)
    val BATTERY_LEVEL: UUID = sigUuid(0x00002A19)
    val BATTERY_POWER_STATE: UUID = sigUuid(0x00002A1A)

    // Standard Device Information Service (0x180A), Firmware Revision
    // String only (0x2A26) - see firmware's app/Profiles/app_soil_sensor.c
    // (SoilSensor_reportFwVersion) and TI's bundled dev_info_service.
    val DEVICE_INFO_SERVICE: UUID = sigUuid(0x0000180A)
    val FIRMWARE_REVISION: UUID = sigUuid(0x00002A26)

    val CLIENT_CHARACTERISTIC_CONFIG: UUID =
        UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    const val DEVICE_NAME = "SoilSensor"

    // TI on-chip OAD (firmware update) UUIDs - see firmware's
    // Profiles/app_oad.c and the SDK's oad_service.h / oad_reset_service.h.
    // Reset Service lives in the currently-running app; the OAD transfer
    // service only appears after rebooting into the "Persistent_app" image.
    val OAD_RESET_SERVICE: UUID = tiUuid(0xFFD0)
    val OAD_RESET_CHAR: UUID = tiUuid(0xFFD1)

    val OAD_SERVICE: UUID = tiUuid(0xFFC0)
    val OAD_IMG_IDENTIFY: UUID = tiUuid(0xFFC1)
    val OAD_IMG_BLOCK: UUID = tiUuid(0xFFC2)
    val OAD_EXT_CTRL: UUID = tiUuid(0xFFC5)

    const val PERSISTENT_DEVICE_NAME = "Persistent_app"
}
