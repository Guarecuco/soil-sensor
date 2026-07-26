/*
 * battery_gauge.h
 *
 * I2C driver for the bq27441-G1 fuel gauge on the BOOSTXL-BATPAKMKII
 * BoosterPack.
 */

#ifndef BATTERY_GAUGE_H_
#define BATTERY_GAUGE_H_

#include <stdbool.h>
#include <stdint.h>

#include <ti/drivers/I2C.h>

/* bq27441-G1 7-bit I2C address (fixed, not configurable via jumpers) */
#define BATTERY_GAUGE_I2C_ADDRESS 0x55

/* Flags() bit: set when the gauge has never completed its initial
 * power-on/learning cycle (Initial Time Power-On Reset). */
#define BATTERY_GAUGE_FLAG_ITPOR 0x0020

/* Flags() bit: set once the gauge considers a battery present (via the
 * BIN pin or a soft BAT_INSERT command - see BatteryGauge_init()). */
#define BATTERY_GAUGE_FLAG_BAT_DET 0x0008

/* Flags() bit: set while the gauge detects net discharge current. */
#define BATTERY_GAUGE_FLAG_DSG 0x0001

/* OpConfig bit: Battery Insertion Enable. When set (factory default), the
 * gauge only trusts the BIN pin for battery detection and ignores the
 * soft BAT_INSERT command entirely. */
#define BATTERY_GAUGE_OPCONFIG_BIE 0x2000

/*
 * Attaches to an already-open I2C bus handle rather than opening its own -
 * the BoosterPack stack has one shared set of I2C pins, so this shares the
 * bus opened by SoilSensor_init() (see SoilSensor_getI2CHandle()). Returns
 * false if the fuel gauge did not respond (check that the BATPAKMKII is
 * attached).
 */
bool BatteryGauge_init(I2C_Handle i2cHandle);

/*
 * Reads the fuel gauge's StateOfCharge() standard command: remaining
 * capacity as a percentage (0-100) of FullChargeCapacity(). This is the
 * chip's own Impedance Track estimate, not a raw voltage reading.
 */
bool BatteryGauge_read(uint8_t *percent);

/*
 * Diagnostic reads for the other standard commands - not used for the
 * BLE-reported percentage, only for UART debug output while chasing why
 * StateOfCharge() reads 0%. Flags() bit 0x0020 is ITPOR (set on a fuel
 * gauge that has never completed its initial power-on learning cycle).
 */
bool BatteryGauge_readFlags(uint16_t *flags);
bool BatteryGauge_readVoltageMv(uint16_t *millivolts);
bool BatteryGauge_readFullChargeCapacityMah(uint16_t *milliampHours);
bool BatteryGauge_readRemainingCapacityMah(uint16_t *milliampHours);
bool BatteryGauge_readAverageCurrentMa(int16_t *milliamps);

/* OpConfig, read as a plain extended command (standard endianness) -
 * only meaningful outside CONFIG UPDATE mode. Debug-only, to confirm the
 * BIE-clear in BatteryGauge_init() actually took. */
bool BatteryGauge_readOpConfig(uint16_t *opConfig);

#endif /* BATTERY_GAUGE_H_ */
