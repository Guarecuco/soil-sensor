/*
 * battery_gauge.c
 *
 * I2C driver for the bq27441-G1 fuel gauge on the BOOSTXL-BATPAKMKII.
 *
 * Unlike the seesaw soil sensor, the bq27441's standard data commands
 * (StateOfCharge() among them) are a plain SMBus register read: write the
 * one-byte command code, then read back the two-byte little-endian result,
 * with no processing delay needed between the two - a single I2C
 * transaction with both writeBuf and readBuf set does a write followed by
 * a repeated-start read.
 */

#include <stddef.h>

#include <ti/drivers/I2C.h>
#include <ti/drivers/dpl/ClockP.h>

#include "battery_gauge.h"

#define BQ27441_REG_CONTROL               0x00
#define BQ27441_REG_VOLTAGE               0x04
#define BQ27441_REG_FLAGS                 0x06
#define BQ27441_REG_REMAINING_CAPACITY    0x0C
#define BQ27441_REG_FULL_CHARGE_CAPACITY  0x0E
#define BQ27441_REG_AVERAGE_CURRENT       0x10
#define BQ27441_REG_STATE_OF_CHARGE       0x1C
#define BQ27441_REG_OP_CONFIG             0x3A /* extended command: standard endianness, valid outside CONFIG UPDATE mode */
#define BQ27441_REG_DATA_CLASS            0x3E
#define BQ27441_REG_DATA_BLOCK            0x3F
#define BQ27441_REG_BLOCK_DATA            0x40 /* data-memory RAM window: big-endian words, only valid in CONFIG UPDATE mode */
#define BQ27441_REG_BLOCK_DATA_CHECKSUM   0x60
#define BQ27441_REG_BLOCK_DATA_CONTROL    0x61

#define BQ27441_FLAG_CFGUPMODE 0x0010

#define BQ27441_OPCONFIG_DATA_CLASS 0x40 /* "Registers" data-memory subclass, holds OpConfig at block offset 0 */

/*
 * Value written to OpConfig with BIE cleared, matching TI's own
 * boostxl_batpakmkii_fuelgauge demo (HAL_BQ27441.c, simplelink_msp432p4_sdk)
 * for boards where jumper JP6 (BIN) is left open - our exact hardware.
 * With BIE set (the factory default), the gauge only trusts the BIN pin
 * for battery-insertion detection and ignores the soft BAT_INSERT command
 * entirely, which is why sending BAT_INSERT alone left Flags() BAT_DET
 * stuck at 0 and StateOfCharge()/FullChargeCapacity()/RemainingCapacity()
 * all stuck at 0.
 */
#define BQ27441_OPCONFIG_VALUE 0x05F8

/*
 * Control() subcommand telling the gauge a battery is physically present.
 * Only takes effect once OpConfig's BIE bit is cleared (see above) - with
 * BIE set, this command is silently ignored.
 */
#define BQ27441_CONTROL_BAT_INSERT   0x000C
#define BQ27441_CONTROL_SET_CFGUPDATE 0x0013
#define BQ27441_CONTROL_SOFT_RESET    0x0042

#define BQ27441_POLL_INTERVAL_USEC 50000
#define BQ27441_POLL_MAX_ATTEMPTS  20
#define BQ27441_CHECKSUM_MAX_RETRIES 5
/* Data-memory (flash) writes take longer to actually commit than a normal
 * register write - too short a wait here reads back the stale checksum
 * and looks like a mismatch even though the write itself went through. */
#define BQ27441_CHECKSUM_SETTLE_USEC 300000

static I2C_Handle i2cHandle = NULL;

static bool readWord(uint8_t reg, uint16_t *value)
{
    I2C_Transaction transaction = {0};
    uint8_t resp[2];

    transaction.targetAddress = BATTERY_GAUGE_I2C_ADDRESS;
    transaction.writeBuf = &reg;
    transaction.writeCount = sizeof(reg);
    transaction.readBuf = resp;
    transaction.readCount = sizeof(resp);

    if (!I2C_transfer(i2cHandle, &transaction))
    {
        return false;
    }

    *value = ((uint16_t)resp[1] << 8) | resp[0];
    return true;
}

static bool readByte(uint8_t reg, uint8_t *value)
{
    I2C_Transaction transaction = {0};

    transaction.targetAddress = BATTERY_GAUGE_I2C_ADDRESS;
    transaction.writeBuf = &reg;
    transaction.writeCount = sizeof(reg);
    transaction.readBuf = value;
    transaction.readCount = 1;

    return I2C_transfer(i2cHandle, &transaction);
}

