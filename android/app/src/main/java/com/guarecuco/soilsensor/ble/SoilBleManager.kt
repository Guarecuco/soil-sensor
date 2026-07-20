package com.guarecuco.soilsensor.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.ArrayDeque

private const val TAG = "SoilBle"

data class DiscoveredDevice(val address: String, val name: String?, val rssi: Int)

sealed interface SoilConnectionState {
    data object Idle : SoilConnectionState
    data object Scanning : SoilConnectionState
    data object Connecting : SoilConnectionState
    data object SyncingHistory : SoilConnectionState
    data class Ready(val deviceAddress: String) : SoilConnectionState
    data class Disconnected(val deviceAddress: String?) : SoilConnectionState
    data class Error(val message: String) : SoilConnectionState
}

/**
 * Thin wrapper around the platform BLE APIs: scans for peripherals advertising
 * the soil sensor service UUID, lets the caller pick one (there may be more
 * than one sensor around), connects, subscribes to live readings, and pulls
 * the on-device history ring buffer. GATT operations are serialized through a
 * small queue since BluetoothGatt only supports one outstanding operation at
 * a time.
 *
 * Caller is responsible for having BLUETOOTH_SCAN/BLUETOOTH_CONNECT granted
 * before calling [startScan].
 */
@SuppressLint("MissingPermission")
class SoilBleManager(context: Context) {
    private val appContext = context.applicationContext
    private val bluetoothManager =
        appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? = bluetoothManager.adapter
    private val mainHandler = Handler(Looper.getMainLooper())

    private var gatt: BluetoothGatt? = null
    private val opQueue = ArrayDeque<() -> Unit>()
    private var opInFlight = false

    private var historyCount = 0
    private var historyIndex = 0
    private var historyBaseSeq = 0L

    private val _connectionState = MutableStateFlow<SoilConnectionState>(SoilConnectionState.Idle)
    val connectionState: StateFlow<SoilConnectionState> = _connectionState.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<DiscoveredDevice>> = _discoveredDevices.asStateFlow()

    // Set as soon as a connection attempt starts (not once sync finishes) so
    // that readings arriving mid-sync - before the state machine reaches
    // Ready - can still be tagged and stored.
    private val _connectedAddress = MutableStateFlow<String?>(null)
    val connectedAddress: StateFlow<String?> = _connectedAddress.asStateFlow()

    private val _connectedName = MutableStateFlow<String?>(null)
    val connectedName: StateFlow<String?> = _connectedName.asStateFlow()

    var onCurrentReading: (SoilRecord) -> Unit = {}
    var onHistoryRecord: (record: SoilRecord, absoluteSeq: Long) -> Unit = { _, _ -> }
    var onSyncComplete: (newestSyncedSeq: Long?) -> Unit = {}

    /**
     * Given the absolute sequence number of the oldest record currently held
     * on-device and how many are held, returns which ring-buffer index to
     * start reading from (skipping ones already synced). Set by the
     * repository, which knows the last-synced sequence per device.
     */
    var resolveStartIndex: (baseSeq: Long, count: Int) -> Int = { _, _ -> 0 }

    fun startScan() {
        val adapter = adapter
        if (adapter == null || !adapter.isEnabled) {
            _connectionState.value = SoilConnectionState.Error("Bluetooth is off")
            return
        }

        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            _connectionState.value = SoilConnectionState.Error("BLE scanning unavailable")
            return
        }

        _discoveredDevices.value = emptyList()
        _connectionState.value = SoilConnectionState.Scanning

