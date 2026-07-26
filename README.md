# Soil Sensor

A BLE soil moisture + temperature sensor for houseplants, built on a TI
CC2340R53 LaunchPad and an Adafruit STEMMA soil sensor, paired with an
Android app that logs and charts the history - and can push firmware
updates to the sensor over BLE, no debugger required.

```
soil-sensor/
├── firmware/   TI SimpleLink BLE5-Stack firmware (FreeRTOS, ticlang)
├── android/    Kotlin/Compose companion app
└── docs/       screenshots etc.
```

<img src="docs/app-screenshot.png" alt="App screenshot" width="300">

## How it works

The LaunchPad reads the soil sensor over I2C every 10 minutes, keeps a
rolling buffer of recent samples, and advertises a custom BLE GATT service
(plus the standard Battery and Device Information services, if a fuel gauge
is attached). The Android app scans for that service, connects, pulls only
the samples it hasn't seen yet, and stores everything locally in a Room
database so history keeps accumulating for as long as you want (independent
of the device's own buffer size). Pull-to-refresh reconnects to the
last-known device directly instead of re-scanning.

The app can also push a new signed firmware image to the sensor over BLE
(on-chip OAD via MCUboot) - see [Firmware updates over BLE](#firmware-updates-over-ble-oad).

## Hardware

- **MCU**: TI LP-EM-CC2340R53 LaunchPad
- **Sensor**: [Adafruit STEMMA Soil Sensor](https://www.adafruit.com/product/4026) (seesaw firmware, I2C address `0x36`)
- **Battery gauge (optional)**: [BOOSTXL-BATPAKMKII](https://www.ti.com/tool/BOOSTXL-BATPAKMKII) (bq27441-G1 fuel gauge, bq24250 charger), shares the same I2C bus as the soil sensor
- **Wiring** (LaunchPad 40-pin BoosterPack header):
  - SDA → DIO0 (header pin 10)
  - SCL → DIO24 (header pin 9)
  - 3V3 → pin 1
  - GND → any GND pin

Pins were auto-assigned by SysConfig when the I2C module was added; if you
regenerate `freertos/soil_sensor.syscfg` from scratch, check
`ti_drivers_config.h` for `CONFIG_GPIO_I2C_0_SDA`/`_SCL` in case they move.

If using the BOOSTXL-BATPAKMKII: its `CE` pin lands on this LaunchPad's
BoosterPack pin 34, which is also `SWDIO` - firmware can't safely drive it.
Ground `CE` directly on the battery pack board instead of via the header.
The bq2425x charger also needs a `BAT_INSERT` pulse (and occasionally a
retry) before it reports the battery as detected; see
`app/battery_gauge.c` for the detection/retry logic. The firmware only
*reads* the gauge (level, charging state) - it doesn't manage charging.

## Firmware

Location: `firmware/`. Based on the SimpleLink SDK's `basic_ble` example,
stripped down to a peripheral-only role (no central/observer/broadcaster/
pairing) plus on-chip OAD (see below), and:

- `app/soil_sensor.c` - I2C driver for the seesaw sensor
- `app/soil_history.c` - RAM ring buffer (1008 samples ≈ 7 days at 10-min
  cadence) with an ever-incrementing sequence number per sample, so a client
  can resume a sync instead of re-reading the whole buffer
- `app/battery_gauge.c` - I2C driver for the bq27441-G1 fuel gauge
  (BOOSTXL-BATPAKMKII); read-only, no charge management
- `app/Profiles/soil_sensor_profile.c` - custom GATT service
- `app/Profiles/battery_service.c` - standard BLE Battery Service (0x180F),
  since TI's bundled one only implements Battery Level, not Power State
- `app/Profiles/app_oad.c` - on-chip OAD glue, copied from the SDK's
  `basic_ble_oad_onchip` reference example
- `app/app_soil_sensor.c` - ties it together: a 10-minute `Clock` triggers a
  sample (soil + battery, if attached) which is stored and notified to any
  subscribed client, and reports this image's own version (read from its
  MCUboot header) over the standard Device Information Service at startup

### Building

Requires the SimpleLink Lowpower F3 SDK, SysConfig, and the ticlang
compiler (all bundled with Code Composer Studio, or installable
standalone). Build from the command line:

```sh
cd firmware/freertos/ticlang
gmake \
  SYSCONFIG_TOOL=/path/to/sysconfig_cli.sh \
  TICLANG_ARMCOMPILER=/path/to/ti-cgt-armllvm_x.y.z.LTS
```

This produces `soil_sensor.out`/`soil_sensor.hex` (for a full debugger
flash) plus two MCUboot-signed images for OAD, `soil_sensor_v1.bin`
(version 1.0.0) and `soil_sensor_v2.bin` (version 2.0.0) - bump the
`--version` flags in `ti_ble_oad_postbuild.cfg` for a real release. Flash
with CCS/UniFlash, or import
`freertos/ticlang/soil_sensor_LP_EM_CC2340R53_freertos_ticlang.projectspec`
into CCS Theia to build/flash/debug from the IDE and edit the SysConfig file
visually.

The `SIMPLELINK_LOWPOWER_F3_SDK_INSTALL_DIR` default in the makefile is a
hardcoded absolute path - override it (or edit the makefile) if your SDK
lives somewhere else.

### First flash (one-time, needs a debugger)

OAD updates a running device, but the very first flash needs three images
in place - a bootloader, a small "persistent" recovery image, and this
project's app - each at a fixed address in the CC2340R53's flash:

| Image | Source | Flash address |
|---|---|---|
| `mcuboot_onchip_LP_EM_CC2340R53_nortos_ticlang.hex` | SDK's `nortos/.../mcuboot/mcuboot` example | `AUTO` (from the hex itself) |
| `basic_persistent_LP_EM_CC2340R53_freertos_ticlang.bin` | SDK's `rtos/.../ble/basic_ble_oad_onchip/persistent` example | `0x6000` |
| `soil_sensor_v1.bin` (this repo) | built above | `0x34000` |

Build the first two from their SDK example projects the same way as above,
then flash all three with UniFlash/`dslite.sh` at those addresses. After
that, every further update can go over BLE - see the next section.

`0x34000` is this specific SysConfig config's app-slot address
(`APP_HDR_ADDR`, generated from `needsOad`/`oadMethod` in
`soil_sensor.syscfg`) - a different board or OAD layout can put it
somewhere else, so don't assume it carries over.

### Firmware updates over BLE (OAD)

Tap the update icon next to "Disconnect" in the app, pick a signed
`soil_sensor_vN.bin`, and confirm the version/size shown. Under the hood
(`android/.../ble/OadUpdateManager.kt`):

1. Writes to the OAD Reset characteristic (0xFFD0/0xFFD1) on the
   currently-running app, which reboots the device into the small
   "Persistent_app" image.
2. Reconnects once it re-advertises under that name, negotiates a larger
   ATT MTU (247B - the block writes below need it), and requests a
   high-priority connection interval.
3. Streams the image in blocks over the OAD service (0xFFC0: Image
   Identify `0xFFC1`, Image Block `0xFFC2`, Ext Control `0xFFC5`),
   driven by the device's own block-request notifications.
4. Sends Enable Image; the device validates the signature, swaps images,
   and reboots into the new firmware.

Two non-obvious failure modes worth knowing if you ever touch this path:
- **ATT MTU**: the OAD service reports a 240-byte block size, but without
  an explicit MTU request the default 23-byte MTU silently truncates every
  block write. Must negotiate MTU before the transfer starts.
- **Image length mismatch**: the firmware computes its own block count
  from the MCUboot header's `ih_img_size` field, which excludes both the
  header and the trailing signature TLV - so it expects the download to
  end a couple of blocks short of the real file length. The client works
  around this by overwriting just the *transmitted* copy of that field
  (not the real flashed bytes) with the true full-file size before sending
  the Image Identify write.

A single dropped or out-of-order block aborts the *entire* transfer (the
firmware resets its block counter to 0), so a flaky connection means
starting over from block 0 - there's no partial-resume.

### Tuning

- **Sample interval**: `SOIL_SAMPLE_PERIOD_MS` in `app/app_soil_sensor.c`
  (currently 10 minutes).
- **Temperature calibration**: the seesaw's onboard temp sensor reads its
  own die temperature, which runs a few degrees above ambient from
  self-heating. `SOIL_TEMP_CALIBRATION_OFFSET_CENTIC` in `app/soil_sensor.c`
  (currently `-400`, i.e. -4.00°C) corrects for this - measure against a
  reference thermometer and adjust.
- **Moisture thresholds**: the raw capacitive value (~200 dry to ~2000
  submerged, per Adafruit's spec) isn't calibrated to any absolute moisture
  quantity and depends on your soil/pot. The Android app's dry/moist/wet
  buckets (`<300` / `300-999` / `≥1000`) are a rough starting point - use
  the CSV export to work out real thresholds for your plant.

## Android app

Location: `android/`. Kotlin + Jetpack Compose, Material 3 (dynamic color),
Room, no third-party BLE or charting libraries.

- `ble/SoilBleManager.kt` - scanning (filtered by service UUID, falls back
  to device name), GATT connect, and the incremental history sync protocol;
  also reads the Battery and Device Information services if present
- `ble/OadUpdateManager.kt` - drives the BLE firmware-update flow described
  above (its own independent `BluetoothGatt` session, not reusing
  `SoilBleManager`'s connection, since the OAD handshake has a completely
  different shape - notification-driven block requests instead of steady
  polling)
- `data/` - Room database, per-device sync-state (SharedPreferences), CSV
  export
- `ui/` - Compose screens: connection card (with pull-to-refresh reconnect,
  battery level/charging, firmware version, and the update-firmware entry
  point) + device picker, current reading, watering advice, a day/month
  chart (calendar-based paging, hourly/daily-averaged bars + temperature
  line, tap a bar for exact values), and the firmware-update confirm/
  progress dialogs

### Building

```sh
cd android
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Requires `local.properties` pointing at an Android SDK (`sdk.dir=...`) -
not checked in, create your own. minSdk 31 (Android 12), targetSdk/compileSdk
37.

## BLE protocol

Custom 128-bit UUIDs on TI's base (`F000xxxx-0451-4000-B000-000000000000`):

| Characteristic      | UUID     | Type        | Description                                   |
|----------------------|----------|-------------|------------------------------------------------|
| Service              | `AA00`   | -           | Soil Sensor service                             |
| CURRENT_READING       | `AA01`   | notify/read | Latest sample, 8 bytes (see below)              |
| HISTORY_COUNT         | `AA02`   | read        | uint16, samples currently in the ring buffer    |
| HISTORY_INDEX         | `AA03`   | write       | uint16, index (oldest-first) to fetch next      |
| HISTORY_RECORD        | `AA04`   | read        | 8 bytes, the record at the last written index   |
| HISTORY_BASE_SEQ      | `AA05`   | read        | uint32, absolute sequence number of index 0     |

Record format (8 bytes, little-endian):
`{ uint32 uptimeSec; uint16 moistureRaw; int16 tempCentiC }`

`HISTORY_BASE_SEQ` + ring index gives each sample an absolute, never-reset
sequence number, which the phone persists per device to know where to
resume a sync from.

Standard BLE SIG services, present only if a BOOSTXL-BATPAKMKII is attached:

| Characteristic       | UUID     | Type        | Description                    |
|----------------------|----------|-------------|---------------------------------|
| Battery Service       | `0x180F` | -           |                                  |
| Battery Level          | `0x2A19` | notify/read | uint8, 0-100                    |
| Battery Power State     | `0x2A1A` | notify/read | packed byte (present/discharge/charging/level state) |
| Device Information Service | `0x180A` | -       |                                  |
| Firmware Revision String | `0x2A26` | read      | UTF8 `"major.minor.revision"`, read from this image's own MCUboot header |

OAD (firmware update) - the Reset Service lives in the normally-running
app; the OAD transfer service only appears after rebooting into the
`Persistent_app` image (see [Firmware updates over BLE](#firmware-updates-over-ble-oad)):

| Characteristic  | UUID     | Type        | Description                          |
|------------------|----------|-------------|----------------------------------------|
| OAD Reset Service | `0xFFD0` | -          |                                        |
| OAD Reset         | `0xFFD1` | write      | Write `0x01` to reboot into `Persistent_app` |
| OAD Service       | `0xFFC0` | -          |                                        |
| Image Identify    | `0xFFC1` | write/notify | 32-byte MCUboot image header        |
| Image Block       | `0xFFC2` | write (no response) | `{ uint32 blockNum; uint8[] data }` |
| Ext Control       | `0xFFC5` | write/notify | Block size query, start/enable/cancel commands, block-request notifications |

## Known limitations

- Temperature reading has a few degrees of inherent uncertainty (self-heating
  ATSAMD10 die sensor) - calibrate the offset for your setup.
- No BLE bonding/encryption - deliberately left unpaired since the GATT
  attributes don't require it; connecting shouldn't prompt for a PIN.
- Moisture thresholds are generic placeholders, not calibrated to a specific
  plant/soil - export CSV data and tune once you have enough history.
- OAD has no partial-resume - a dropped block aborts the whole transfer and
  it must restart from block 0 (see [Firmware updates over BLE](#firmware-updates-over-ble-oad)).
- Battery gauge support is read-only - the firmware reports level/charging
  state but doesn't manage charging (no active charger control).
