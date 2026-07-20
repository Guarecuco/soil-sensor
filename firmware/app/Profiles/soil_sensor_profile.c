/*
 * soil_sensor_profile.c
 *
 * Custom GATT profile for the soil moisture sensor. Structure mirrors
 * TI's simple_gatt_profile.c sample, adapted to a 128-bit custom UUID
 * (TI base F000XXXX-0451-4000-B000-000000000000) and this project's
 * four characteristics.
 */

#include <string.h>

#include "ti/ble/stack_util/icall/app/icall.h"
#include "ti/ble/stack_util/icall/app/icall_ble_api.h"

#include "soil_sensor_profile.h"
#include "soil_history.h"

/*********************************************************************
 * GLOBAL VARIABLES
 */

// Soil Sensor Service UUID
GATT_UUID(soilSensorProfile_ServUUID, SOILSENSORPROFILE_SERV_UUID);

GATT_UUID(soilSensorProfile_currentReadingUUID, SOILSENSORPROFILE_CURRENT_READING_UUID);
GATT_UUID(soilSensorProfile_historyCountUUID, SOILSENSORPROFILE_HISTORY_COUNT_UUID);
GATT_UUID(soilSensorProfile_historyIndexUUID, SOILSENSORPROFILE_HISTORY_INDEX_UUID);
GATT_UUID(soilSensorProfile_historyRecordUUID, SOILSENSORPROFILE_HISTORY_RECORD_UUID);
GATT_UUID(soilSensorProfile_historyBaseSeqUUID, SOILSENSORPROFILE_HISTORY_BASE_SEQ_UUID);

/*********************************************************************
 * LOCAL VARIABLES
 */

// Service attribute (128-bit)
static const gattAttrType_t soilSensorProfile_Service = { ATT_UUID_SIZE, soilSensorProfile_ServUUID };

// Current Reading: notify + read, 8 bytes
static uint8_t soilSensorProfile_CurrentReadingProps = GATT_PROP_READ | GATT_PROP_NOTIFY;
static SoilSensor_Record_t soilSensorProfile_CurrentReading = {0};
static gattCharCfg_t *soilSensorProfile_CurrentReadingConfig;
static uint8_t soilSensorProfile_CurrentReadingUserDesp[9] = "Reading";

// History Count: read, uint16
static uint8_t soilSensorProfile_HistoryCountProps = GATT_PROP_READ;
static uint16_t soilSensorProfile_HistoryCount = 0;
static uint8_t soilSensorProfile_HistoryCountUserDesp[13] = "HistCount";

// History Index: write, uint16 (index of the record the client wants next)
static uint8_t soilSensorProfile_HistoryIndexProps = GATT_PROP_WRITE;
static uint16_t soilSensorProfile_HistoryIndex = 0;
static uint8_t soilSensorProfile_HistoryIndexUserDesp[13] = "HistIndex";

// History Record: read, 8 bytes - populated when History Index is written
static uint8_t soilSensorProfile_HistoryRecordProps = GATT_PROP_READ;
static SoilSensor_Record_t soilSensorProfile_HistoryRecord = {0};
static uint8_t soilSensorProfile_HistoryRecordUserDesp[13] = "HistRecord";

// History Base Seq: read, uint32 (absolute sequence number of index 0)
static uint8_t soilSensorProfile_HistoryBaseSeqProps = GATT_PROP_READ;
static uint32_t soilSensorProfile_HistoryBaseSeq = 0;
static uint8_t soilSensorProfile_HistoryBaseSeqUserDesp[13] = "HistBaseSeq";

/*********************************************************************
 * Profile Attributes - Table
 */
