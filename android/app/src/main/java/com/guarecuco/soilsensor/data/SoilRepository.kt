package com.guarecuco.soilsensor.data

import android.content.Context
import com.guarecuco.soilsensor.ble.SoilBleManager
import com.guarecuco.soilsensor.ble.SoilConnectionState
import com.guarecuco.soilsensor.ble.SoilRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Bridges the BLE manager (device protocol) and Room (local history).
 *
 * The firmware only knows its own uptime, not wall-clock time, so every time
 * a CURRENT_READING arrives this recomputes an anchor (phone-now minus device
 * uptime) and uses it to convert each history record's uptimeSec into a real
 * timestamp. Recomputing the anchor on every live reading keeps it accurate;
 * a few seconds of drift is irrelevant at 10-minute sample resolution.
 *
 * History sync is incremental: the device exposes an absolute, ever-counting
 * sequence number for each sample (never reset by its ring buffer wrapping),
 * and we persist the last one synced per device so a reconnect only pulls
 * what's new. Room's unique index on the reading also makes re-fetching a
 * few already-synced records (e.g. after an interrupted sync) harmless.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SoilRepository(context: Context, private val scope: CoroutineScope) {
    private val dao = SoilDatabase.getInstance(context).soilReadingDao()
    private val syncStateStore = SyncStateStore(context)
    val bleManager = SoilBleManager(context)

    @Volatile private var anchorMillis: Long? = null

    val connectionState: StateFlow<SoilConnectionState> = bleManager.connectionState
    val discoveredDevices = bleManager.discoveredDevices
    val connectedDeviceAddress: StateFlow<String?> = bleManager.connectedAddress
    val connectedDeviceName: StateFlow<String?> = bleManager.connectedName

    val readings = connectedDeviceAddress
        .flatMapLatest { address ->
            if (address == null) flowOf(emptyList()) else dao.observeForDevice(address)
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        // Restore the last-connected device so its history shows from Room
        // immediately on a cold start, with or without the sensor in range.
        syncStateStore.getLastDevice()?.let { (address, name) ->
            bleManager.setKnownDevice(address, name)
        }

        bleManager.onCurrentReading = { record -> onReading(record, isLive = true) }
        bleManager.onHistoryRecord = { record, _ -> onReading(record, isLive = false) }

        bleManager.resolveStartIndex = { baseSeq, count ->
            val address = bleManager.connectedAddress.value
            val lastSynced = address?.let { syncStateStore.getLastSyncedSeq(it) }
            computeStartIndex(baseSeq, count, lastSynced)
        }

        bleManager.onSyncComplete = { newestSyncedSeq ->
            val address = bleManager.connectedAddress.value
            if (address != null && newestSyncedSeq != null) {
                syncStateStore.setLastSyncedSeq(address, newestSyncedSeq)
            }
        }
    }

    fun startScan() = bleManager.startScan()

    fun connectToDevice(address: String, name: String?) {
        syncStateStore.setLastDevice(address, name)
        bleManager.connectToDevice(address, name)
    }

    fun disconnect() = bleManager.disconnect()

    private fun computeStartIndex(baseSeq: Long, count: Int, lastSyncedSeq: Long?): Int {
        if (lastSyncedSeq == null || count == 0) return 0
        val newestAvailable = baseSeq + count - 1
        if (newestAvailable < lastSyncedSeq) return 0 // device rebooted - its sequence counter reset
        if (lastSyncedSeq + 1 < baseSeq) return 0 // gap - some records were evicted since last sync
        return (lastSyncedSeq + 1 - baseSeq).toInt().coerceIn(0, count)
    }

    private fun onReading(record: SoilRecord, isLive: Boolean) {
        val address = bleManager.connectedAddress.value ?: return
        val nowAnchor = System.currentTimeMillis() - record.uptimeSec * 1000L
        if (isLive) {
            anchorMillis = nowAnchor
        }
        val anchor = anchorMillis ?: nowAnchor
        val timestamp = anchor + record.uptimeSec * 1000L

        scope.launch(Dispatchers.IO) {
            dao.insert(
                SoilReadingEntity(
                    deviceAddress = address,
                    timestampMillis = timestamp,
                    uptimeSec = record.uptimeSec,
                    moistureRaw = record.moistureRaw,
                    tempCentiC = record.tempCentiC,
                ),
            )
        }
    }
}
