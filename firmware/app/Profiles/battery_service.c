/*
 * battery_service.c
 *
 * Standard BLE Battery Service (0x180F). Structure mirrors
 * Profiles/soil_sensor_profile.c (itself modeled on TI's
 * simple_gatt_profile.c sample), but with real BLE SIG 16-bit UUIDs
 * instead of a custom 128-bit one, since these are BLE SIG-adopted
 * characteristics most clients already know how to recognize.
 */

#include <string.h>

#include "ti/ble/stack_util/icall/app/icall.h"
#include "ti/ble/stack_util/icall/app/icall_ble_api.h"

#include "battery_service.h"

/*********************************************************************
 * GLOBAL VARIABLES
 */

// Battery Service UUID
GATT_BT_UUID(batteryService_ServUUID, BATTERY_SERVICE_SERV_UUID);

GATT_BT_UUID(batteryService_LevelUUID, BATTERY_SERVICE_LEVEL_UUID);
GATT_BT_UUID(batteryService_PowerStateUUID, BATTERY_SERVICE_POWER_STATE_UUID);

/*********************************************************************
 * LOCAL VARIABLES
 */

// Service attribute (16-bit)
static const gattAttrType_t batteryService_Service = { ATT_BT_UUID_SIZE, batteryService_ServUUID };

// Battery Level: notify + read, uint8 (0-100)
static uint8_t batteryService_LevelProps = GATT_PROP_READ | GATT_PROP_NOTIFY;
static uint8_t batteryService_Level = 0;
static gattCharCfg_t *batteryService_LevelConfig;
static uint8_t batteryService_LevelUserDesp[] = "Battery Level";

// Battery Power State: notify + read, one packed byte (see battery_service.h)
static uint8_t batteryService_PowerStateProps = GATT_PROP_READ | GATT_PROP_NOTIFY;
static uint8_t batteryService_PowerState = 0;
static gattCharCfg_t *batteryService_PowerStateConfig;
static uint8_t batteryService_PowerStateUserDesp[] = "Battery Power State";

/*********************************************************************
 * Profile Attributes - Table
 */
static gattAttribute_t batteryService_attrTbl[] =
{
  // Battery Service
  GATT_BT_ATT( primaryServiceUUID, GATT_PERMIT_READ, (uint8_t *) &batteryService_Service ),

  // Battery Level Declaration
  GATT_BT_ATT( characterUUID, GATT_PERMIT_READ, &batteryService_LevelProps ),
  // Battery Level Value
  GATT_BT_ATT( batteryService_LevelUUID, GATT_PERMIT_READ, &batteryService_Level ),
  // Battery Level Configuration (CCCD, for notifications)
  GATT_BT_ATT( clientCharCfgUUID, GATT_PERMIT_READ | GATT_PERMIT_WRITE, (uint8_t *) &batteryService_LevelConfig ),
  // Battery Level User Description
  GATT_BT_ATT( charUserDescUUID, GATT_PERMIT_READ, batteryService_LevelUserDesp ),

  // Battery Power State Declaration
  GATT_BT_ATT( characterUUID, GATT_PERMIT_READ, &batteryService_PowerStateProps ),
  // Battery Power State Value
  GATT_BT_ATT( batteryService_PowerStateUUID, GATT_PERMIT_READ, &batteryService_PowerState ),
  // Battery Power State Configuration (CCCD, for notifications)
  GATT_BT_ATT( clientCharCfgUUID, GATT_PERMIT_READ | GATT_PERMIT_WRITE, (uint8_t *) &batteryService_PowerStateConfig ),
  // Battery Power State User Description
  GATT_BT_ATT( charUserDescUUID, GATT_PERMIT_READ, batteryService_PowerStateUserDesp ),
};

/*********************************************************************
 * LOCAL FUNCTIONS
 */
static bStatus_t BatteryService_readAttrCB( uint16_t connHandle,
                                            gattAttribute_t *pAttr,
                                            uint8_t *pValue, uint16_t *pLen,
                                            uint16_t offset, uint16_t maxLen,
                                            uint8_t method );
static bStatus_t BatteryService_writeAttrCB( uint16_t connHandle,
                                             gattAttribute_t *pAttr,
                                             uint8_t *pValue, uint16_t len,
                                             uint16_t offset, uint8_t method );

static const gattServiceCBs_t batteryService_CBs =
{
  BatteryService_readAttrCB,
  BatteryService_writeAttrCB,
  NULL // Authorization callback
};