static gattAttribute_t soilSensorProfile_attrTbl[] =
{
  // Soil Sensor Service
  GATT_BT_ATT( primaryServiceUUID, GATT_PERMIT_READ, (uint8_t *) &soilSensorProfile_Service ),

  // Current Reading Declaration
  GATT_BT_ATT( characterUUID, GATT_PERMIT_READ, &soilSensorProfile_CurrentReadingProps ),
  // Current Reading Value
  GATT_ATT( soilSensorProfile_currentReadingUUID, GATT_PERMIT_READ, (uint8_t *) &soilSensorProfile_CurrentReading ),
  // Current Reading Configuration (CCCD, for notifications)
  GATT_BT_ATT( clientCharCfgUUID, GATT_PERMIT_READ | GATT_PERMIT_WRITE, (uint8_t *) &soilSensorProfile_CurrentReadingConfig ),
  // Current Reading User Description
  GATT_BT_ATT( charUserDescUUID, GATT_PERMIT_READ, soilSensorProfile_CurrentReadingUserDesp ),

  // History Count Declaration
  GATT_BT_ATT( characterUUID, GATT_PERMIT_READ, &soilSensorProfile_HistoryCountProps ),
  // History Count Value
  GATT_ATT( soilSensorProfile_historyCountUUID, GATT_PERMIT_READ, (uint8_t *) &soilSensorProfile_HistoryCount ),
  // History Count User Description
  GATT_BT_ATT( charUserDescUUID, GATT_PERMIT_READ, soilSensorProfile_HistoryCountUserDesp ),

  // History Index Declaration
  GATT_BT_ATT( characterUUID, GATT_PERMIT_READ, &soilSensorProfile_HistoryIndexProps ),
  // History Index Value
  GATT_ATT( soilSensorProfile_historyIndexUUID, GATT_PERMIT_WRITE, (uint8_t *) &soilSensorProfile_HistoryIndex ),
  // History Index User Description
  GATT_BT_ATT( charUserDescUUID, GATT_PERMIT_READ, soilSensorProfile_HistoryIndexUserDesp ),

  // History Record Declaration
  GATT_BT_ATT( characterUUID, GATT_PERMIT_READ, &soilSensorProfile_HistoryRecordProps ),
  // History Record Value
  GATT_ATT( soilSensorProfile_historyRecordUUID, GATT_PERMIT_READ, (uint8_t *) &soilSensorProfile_HistoryRecord ),
  // History Record User Description
  GATT_BT_ATT( charUserDescUUID, GATT_PERMIT_READ, soilSensorProfile_HistoryRecordUserDesp ),

  // History Base Seq Declaration
  GATT_BT_ATT( characterUUID, GATT_PERMIT_READ, &soilSensorProfile_HistoryBaseSeqProps ),
  // History Base Seq Value
  GATT_ATT( soilSensorProfile_historyBaseSeqUUID, GATT_PERMIT_READ, (uint8_t *) &soilSensorProfile_HistoryBaseSeq ),
  // History Base Seq User Description
  GATT_BT_ATT( charUserDescUUID, GATT_PERMIT_READ, soilSensorProfile_HistoryBaseSeqUserDesp ),
};

/*********************************************************************
 * LOCAL FUNCTIONS
 */
static bStatus_t SoilSensorProfile_readAttrCB( uint16_t connHandle,
                                               gattAttribute_t *pAttr,
                                               uint8_t *pValue, uint16_t *pLen,
                                               uint16_t offset, uint16_t maxLen,
                                               uint8_t method );
static bStatus_t SoilSensorProfile_writeAttrCB( uint16_t connHandle,
                                                gattAttribute_t *pAttr,
                                                uint8_t *pValue, uint16_t len,
                                                uint16_t offset, uint8_t method );

static const gattServiceCBs_t soilSensorProfile_CBs =
{
  SoilSensorProfile_readAttrCB,
  SoilSensorProfile_writeAttrCB,
  NULL // Authorization callback
};

/*********************************************************************
 * PUBLIC FUNCTIONS
 */

bStatus_t SoilSensorProfile_addService( void )
{
  uint8_t status = SUCCESS;

  soilSensorProfile_CurrentReadingConfig = (gattCharCfg_t *)ICall_malloc( sizeof( gattCharCfg_t ) *
                                                                           MAX_NUM_BLE_CONNS );
  if ( soilSensorProfile_CurrentReadingConfig == NULL )
  {
    return ( bleMemAllocError );
  }

  GATTServApp_InitCharCfg( LINKDB_CONNHANDLE_INVALID, soilSensorProfile_CurrentReadingConfig );

  status = GATTServApp_RegisterService( soilSensorProfile_attrTbl,
                                        GATT_NUM_ATTRS( soilSensorProfile_attrTbl ),
                                        GATT_MAX_ENCRYPT_KEY_SIZE,
                                        &soilSensorProfile_CBs );

  return ( status );
}

bStatus_t SoilSensorProfile_setParameter( uint8_t param, uint8_t len, void *value )
{
  bStatus_t status = SUCCESS;

  switch ( param )
  {
    case SOILSENSORPROFILE_CURRENT_READING:
      if ( len == sizeof( SoilSensor_Record_t ) )
      {
        memcpy( &soilSensorProfile_CurrentReading, value, sizeof( SoilSensor_Record_t ) );

        // Notify if a client has subscribed
        GATTServApp_ProcessCharCfg( soilSensorProfile_CurrentReadingConfig,
                                    (uint8_t *) &soilSensorProfile_CurrentReading, FALSE,
                                    soilSensorProfile_attrTbl, GATT_NUM_ATTRS( soilSensorProfile_attrTbl ),
                                    INVALID_TASK_ID, SoilSensorProfile_readAttrCB );
      }
      else
      {
        status = bleInvalidRange;
      }
      break;

    case SOILSENSORPROFILE_HISTORY_COUNT:
      if ( len == sizeof( uint16_t ) )
      {
        soilSensorProfile_HistoryCount = *((uint16_t *)value);
      }
      else
      {
        status = bleInvalidRange;
      }
      break;

    case SOILSENSORPROFILE_HISTORY_BASE_SEQ:
      if ( len == sizeof( uint32_t ) )
      {
        soilSensorProfile_HistoryBaseSeq = *((uint32_t *)value);
      }
      else
      {
        status = bleInvalidRange;
      }
      break;

    default:
      status = INVALIDPARAMETER;
      break;
  }

  return ( status );
}

