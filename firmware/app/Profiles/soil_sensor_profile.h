/*
 * soil_sensor_profile.h
 *
 * Custom GATT profile for the soil moisture sensor. Modeled on TI's
 * simple_gatt_profile sample.
 *
 * Characteristics:
 *   CURRENT_READING - notify+read, 8 bytes (SoilSensor_Record_t): the
 *                      most recent sample.
 *   HISTORY_COUNT   - read, uint16: number of samples stored on-device.
 *   HISTORY_INDEX   - write, uint16: index (oldest-first) of the sample
 *                      the client wants to fetch next.
 *   HISTORY_RECORD  - read, 8 bytes: the sample at the last index
 *                      written to HISTORY_INDEX.
 *   HISTORY_BASE_SEQ - read, uint32: absolute sequence number of the
 *                      oldest sample currently in HISTORY_INDEX 0. Sequence
 *                      numbers count up forever from boot (never reset by
 *                      the ring buffer wrapping), so a client can persist
 *                      "last sequence synced" and compute which index to
 *                      resume from instead of re-reading the whole history
 *                      on every reconnect.
 *
 * Firmware version is reported separately over the standard Device
 * Information Service (0x180A / Firmware Revision String 0x2A26, TI's
 * bundled dev_info_service - see app/Profiles/app_dev_info.c) rather than
 * a characteristic here, for the same reason battery level uses a BLE
 * SIG service instead of a custom one.
 *
 * Battery level is reported separately over the standard BLE Battery
 * Service (0x180F / 0x2A19, see Profiles/battery_service.h) rather than a
 * characteristic on this custom service, since it's a BLE SIG-adopted
 * profile that clients (including Android's own BLE stack) already know
 * how to recognize.
 */

#ifndef SOILSENSORPROFILE_H
#define SOILSENSORPROFILE_H

#ifdef __cplusplus
extern "C"
{
#endif

#include "soil_history.h"

// Profile Parameters
#define SOILSENSORPROFILE_CURRENT_READING   0
#define SOILSENSORPROFILE_HISTORY_COUNT     1
#define SOILSENSORPROFILE_HISTORY_INDEX     2
#define SOILSENSORPROFILE_HISTORY_RECORD    3
#define SOILSENSORPROFILE_HISTORY_BASE_SEQ  4

// Custom 128-bit UUIDs, built on TI's base (F000XXXX-0451-4000-B000-000000000000)
#define SOILSENSORPROFILE_SERV_UUID             0xAA00
#define SOILSENSORPROFILE_CURRENT_READING_UUID  0xAA01
#define SOILSENSORPROFILE_HISTORY_COUNT_UUID    0xAA02
#define SOILSENSORPROFILE_HISTORY_INDEX_UUID    0xAA03
#define SOILSENSORPROFILE_HISTORY_RECORD_UUID   0xAA04
#define SOILSENSORPROFILE_HISTORY_BASE_SEQ_UUID 0xAA05

#define SOILSENSORPROFILE_RECORD_LEN  sizeof(SoilSensor_Record_t)

/*
 * Registers the GATT attribute table with the GATT server. Call once
 * after the BLE stack has finished initializing.
 */
bStatus_t SoilSensorProfile_addService(void);

/*
 * Writes a profile parameter's backing value. For CURRENT_READING this
 * also triggers a notification if the connected client has subscribed.
 */
bStatus_t SoilSensorProfile_setParameter(uint8_t param, uint8_t len, void *value);

bStatus_t SoilSensorProfile_getParameter(uint8_t param, void *value);

#ifdef __cplusplus
}
#endif

#endif /* SOILSENSORPROFILE_H */
