/*
 * soil_sensor.c
 *
 * I2C driver for the Adafruit seesaw STEMMA soil moisture sensor.
 *
 * Protocol: write a 2-byte [module_base, function] register pair, wait
 * for the seesaw co-processor to prepare the response, then read the
 * result in a separate I2C transaction. The delay between write and
 * read is required by the seesaw firmware and can't be folded into a
 * single repeated-start transfer.
 */

#include <stddef.h>

#include <ti/drivers/I2C.h>
#include <ti/drivers/dpl/ClockP.h>

#include "soil_sensor.h"
#include "ti_drivers_config.h"

#define SEESAW_STATUS_BASE          0x00
#define SEESAW_STATUS_TEMP          0x04
#define SEESAW_TOUCH_BASE           0x0F
#define SEESAW_TOUCH_CHANNEL_OFFSET 0x10
#define SEESAW_MOISTURE_CHANNEL     0

/*
 * The seesaw's ATSAMD10 reads its own die temperature, which runs a few
 * degrees above ambient from self-heating (a known characteristic, not a
 * bug in this conversion). To calibrate: compare a live reading against a
 * reference thermometer and set this to (reference - reported), in
 * hundredths of a degree, e.g. -400 if the sensor reads 4.00C high.
 */
#define SOIL_TEMP_CALIBRATION_OFFSET_CENTIC (-400) /* measured: sensor read 28.3C against a 24.3C reference */

static I2C_Handle i2cHandle = NULL;

static bool writeThenRead(const uint8_t *writeBuf, size_t writeCount,
                           uint8_t *readBuf, size_t readCount,
                           uint32_t delayUsec)
{
    I2C_Transaction transaction = {0};

    transaction.targetAddress = SOIL_SENSOR_I2C_ADDRESS;
    transaction.writeBuf = (void *)writeBuf;
    transaction.writeCount = writeCount;
    transaction.readBuf = NULL;
    transaction.readCount = 0;

    if (!I2C_transfer(i2cHandle, &transaction))
    {
        return false;
    }

    ClockP_usleep(delayUsec);

    transaction.writeBuf = NULL;
    transaction.writeCount = 0;
    transaction.readBuf = readBuf;
    transaction.readCount = readCount;

    return I2C_transfer(i2cHandle, &transaction);
}

bool SoilSensor_init(void)
{
    I2C_Params i2cParams;
    uint8_t probeReg[2] = {SEESAW_STATUS_BASE, SEESAW_STATUS_TEMP};
    uint8_t probeResp[4];

    I2C_Params_init(&i2cParams);
    i2cParams.bitRate = I2C_400kHz;

    i2cHandle = I2C_open(CONFIG_I2C_0, &i2cParams);
    if (i2cHandle == NULL)
    {
        return false;
    }

    /* Probe: a temperature read confirms the sensor is present and wired correctly */
    return writeThenRead(probeReg, sizeof(probeReg), probeResp, sizeof(probeResp), 1000);
}

bool SoilSensor_read(uint16_t *moistureRaw, int16_t *tempCentiC)
{
    uint8_t moistureReg[2] = {SEESAW_TOUCH_BASE, SEESAW_TOUCH_CHANNEL_OFFSET + SEESAW_MOISTURE_CHANNEL};
    uint8_t moistureResp[2];
    uint8_t tempReg[2] = {SEESAW_STATUS_BASE, SEESAW_STATUS_TEMP};
    uint8_t tempResp[4];
    int32_t tempRaw;

    if (i2cHandle == NULL)
    {
        return false;
    }

    if (!writeThenRead(moistureReg, sizeof(moistureReg), moistureResp, sizeof(moistureResp), 3000))
    {
        return false;
    }
    *moistureRaw = ((uint16_t)moistureResp[0] << 8) | moistureResp[1];

    if (!writeThenRead(tempReg, sizeof(tempReg), tempResp, sizeof(tempResp), 1000))
    {
        return false;
    }
    tempRaw = ((uint32_t)tempResp[0] << 24) | ((uint32_t)tempResp[1] << 16) |
              ((uint32_t)tempResp[2] << 8) | (uint32_t)tempResp[3];
    /* seesaw returns Q16.16 fixed point Celsius; convert to hundredths of a degree */
    *tempCentiC = (int16_t)(((int64_t)tempRaw * 100) / 65536) + SOIL_TEMP_CALIBRATION_OFFSET_CENTIC;

    return true;
}

I2C_Handle SoilSensor_getI2CHandle(void)
{
    return i2cHandle;
}