static bool writeByte(uint8_t reg, uint8_t value)
{
    I2C_Transaction transaction = {0};
    uint8_t buf[2];

    buf[0] = reg;
    buf[1] = value;

    transaction.targetAddress = BATTERY_GAUGE_I2C_ADDRESS;
    transaction.writeBuf = buf;
    transaction.writeCount = sizeof(buf);

    return I2C_transfer(i2cHandle, &transaction);
}

/* Control() is a standard command too, but its "value" is a subcommand
 * code that the gauge acts on rather than data to store - written the same
 * little-endian way as any other standard command. */
static bool writeControlSubcommand(uint16_t subcommand)
{
    I2C_Transaction transaction = {0};
    uint8_t buf[3];

    buf[0] = BQ27441_REG_CONTROL;
    buf[1] = (uint8_t)(subcommand & 0xFF);
    buf[2] = (uint8_t)(subcommand >> 8);

    transaction.targetAddress = BATTERY_GAUGE_I2C_ADDRESS;
    transaction.writeBuf = buf;
    transaction.writeCount = sizeof(buf);
    transaction.readBuf = NULL;
    transaction.readCount = 0;

    return I2C_transfer(i2cHandle, &transaction);
}

/* Data-memory (block RAM) words are transferred big-endian (MSB first) -
 * the opposite of every standard/extended command elsewhere in this
 * driver. This is a documented bq27441 quirk, not a typo. */
static bool readBlockWordBE(uint8_t reg, uint16_t *value)
{
    I2C_Transaction transaction = {0};
    uint8_t resp[2];

    transaction.targetAddress = BATTERY_GAUGE_I2C_ADDRESS;
    transaction.writeBuf = &reg;
    transaction.writeCount = sizeof(reg);
    transaction.readBuf = resp;
    transaction.readCount = sizeof(resp);

    if (!I2C_transfer(i2cHandle, &transaction))
    {
        return false;
    }

    *value = ((uint16_t)resp[0] << 8) | resp[1];
    return true;
}

static bool writeBlockWordBE(uint8_t reg, uint16_t value)
{
    I2C_Transaction transaction = {0};
    uint8_t buf[3];

    buf[0] = reg;
    buf[1] = (uint8_t)(value >> 8);
    buf[2] = (uint8_t)(value & 0xFF);

    transaction.targetAddress = BATTERY_GAUGE_I2C_ADDRESS;
    transaction.writeBuf = buf;
    transaction.writeCount = sizeof(buf);

    return I2C_transfer(i2cHandle, &transaction);
}

/* Same byte-difference checksum algorithm as TI's demo: 0xFF minus the
 * running sum of every data-memory byte in the block, updated in place as
 * each word is replaced rather than recomputed from scratch. */
static uint8_t computeChecksum(uint8_t oldChecksum, uint16_t oldData, uint16_t newData)
{
    uint8_t tmp = (uint8_t)(0xFF - oldChecksum - (uint8_t)oldData - (uint8_t)(oldData >> 8));
    return (uint8_t)(0xFF - (uint8_t)(tmp + (uint8_t)newData + (uint8_t)(newData >> 8)));
}

static bool waitForFlagState(uint16_t mask, bool wantSet)
{
    uint16_t flags = 0;
    int attempt;

    for (attempt = 0; attempt < BQ27441_POLL_MAX_ATTEMPTS; attempt++)
    {
        if (readWord(BQ27441_REG_FLAGS, &flags) && (((flags & mask) != 0) == wantSet))
        {
            return true;
        }
        ClockP_usleep(BQ27441_POLL_INTERVAL_USEC);
    }
    return false;
}

/*
 * Clears OpConfig's Battery Insertion Enable bit via the data-memory
 * CONFIG UPDATE handshake (enter config-update mode, select the
 * "Registers" subclass, rewrite the block with a matching checksum,
 * soft-reset back out). Safe to run every boot: if BIE is already clear,
 * this rewrites the same value the gauge already has.
 */
