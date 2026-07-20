/*
 * soil_history.c
 */

#include <string.h>

#include <ti/drivers/dpl/HwiP.h>

#include "soil_history.h"

static SoilSensor_Record_t historyBuf[SOIL_HISTORY_CAPACITY];
static uint16_t nextWriteSlot = 0;
static uint16_t recordCount = 0;
static uint32_t totalSamplesTaken = 0;

void SoilHistory_init(void)
{
    memset(historyBuf, 0, sizeof(historyBuf));
    nextWriteSlot = 0;
    recordCount = 0;
    totalSamplesTaken = 0;
}

void SoilHistory_add(const SoilSensor_Record_t *record)
{
    uintptr_t key = HwiP_disable();

    historyBuf[nextWriteSlot] = *record;
    nextWriteSlot = (nextWriteSlot + 1) % SOIL_HISTORY_CAPACITY;
    if (recordCount < SOIL_HISTORY_CAPACITY)
    {
        recordCount++;
    }
    totalSamplesTaken++;

    HwiP_restore(key);
}

uint16_t SoilHistory_getCount(void)
{
    return recordCount;
}

uint32_t SoilHistory_getBaseSeq(void)
{
    return totalSamplesTaken - recordCount;
}

bool SoilHistory_getRecord(uint16_t index, SoilSensor_Record_t *outRecord)
{
    uintptr_t key;
    uint16_t oldestSlot;

    if (index >= recordCount)
    {
        return false;
    }

    key = HwiP_disable();

    /* oldest record lives at nextWriteSlot when the buffer has wrapped, otherwise at 0 */
    oldestSlot = (recordCount == SOIL_HISTORY_CAPACITY) ? nextWriteSlot : 0;
    *outRecord = historyBuf[(oldestSlot + index) % SOIL_HISTORY_CAPACITY];

    HwiP_restore(key);

    return true;
}