/*********************************************************************
 * PUBLIC FUNCTIONS
 */

bStatus_t BatteryService_addService( void )
{
  uint8_t status = SUCCESS;

  batteryService_LevelConfig = (gattCharCfg_t *)ICall_malloc( sizeof( gattCharCfg_t ) *
                                                                MAX_NUM_BLE_CONNS );
  if ( batteryService_LevelConfig == NULL )
  {
    return ( bleMemAllocError );
  }
  GATTServApp_InitCharCfg( LINKDB_CONNHANDLE_INVALID, batteryService_LevelConfig );

  batteryService_PowerStateConfig = (gattCharCfg_t *)ICall_malloc( sizeof( gattCharCfg_t ) *
                                                                     MAX_NUM_BLE_CONNS );
  if ( batteryService_PowerStateConfig == NULL )
  {
    return ( bleMemAllocError );
  }
  GATTServApp_InitCharCfg( LINKDB_CONNHANDLE_INVALID, batteryService_PowerStateConfig );

  status = GATTServApp_RegisterService( batteryService_attrTbl,
                                        GATT_NUM_ATTRS( batteryService_attrTbl ),
                                        GATT_MAX_ENCRYPT_KEY_SIZE,
                                        &batteryService_CBs );

  return ( status );
}

bStatus_t BatteryService_setParameter( uint8_t param, uint8_t len, void *value )
{
  bStatus_t status = SUCCESS;

  switch ( param )
  {
    case BATTERY_SERVICE_LEVEL_ID:
      if ( len == sizeof( uint8_t ) )
      {
        batteryService_Level = *((uint8_t *)value);

        GATTServApp_ProcessCharCfg( batteryService_LevelConfig,
                                    &batteryService_Level, FALSE,
                                    batteryService_attrTbl, GATT_NUM_ATTRS( batteryService_attrTbl ),
                                    INVALID_TASK_ID, BatteryService_readAttrCB );
      }
      else
      {
        status = bleInvalidRange;
      }
      break;

    case BATTERY_SERVICE_POWER_STATE_ID:
      if ( len == sizeof( uint8_t ) )
      {
        batteryService_PowerState = *((uint8_t *)value);

        GATTServApp_ProcessCharCfg( batteryService_PowerStateConfig,
                                    &batteryService_PowerState, FALSE,
                                    batteryService_attrTbl, GATT_NUM_ATTRS( batteryService_attrTbl ),
                                    INVALID_TASK_ID, BatteryService_readAttrCB );
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

bStatus_t BatteryService_getParameter( uint8_t param, void *value )
{
  bStatus_t status = SUCCESS;

  switch ( param )
  {
    case BATTERY_SERVICE_LEVEL_ID:
      *((uint8_t *)value) = batteryService_Level;
      break;

    case BATTERY_SERVICE_POWER_STATE_ID:
      *((uint8_t *)value) = batteryService_PowerState;
      break;

    default:
      status = INVALIDPARAMETER;
      break;
  }

  return ( status );
}

static bStatus_t BatteryService_readAttrCB(uint16_t connHandle,
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

  if ( pAttr->type.len == ATT_BT_UUID_SIZE )
  {
    uint16_t uuid = BUILD_UINT16( pAttr->type.uuid[0], pAttr->type.uuid[1] );

    if ( uuid == BATTERY_SERVICE_LEVEL_UUID )
    {
      *pLen = sizeof( uint8_t );
      *pValue = batteryService_Level;
    }
    else if ( uuid == BATTERY_SERVICE_POWER_STATE_UUID )
    {
      *pLen = sizeof( uint8_t );
      *pValue = batteryService_PowerState;
    }
    else
    {
      // CCCD and other 16-bit UUID attributes are handled by gattservapp itself
      *pLen = 0;
      status = ATT_ERR_ATTR_NOT_FOUND;
    }
  }
  else
  {
    *pLen = 0;
    status = ATT_ERR_ATTR_NOT_FOUND;
  }

  return ( status );
}

static bStatus_t BatteryService_writeAttrCB( uint16_t connHandle,
                                             gattAttribute_t *pAttr,
                                             uint8_t *pValue, uint16_t len,
                                             uint16_t offset, uint8_t method )
{
  bStatus_t status = SUCCESS;

  if ( pAttr->type.len == ATT_BT_UUID_SIZE )
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
