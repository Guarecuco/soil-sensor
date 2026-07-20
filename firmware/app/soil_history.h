/*
 * soil_history.h
 *
 * Fixed-size RAM ring buffer holding soil sensor samples, one every
 * 10 minutes (1008 entries == 7 days of history, a safety margin against
 * the phone not syncing for a while). Oldest entries are overwritten once
 * the buffer is full.
 *
 * Every sample is also assigned a sequence number that counts up forever
 * from boot (never reset by the ring buffer wrapping around) so a client
 * can ask "give me everything after sequence N" instead of re-reading the
 * whole buffer on every reconnect.
 */

#ifndef SOIL_HISTORY_H_
#define SOIL_HISTORY_H_

#include <stdbool.h>
#include <stdint.h>

#define SOIL_HISTORY_CAPACITY 1008

typedef struct
{
    uint32_t uptimeSec;
    uint16_t moistureRaw;
    int16_t  tempCentiC;
} SoilSensor_Record_t;

void SoilHistory_init(void);

void SoilHistory_add(const SoilSensor_Record_t *record);

/* Number of valid records currently stored (<= SOIL_HISTORY_CAPACITY) */
uint16_t SoilHistory_getCount(void);

/*
 * Sequence number of the oldest record currently held (record at index 0).
 * Sequence numbers start at 0 at boot and count up by 1 per sample forever;
 * record at ring index i has sequence number getBaseSeq() + i.
 */
uint32_t SoilHistory_getBaseSeq(void);

/*
 * Fetches the record at the given index, oldest-first (0 == oldest
 * record still in the buffer). Returns false if index is out of range.
 */
bool SoilHistory_getRecord(uint16_t index, SoilSensor_Record_t *outRecord);

#endif /* SOIL_HISTORY_H_ */
