/*
 * soil_sensor.h
 *
 * I2C driver for the Adafruit seesaw STEMMA soil moisture sensor.
 */

#ifndef SOIL_SENSOR_H_
#define SOIL_SENSOR_H_

#include <stdbool.h>
#include <stdint.h>

/* Default I2C address (selectable 0x36-0x39 via the sensor's AD0/AD1 solder jumpers) */
#define SOIL_SENSOR_I2C_ADDRESS 0x36

/*
 * Opens the I2C bus used to talk to the sensor. Must be called once
 * before SoilSensor_read(). Returns false if the sensor did not
 * respond (check wiring / address).
 */
bool SoilSensor_init(void);

/*
 * Reads one sample from the sensor.
 *
 * moistureRaw - capacitive touch reading, roughly 200 (dry) to 2000 (wet)
 * tempCentiC  - ambient temperature in hundredths of a degree Celsius
 *               (e.g. 2345 == 23.45 C)
 *
 * Returns false if either I2C transaction failed.
 */
bool SoilSensor_read(uint16_t *moistureRaw, int16_t *tempCentiC);

#endif /* SOIL_SENSOR_H_ */