static bool clearBatteryInsertionEnable(void)
{
    uint8_t oldChecksum, newChecksum, verifyChecksum;
    uint16_t oldOpConfig;
    int attempt;
    bool checksumOk = false;

    if (!writeControlSubcommand(BQ27441_CONTROL_SET_CFGUPDATE))
    {
        return false;
    }
    if (!waitForFlagState(BQ27441_FLAG_CFGUPMODE, true))
    {
        return false;
    }

    if (writeByte(BQ27441_REG_BLOCK_DATA_CONTROL, 0x00) &&
        writeByte(BQ27441_REG_DATA_CLASS, BQ27441_OPCONFIG_DATA_CLASS) &&
        writeByte(BQ27441_REG_DATA_BLOCK, 0x00))
    {
        ClockP_usleep(BQ27441_POLL_INTERVAL_USEC);

        for (attempt = 0; attempt < BQ27441_CHECKSUM_MAX_RETRIES; attempt++)
        {
            if (!readByte(BQ27441_REG_BLOCK_DATA_CHECKSUM, &oldChecksum) ||
                !readBlockWordBE(BQ27441_REG_BLOCK_DATA, &oldOpConfig))
            {
                continue;
            }

            newChecksum = computeChecksum(oldChecksum, oldOpConfig, BQ27441_OPCONFIG_VALUE);

            if (!writeBlockWordBE(BQ27441_REG_BLOCK_DATA, BQ27441_OPCONFIG_VALUE) ||
                !writeByte(BQ27441_REG_BLOCK_DATA_CHECKSUM, newChecksum))
            {
                continue;
            }
            ClockP_usleep(BQ27441_CHECKSUM_SETTLE_USEC);

            if (readByte(BQ27441_REG_BLOCK_DATA_CHECKSUM, &verifyChecksum) && verifyChecksum == newChecksum)
            {
                checksumOk = true;
                break;
            }
        }
    }

    // Always leave CONFIG UPDATE mode, whether or not the checksum
    // verified above - getting stuck here freezes SOC/capacity/BAT_DET at
    // 0 on every subsequent boot until something forces a real reset.
    writeControlSubcommand(BQ27441_CONTROL_SOFT_RESET);
    if (!waitForFlagState(BQ27441_FLAG_CFGUPMODE, false))
    {
        return false;
    }

    return checksumOk;
}

/*
 * A single BAT_INSERT send left Flags() BAT_DET stuck at 0 even with BIE
 * confirmed clear, so poll for it and resend rather than fire-and-forget -
 * same pattern that fixed the CFGUPMODE-stuck issue above.
 */
static bool ensureBatteryDetected(void)
{
    uint16_t flags;
    int attempt;

    for (attempt = 0; attempt < BQ27441_POLL_MAX_ATTEMPTS; attempt++)
    {
        if (readWord(BQ27441_REG_FLAGS, &flags) && (flags & BATTERY_GAUGE_FLAG_BAT_DET))
        {
            return true;
        }
        writeControlSubcommand(BQ27441_CONTROL_BAT_INSERT);
        ClockP_usleep(BQ27441_POLL_INTERVAL_USEC);
    }
    return false;
}

bool BatteryGauge_init(I2C_Handle handle)
{
    uint16_t soc;

    i2cHandle = handle;
    if (i2cHandle == NULL)
    {
        return false;
    }

    /* Probe: a StateOfCharge read confirms the fuel gauge is present and wired correctly */
    if (!readWord(BQ27441_REG_STATE_OF_CHARGE, &soc))
    {
        return false;
    }

    /* Both steps safe to resend every boot - idempotent if already set/cleared */
    clearBatteryInsertionEnable();
    ensureBatteryDetected();

    return true;
}

bool BatteryGauge_read(uint8_t *percent)
{
    uint16_t soc;

    if (i2cHandle == NULL)
    {
        return false;
    }

    if (!readWord(BQ27441_REG_STATE_OF_CHARGE, &soc))
    {
        return false;
    }

    /* StateOfCharge() range is 0-100 by definition, fits a byte */
    *percent = (uint8_t)soc;
    return true;
}

bool BatteryGauge_readFlags(uint16_t *flags)
{
    if (i2cHandle == NULL)
    {
        return false;
    }
    return readWord(BQ27441_REG_FLAGS, flags);
}

bool BatteryGauge_readVoltageMv(uint16_t *millivolts)
{
    if (i2cHandle == NULL)
    {
        return false;
    }
    return readWord(BQ27441_REG_VOLTAGE, millivolts);
}

bool BatteryGauge_readFullChargeCapacityMah(uint16_t *milliampHours)
{
    if (i2cHandle == NULL)
    {
        return false;
    }
    return readWord(BQ27441_REG_FULL_CHARGE_CAPACITY, milliampHours);
}

bool BatteryGauge_readRemainingCapacityMah(uint16_t *milliampHours)
{
    if (i2cHandle == NULL)
    {
        return false;
    }
    return readWord(BQ27441_REG_REMAINING_CAPACITY, milliampHours);
}

bool BatteryGauge_readAverageCurrentMa(int16_t *milliamps)
{
    uint16_t raw;

    if (i2cHandle == NULL)
    {
        return false;
    }
    if (!readWord(BQ27441_REG_AVERAGE_CURRENT, &raw))
    {
        return false;
    }

    /* AverageCurrent() is signed - negative while discharging */
    *milliamps = (int16_t)raw;
    return true;
}

bool BatteryGauge_readOpConfig(uint16_t *opConfig)
{
    if (i2cHandle == NULL)
    {
        return false;
    }
    return readWord(BQ27441_REG_OP_CONFIG, opConfig);
}