bStatus_t SoilSensorProfile_getParameter( uint8_t param, void *value )
{
  bStatus_t status = SUCCESS;

  switch ( param )
  {
    case SOILSENSORPROFILE_CURRENT_READING:
      memcpy( value, &soilSensorProfile_CurrentReading, sizeof( SoilSensor_Record_t ) );
      break;

    case SOILSENSORPROFILE_HISTORY_COUNT:
      *((uint16_t *)value) = soilSensorProfile_HistoryCount;
      break;

    case SOILSENSORPROFILE_HISTORY_BASE_SEQ:
      *((uint32_t *)value) = soilSensorProfile_HistoryBaseSeq;
      break;

    default:
      status = INVALIDPARAMETER;
      break;
  }

  return ( status );
}

static bStatus_t SoilSensorProfile_readAttrCB(uint16_t connHandle,
                                              gattAttribute_t *pAttr,
                                              uint8_t *pValue, uint16_t *pLen,
                                              uint16_t offset, uint16_t maxLen,
                                              uint8_t method)
{
  bStatus_t status = SUCCESS;

  if ( offset > 0 )
  {
    return ( ATT_ERR_ATTR_NOT_LONG );
  }

  if ( pAttr->type.len == ATT_UUID_SIZE )
  {
    if ( memcmp( pAttr->type.uuid, soilSensorProfile_currentReadingUUID, ATT_UUID_SIZE ) == 0 )
    {
      *pLen = sizeof( SoilSensor_Record_t );
      memcpy( pValue, &soilSensorProfile_CurrentReading, sizeof( SoilSensor_Record_t ) );
    }
    else if ( memcmp( pAttr->type.uuid, soilSensorProfile_historyCountUUID, ATT_UUID_SIZE ) == 0 )
    {
      *pLen = sizeof( uint16_t );
      memcpy( pValue, &soilSensorProfile_HistoryCount, sizeof( uint16_t ) );
    }
    else if ( memcmp( pAttr->type.uuid, soilSensorProfile_historyRecordUUID, ATT_UUID_SIZE ) == 0 )
    {
      *pLen = sizeof( SoilSensor_Record_t );
      memcpy( pValue, &soilSensorProfile_HistoryRecord, sizeof( SoilSensor_Record_t ) );
    }
    else if ( memcmp( pAttr->type.uuid, soilSensorProfile_historyBaseSeqUUID, ATT_UUID_SIZE ) == 0 )
    {
      *pLen = sizeof( uint32_t );
      memcpy( pValue, &soilSensorProfile_HistoryBaseSeq, sizeof( uint32_t ) );
    }
    else
    {
      *pLen = 0;
      status = ATT_ERR_ATTR_NOT_FOUND;
    }
  }
  else
  {
    // 16-bit UUID attributes (e.g. CCCD) are handled by gattservapp itself
    *pLen = 0;
    status = ATT_ERR_ATTR_NOT_FOUND;
  }

  return ( status );
}

static bStatus_t SoilSensorProfile_writeAttrCB( uint16_t connHandle,
                                                gattAttribute_t *pAttr,
                                                uint8_t *pValue, uint16_t len,
                                                uint16_t offset, uint8_t method )
{
  bStatus_t status = SUCCESS;

  if ( pAttr->type.len == ATT_UUID_SIZE )
  {
    if ( memcmp( pAttr->type.uuid, soilSensorProfile_historyIndexUUID, ATT_UUID_SIZE ) == 0 )
    {
      if ( offset != 0 || len != sizeof( uint16_t ) )
      {
        status = ATT_ERR_INVALID_VALUE_SIZE;
      }
      else
      {
        SoilSensor_Record_t record;

        soilSensorProfile_HistoryIndex = BUILD_UINT16( pValue[0], pValue[1] );

        // Populate History Record synchronously so it is ready before the
        // client's follow-up read of that characteristic.
        if ( SoilHistory_getRecord( soilSensorProfile_HistoryIndex, &record ) )
        {
          soilSensorProfile_HistoryRecord = record;
        }
      }
    }
    else
    {
      status = ATT_ERR_ATTR_NOT_FOUND;
    }
  }
  else if ( pAttr->type.len == ATT_BT_UUID_SIZE )
  {
    uint16_t uuid = BUILD_UINT16( pAttr->type.uuid[0], pAttr->type.uuid[1] );

    if ( uuid == GATT_CLIENT_CHAR_CFG_UUID )
    {
      status = GATTServApp_ProcessCCCWriteReq( connHandle, pAttr, pValue, len,
                                               offset, GATT_CLIENT_CFG_NOTIFY );
    }
    else
    {
      status = ATT_ERR_ATTR_NOT_FOUND;
    }
  }
  else
  {
    status = ATT_ERR_INVALID_HANDLE;
  }

  return ( status );
}
