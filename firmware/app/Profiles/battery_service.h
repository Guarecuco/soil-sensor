/*
 * battery_service.h
 *
 * Standard BLE SIG Battery Service (org.bluetooth.service.battery_service,
 * UUID 0x180F), with both the Battery Level characteristic and the
 * Battery Power State characteristic.
 *
 * TI's bundled battery_server.c (source/ti/ble/services/battery) only
 * implements Battery Level - it has no support for Power State - so this
 * is a project-owned service definition instead, following the exact same
 * GATT attribute table pattern as Profiles/soil_sensor_profile.c. Both
 * characteristics use their real BLE SIG UUIDs, not custom ones.
 */

#ifndef BATTERY_SERVICE_H
#define BATTERY_SERVICE_H

#ifdef __cplusplus
extern "C"
{
#endif

#include <stdint.h>

// Service Parameters
#define BATTERY_SERVICE_LEVEL_ID        0
#define BATTERY_SERVICE_POWER_STATE_ID  1

// Standard BLE SIG UUIDs (see the Bluetooth Assigned Numbers document)
#define BATTERY_SERVICE_SERV_UUID        0x180F
#define BATTERY_SERVICE_LEVEL_UUID       0x2A19
#define BATTERY_SERVICE_POWER_STATE_UUID 0x2A1A

/*
 * Battery Power State is one mandatory byte, packed as four 2-bit fields
 * (org.bluetooth.characteristic.battery_power_state):
 *   bits 0-1: Battery Present
 *   bits 2-3: Discharge State
 *   bits 4-5: Charging State
 *   bits 6-7: Battery Level State
 */
#define BATTERY_SERVICE_PRESENT_UNKNOWN        0
#define BATTERY_SERVICE_PRESENT_NOT_SUPPORTED  1
#define BATTERY_SERVICE_PRESENT_NOT_PRESENT    2
#define BATTERY_SERVICE_PRESENT_PRESENT        3

#define BATTERY_SERVICE_DISCHARGE_UNKNOWN         0
#define BATTERY_SERVICE_DISCHARGE_NOT_SUPPORTED   1
#define BATTERY_SERVICE_DISCHARGE_NOT_DISCHARGING 2
#define BATTERY_SERVICE_DISCHARGE_DISCHARGING     3

#define BATTERY_SERVICE_CHARGING_UNKNOWN        0
#define BATTERY_SERVICE_CHARGING_NOT_CHARGEABLE 1
#define BATTERY_SERVICE_CHARGING_NOT_CHARGING   2
#define BATTERY_SERVICE_CHARGING_CHARGING       3

#define BATTERY_SERVICE_LEVEL_STATE_UNKNOWN       0
#define BATTERY_SERVICE_LEVEL_STATE_NOT_SUPPORTED 1
#define BATTERY_SERVICE_LEVEL_STATE_GOOD          2
#define BATTERY_SERVICE_LEVEL_STATE_CRITICAL      3

#define BATTERY_SERVICE_PACK_POWER_STATE(present, discharge, charging, levelState) \
    ((uint8_t)(((present) & 0x3) | (((discharge) & 0x3) << 2) | \
               (((charging) & 0x3) << 4) | (((levelState) & 0x3) << 6)))

/*
 * Registers the GATT attribute table with the GATT server. Call once
 * after the BLE stack has finished initializing.
 */
bStatus_t BatteryService_addService(void);

/*
 * Writes a characteristic's backing value and notifies any subscribed
 * client. len must be sizeof(uint8_t) for both parameters.
 */
bStatus_t BatteryService_setParameter(uint8_t param, uint8_t len, void *value);

bStatus_t BatteryService_getParameter(uint8_t param, void *value);

#ifdef __cplusplus
}
#endif

#endif /* BATTERY_SERVICE_H */
