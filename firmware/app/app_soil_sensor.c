/*
 * app_soil_sensor.c
 *
 * Ties together the I2C sensor driver, the RAM history buffer and the
 * GATT profile: every 5 minutes, reads the sensor, stores the sample,
 * and notifies any subscribed BLE client.
 */

#include <stddef.h>

#include "ti/ble/app_util/framework/bleapputil_api.h"
#include "ti/ble/app_util/common/util.h"
#include "ti/ble/app_util/menu/menu_module.h"
#include <app_main.h>

#include "soil_sensor.h"
#include "soil_history.h"
#include "Profiles/soil_sensor_profile.h"

#define SOIL_SAMPLE_PERIOD_MS 600000u /* 10 minutes */

static Clock_Struct soilSampleClock;
static uint32_t soilUptimeSec = 0;
static bool soilSensorReady = false;

static void SoilSensor_takeSample(char *pData);
static void SoilSensor_clockCB(uint32_t arg);

/*********************************************************************
 * @fn      SoilSensor_start
 *
 * @brief   Called after BLE stack init: brings up the I2C sensor,
 *          registers the GATT service and starts the 5-minute sampling
 *          clock.
 *
 * @return  SUCCESS or stack call status
 */
bStatus_t SoilSensor_start(void)
{
    bStatus_t status;

    SoilHistory_init();

    status = SoilSensorProfile_addService();
    if (status != SUCCESS)
    {
        return status;
    }

    soilSensorReady = SoilSensor_init();
    if (!soilSensorReady)
    {
        MenuModule_printf(APP_MENU_PROFILE_STATUS_LINE, 0,
                          "Soil sensor: I2C init FAILED - check wiring");
    }

    Util_constructClock(&soilSampleClock, SoilSensor_clockCB,
                        SOIL_SAMPLE_PERIOD_MS, SOIL_SAMPLE_PERIOD_MS, TRUE, 0);

    // Take a first sample immediately rather than waiting 5 minutes
    BLEAppUtil_invokeFunctionNoData(SoilSensor_takeSample);

    return SUCCESS;
}

/*********************************************************************
 * @fn      SoilSensor_clockCB
 *
 * @brief   Fires every 5 minutes from timer/Swi context. Defers the
 *          actual (blocking) I2C read to the BLE application task.
 */
static void SoilSensor_clockCB(uint32_t arg)
{
    BLEAppUtil_invokeFunctionNoData(SoilSensor_takeSample);
}

/*********************************************************************
 * @fn      SoilSensor_takeSample
 *
 * @brief   Runs in the BLE application task context. Reads the sensor,
 *          appends to the history buffer, updates HISTORY_COUNT and
 *          notifies CURRENT_READING to any subscribed client.
 */
static void SoilSensor_takeSample(char *pData)
{
    SoilSensor_Record_t record;
    uint16_t moistureRaw;
    int16_t tempCentiC;
    uint16_t historyCount;

    soilUptimeSec += SOIL_SAMPLE_PERIOD_MS / 1000;

    if (!soilSensorReady || !SoilSensor_read(&moistureRaw, &tempCentiC))
    {
        MenuModule_printf(APP_MENU_PROFILE_STATUS_LINE, 0,
                          "Soil sensor: read FAILED");
        return;
    }

    record.uptimeSec = soilUptimeSec;
    record.moistureRaw = moistureRaw;
    record.tempCentiC = tempCentiC;

    SoilHistory_add(&record);

    historyCount = SoilHistory_getCount();
    uint32_t historyBaseSeq = SoilHistory_getBaseSeq();
    SoilSensorProfile_setParameter(SOILSENSORPROFILE_HISTORY_COUNT, sizeof(historyCount), &historyCount);
    SoilSensorProfile_setParameter(SOILSENSORPROFILE_HISTORY_BASE_SEQ, sizeof(historyBaseSeq), &historyBaseSeq);
    SoilSensorProfile_setParameter(SOILSENSORPROFILE_CURRENT_READING, sizeof(record), &record);

    MenuModule_printf(APP_MENU_PROFILE_STATUS_LINE, 0,
                      "Soil: moisture=%d temp=%d.%02dC",
                      moistureRaw, tempCentiC / 100, tempCentiC % 100);
}