        // Matches on service UUID (proper way) OR device name (fallback while
        // older firmware builds don't advertise the UUID yet) - Android ORs
        // multiple filters together.
        val byServiceUuid = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(BleUuids.SERVICE))
            .build()
        val byName = ScanFilter.Builder()
            .setDeviceName(BleUuids.DEVICE_NAME)
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        Log.d(TAG, "startScan: filtering on service ${BleUuids.SERVICE} or name ${BleUuids.DEVICE_NAME}")
        scanner.startScan(listOf(byServiceUuid, byName), settings, scanCallback)

        mainHandler.postDelayed({
            if (_connectionState.value == SoilConnectionState.Scanning) {
                scanner.stopScan(scanCallback)
                Log.d(TAG, "scan timeout, ${_discoveredDevices.value.size} device(s) found")
            }
        }, SCAN_TIMEOUT_MS)
    }

    /**
     * Seeds the "current device" address/name without touching connection
     * state or touching the radio - used to restore the last-known device
     * on a cold app start so its history can be shown from Room right away.
     */
    fun setKnownDevice(address: String, name: String?) {
        _connectedAddress.value = address
        _connectedName.value = name
    }

    fun connectToDevice(address: String, name: String? = null) {
        val adapter = adapter ?: return
        adapter.bluetoothLeScanner?.stopScan(scanCallback)
        val device = adapter.getRemoteDevice(address)
        Log.d(TAG, "connecting to $address ($name)")
        _connectedAddress.value = address
        _connectedName.value = name ?: device.name
        _connectionState.value = SoilConnectionState.Connecting
        gatt = device.connectGatt(appContext, false, gattCallback)
    }

    fun disconnect() {
        // Keep _connectedAddress so the last device's history stays visible;
        // it's overwritten the next time connectToDevice() is called.
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        opQueue.clear()
        opInFlight = false
        _connectionState.value = SoilConnectionState.Idle
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val current = _discoveredDevices.value
            if (current.none { it.address == device.address }) {
                _discoveredDevices.value = current + DiscoveredDevice(device.address, device.name, result.rssi)
                Log.d(TAG, "found ${device.address} name=${device.name} rssi=${result.rssi}")
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.w(TAG, "scan failed: $errorCode")
            _connectionState.value = SoilConnectionState.Error("Scan failed ($errorCode)")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            Log.d(TAG, "onConnectionStateChange status=$status newState=$newState")
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(TAG, "connected, requesting MTU + discovering services")
                    g.requestMtu(185)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    opQueue.clear()
                    opInFlight = false
                    _connectionState.value = SoilConnectionState.Disconnected(g.device?.address)
                    g.close()
                }
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            Log.d(TAG, "onMtuChanged mtu=$mtu status=$status")
            g.discoverServices()
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            Log.d(TAG, "onServicesDiscovered status=$status")
            val service = g.getService(BleUuids.SERVICE)
            if (service == null) {
                _connectionState.value = SoilConnectionState.Error("Sensor service not found")
                return
            }

            _connectionState.value = SoilConnectionState.SyncingHistory

            val currentReadingChar = service.getCharacteristic(BleUuids.CURRENT_READING)
            enqueue { enableNotify(g, currentReadingChar) }
            enqueue { readCharacteristic(g, currentReadingChar) }
            enqueue { readCharacteristic(g, service.getCharacteristic(BleUuids.HISTORY_BASE_SEQ)) }
            enqueue { readCharacteristic(g, service.getCharacteristic(BleUuids.HISTORY_COUNT)) }
            runNext()
        }

        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            Log.d(TAG, "onDescriptorWrite status=$status")
            opInFlight = false
            runNext()
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            Log.d(TAG, "onCharacteristicWrite ${characteristic.uuid} status=$status")
            opInFlight = false
            runNext()
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicRead(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            Log.d(TAG, "onCharacteristicRead ${characteristic.uuid} status=$status len=${characteristic.value?.size}")
            handleRead(g, characteristic, characteristic.value)
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (characteristic.uuid == BleUuids.CURRENT_READING) {
                SoilRecord.parse(characteristic.value)?.let(onCurrentReading)
            }
        }

        private fun handleRead(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            when (characteristic.uuid) {
                BleUuids.CURRENT_READING -> {
                    SoilRecord.parse(value)?.let(onCurrentReading)
                }
                BleUuids.HISTORY_BASE_SEQ -> {
                    historyBaseSeq = value.toUInt32LE() ?: 0L
                    Log.d(TAG, "history base seq=$historyBaseSeq")
                }
                BleUuids.HISTORY_COUNT -> {
                    historyCount = value.toUInt16LE() ?: 0
                    historyIndex = resolveStartIndex(historyBaseSeq, historyCount).coerceIn(0, historyCount)
                    Log.d(TAG, "history count=$historyCount startIndex=$historyIndex")
                    queueNextHistoryStep(g)
                }
                BleUuids.HISTORY_RECORD -> {
                    SoilRecord.parse(value)?.let { onHistoryRecord(it, historyBaseSeq + historyIndex) }
                    historyIndex++
                    queueNextHistoryStep(g)
                }
            }
            opInFlight = false
            runNext()
        }

        private fun queueNextHistoryStep(g: BluetoothGatt) {
            val service = g.getService(BleUuids.SERVICE) ?: return
            if (historyIndex >= historyCount) {
                Log.d(TAG, "sync complete")
                _connectionState.value = SoilConnectionState.Ready(g.device.address)
                val newestSyncedSeq = if (historyCount == 0) null else historyBaseSeq + historyCount - 1
                onSyncComplete(newestSyncedSeq)
                return
            }
            val indexChar = service.getCharacteristic(BleUuids.HISTORY_INDEX)
            val recordChar = service.getCharacteristic(BleUuids.HISTORY_RECORD)
            enqueue { writeCharacteristic(g, indexChar, historyIndex.toUInt16LEBytes()) }
            enqueue { readCharacteristic(g, recordChar) }
        }
    }

    private fun enableNotify(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        g.setCharacteristicNotification(characteristic, true)
        val descriptor = characteristic.getDescriptor(BleUuids.CLIENT_CHARACTERISTIC_CONFIG)
        if (descriptor != null) {
            @Suppress("DEPRECATION")
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            @Suppress("DEPRECATION")
            g.writeDescriptor(descriptor)
        } else {
            opInFlight = false
            runNext()
        }
    }

    private fun readCharacteristic(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        g.readCharacteristic(characteristic)
    }

    @Suppress("DEPRECATION")
    private fun writeCharacteristic(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, bytes: ByteArray) {
        characteristic.value = bytes
        g.writeCharacteristic(characteristic)
    }

    private fun enqueue(op: () -> Unit) {
        opQueue.addLast(op)
    }

    private fun runNext() {
        if (opInFlight) return
        val next = opQueue.pollFirst() ?: return
        opInFlight = true
        next()
    }

    companion object {
        private const val SCAN_TIMEOUT_MS = 10_000L
    }
}
