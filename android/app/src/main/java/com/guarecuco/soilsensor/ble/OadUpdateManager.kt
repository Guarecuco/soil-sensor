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
import android.bluetooth.le.ScanResult
import android.content.Context
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TAG = "SoilOad"

/*
 * Pushes a signed MCUboot image to the sensor over BLE using TI's on-chip
 * OAD profile. Protocol mirrors firmware/tools/oad_update.py exactly (see
 * that file's module docstring for the full protocol writeup and source
 * references) - this is the Kotlin/Android port of the same handshake:
 *
 *   1. Connect to the currently-running app, write 0x01 to the OAD Reset
 *      characteristic (0xFFD0/0xFFD1) -> device reboots into the
 *      "Persistent_app" image.
 *   2. Reconnect (device re-advertises as Persistent_app).
 *   3. Enable notifications on Image Identify/Image Block/Ext Control
 *      (0xFFC1/0xFFC2/0xFFC5), query block size, send the 32-byte image
 *      header, start the transfer, stream blocks in response to the
 *      device's own block-request notifications, then enable the image.
 *
 * Runs its own independent BluetoothGatt connection - deliberately not
 * reusing SoilBleManager's connection/op-queue, since this is a one-shot
 * linear procedure with a completely different shape (notification-driven
 * block requests) rather than the steady-state sensor polling flow.
 */
sealed interface OadProgress {
    data object Idle : OadProgress
    data object ResettingToPersistent : OadProgress
    data object ReconnectingToPersistent : OadProgress
    data class Transferring(val block: Int, val totalBlocks: Int) : OadProgress
    data object EnablingImage : OadProgress
    data object Success : OadProgress
    data class Failed(val message: String) : OadProgress
}

// bq27441-style status codes from OADProfile_Status_e (oad_profile.h)
private const val OAD_SUCCESS = 0
private const val OAD_DL_COMPLETE = 14

private const val EXT_GET_BLK_SZ: Byte = 0x01
private const val EXT_START_OAD: Byte = 0x03
private const val EXT_ENABLE_IMG: Byte = 0x04
private const val EXT_BLK_RSP_NOTIF: Byte = 0x12

private const val OAD_RESET_CMD_START_OAD: Byte = 0x01

private const val IMAGE_HEADER_LEN = 32
private const val SCAN_TIMEOUT_MS = 15_000L
private const val RECONNECT_SCAN_TIMEOUT_MS = 25_000L

/**
 * Reads "major.minor.revision" out of a signed image's MCUboot header
 * (offsets 20/21/22, see IMAGE_MAGIC's format comment above) - the
 * version this file will report once flashed, so the UI can show it
 * before starting the OAD transfer. Returns null if the file is too
 * short or doesn't start with a valid mcuboot magic.
 */
fun parseImageVersion(imageBytes: ByteArray): String? {
    if (imageBytes.size < IMAGE_HEADER_LEN) return null
    val magic = ByteBuffer.wrap(imageBytes, 0, 4).order(ByteOrder.LITTLE_ENDIAN).int
    if (magic != OadUpdateManager.IMAGE_MAGIC) return null
    val major = imageBytes[20].toInt() and 0xFF
    val minor = imageBytes[21].toInt() and 0xFF
    val revision = ByteBuffer.wrap(imageBytes, 22, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF
    return "$major.$minor.$revision"
}
private const val NOTIFY_TIMEOUT_MS = 10_000L

@SuppressLint("MissingPermission")
class OadUpdateManager(context: Context) {
    private val appContext = context.applicationContext
    private val adapter: BluetoothAdapter? =
        (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    private val _progress = MutableStateFlow<OadProgress>(OadProgress.Idle)
    val progress: StateFlow<OadProgress> = _progress.asStateFlow()

    /**
     * Runs the full update flow against [mainDeviceAddress] (the address the
     * app is normally connected to, running the main soil sensor image),
     * pushing [imageBytes] (a signed .bin, header included, exactly as
     * produced by the firmware build - do not strip or re-sign it).
     *
     * Throws on failure; check [progress] for a human-readable last state.
     */
    suspend fun update(mainDeviceAddress: String, imageBytes: ByteArray) {
        require(imageBytes.size >= IMAGE_HEADER_LEN) { "Image too small to contain a header" }
        val header = imageBytes.copyOfRange(0, IMAGE_HEADER_LEN)
        val magic = ByteBuffer.wrap(header, 0, 4).order(ByteOrder.LITTLE_ENDIAN).int
        require(magic == IMAGE_MAGIC) {
            "Bad image magic 0x%08x (expected 0x%08x) - not a signed MCUboot image".format(magic, IMAGE_MAGIC)
        }
        // mcuboot's ih_img_size (header offset 12) counts only the body,
        // excluding the header itself and the trailing TLV/signature area -
        // but the firmware's OAD profile (sw_update.c: SwUpdate_CheckImageHeader,
        // on-chip/no-external-flash branch) uses that field as-is to compute
        // its own totalBlocks/lastBlockSize, so it expects the download to
        // stop ~2 blocks short of the real file end and rejects our
        // full-size blocks once we cross that point (OAD_BUFFER_OFL).
        // Overwrite just this transmitted copy of the field with the true
        // total transfer length so the firmware's block accounting matches
        // what we actually send; the real flashed bytes (sent via the block
        // writes below) are untouched, and the only other consumer of this
        // value is a pre-erase page count, where erasing extra empty flash
        // inside the slot is harmless.
        ByteBuffer.wrap(header, 12, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(imageBytes.size)

        try {
            _progress.value = OadProgress.ResettingToPersistent
            resetToPersistent(mainDeviceAddress)

            _progress.value = OadProgress.ReconnectingToPersistent
            val persistentAddress = scanFor(BleUuids.PERSISTENT_DEVICE_NAME, RECONNECT_SCAN_TIMEOUT_MS)
                ?: throw OadException("Device did not re-advertise as ${BleUuids.PERSISTENT_DEVICE_NAME} after reset")

            transfer(persistentAddress, header, imageBytes)
            _progress.value = OadProgress.Success
        } catch (e: OadException) {
            _progress.value = OadProgress.Failed(e.message ?: "OAD failed")
            throw e
        }
    }

    private class OadException(message: String) : Exception(message)

    // ------------------------------------------------------------------
    // Step 1: trigger reset into the persistent image
    // ------------------------------------------------------------------
    private suspend fun resetToPersistent(address: String) {
        val session = GattSession(appContext, adapter, address)
        try {
            session.connect()
            val resetChar = session.requireCharacteristic(BleUuids.OAD_RESET_SERVICE, BleUuids.OAD_RESET_CHAR)
            Log.d(TAG, "Writing OAD_RESET_CMD_START_OAD to ${BleUuids.OAD_RESET_CHAR}")
            try {
                session.writeCharacteristic(resetChar, byteArrayOf(OAD_RESET_CMD_START_OAD), withResponse = true)
            } catch (e: OadException) {
                // A disconnect racing the write response is expected - the
                // device may tear the link down before the ATT response
                // arrives, since it resets essentially immediately.
                Log.d(TAG, "write raised during reset (expected if device reset immediately): ${e.message}")
            }
            session.awaitDisconnect(5_000L)
        } finally {
            session.close()
        }
    }

    // ------------------------------------------------------------------
    // Step 2 + 3: reconnect to the persistent image and run the transfer
    // ------------------------------------------------------------------
    private suspend fun transfer(address: String, header: ByteArray, imageBytes: ByteArray) {
        val session = GattSession(appContext, adapter, address)
        try {
            session.connect()
            // Firmware rejects block writes with OAD_BUFFER_OFL unless the
            // ATT write length exactly matches the negotiated block size
            // (240B) - the default 23B MTU would silently truncate every
            // block write, so bump it before starting the transfer.
            session.requestMtu(247)
            // Firmware aborts the whole transfer (resets to block 0) on a
            // single dropped/out-of-order block write - request a tighter
            // connection interval to cut the odds of a radio-level hiccup
            // over 800+ back-to-back writes-without-response.
            session.gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
            val oadService = session.gatt.getService(BleUuids.OAD_SERVICE)
                ?: throw OadException("OAD Service (${BleUuids.OAD_SERVICE}) not found - is the device really running ${BleUuids.PERSISTENT_DEVICE_NAME}?")
            val imgIdentify = oadService.getCharacteristic(BleUuids.OAD_IMG_IDENTIFY)
                ?: throw OadException("Image Identify characteristic not found")
            val imgBlock = oadService.getCharacteristic(BleUuids.OAD_IMG_BLOCK)
                ?: throw OadException("Image Block characteristic not found")
            val extCtrl = oadService.getCharacteristic(BleUuids.OAD_EXT_CTRL)
                ?: throw OadException("Ext Control characteristic not found")

            session.enableNotify(imgIdentify)
            session.enableNotify(imgBlock)
            session.enableNotify(extCtrl)

            Log.d(TAG, "Requesting OAD block size")
            session.writeCharacteristic(extCtrl, byteArrayOf(EXT_GET_BLK_SZ), withResponse = false)
            val blkSzRsp = session.awaitNotification(BleUuids.OAD_EXT_CTRL, NOTIFY_TIMEOUT_MS) { it.isNotEmpty() && it[0] == EXT_GET_BLK_SZ }
            if (blkSzRsp.size < 3) throw OadException("Malformed block-size response")
            val blockSize = ByteBuffer.wrap(blkSzRsp, 1, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF
            val imgBytesPerBlock = blockSize - 4
            val totalBlocks = (imageBytes.size + imgBytesPerBlock - 1) / imgBytesPerBlock
            Log.d(TAG, "Block size=$blockSize ($imgBytesPerBlock payload/block), $totalBlocks blocks total")

            Log.d(TAG, "Writing Image Identify header")
            session.writeCharacteristic(imgIdentify, header, withResponse = true)
            val idRsp = session.awaitNotification(BleUuids.OAD_IMG_IDENTIFY, NOTIFY_TIMEOUT_MS) { true }
            if (idRsp.isEmpty() || idRsp[0].toInt() != OAD_SUCCESS) {
                throw OadException("Image Identify rejected: status=${idRsp.firstOrNull()?.toInt() ?: -1}")
            }

            Log.d(TAG, "Sending Start OAD")
            session.writeCharacteristic(extCtrl, byteArrayOf(EXT_START_OAD), withResponse = false)
            var blkRsp = session.awaitNotification(BleUuids.OAD_EXT_CTRL, NOTIFY_TIMEOUT_MS) { it.isNotEmpty() && it[0] == EXT_BLK_RSP_NOTIF }
            var (prevStatus, nextBlock) = parseBlockRspNotif(blkRsp)
            if (prevStatus != OAD_SUCCESS) throw OadException("Start OAD failed: status=$prevStatus")

            _progress.value = OadProgress.Transferring(0, totalBlocks)
            while (true) {
                val offset = nextBlock * imgBytesPerBlock
                if (offset >= imageBytes.size) {
                    throw OadException("Device requested block $nextBlock past end of image ($totalBlocks blocks) - protocol desync")
                }
                val chunkEnd = minOf(offset + imgBytesPerBlock, imageBytes.size)
                val payload = ByteBuffer.allocate(4 + (chunkEnd - offset)).order(ByteOrder.LITTLE_ENDIAN)
                    .putInt(nextBlock)
                    .put(imageBytes, offset, chunkEnd - offset)
                    .array()
                session.writeCharacteristic(imgBlock, payload, withResponse = false)
                _progress.value = OadProgress.Transferring(nextBlock + 1, totalBlocks)

                blkRsp = session.awaitNotification(BleUuids.OAD_EXT_CTRL, NOTIFY_TIMEOUT_MS) { it.isNotEmpty() && it[0] == EXT_BLK_RSP_NOTIF }
                val (status, requestedNext) = parseBlockRspNotif(blkRsp)
                if (status == OAD_DL_COMPLETE) {
                    Log.d(TAG, "Download complete after block $nextBlock")
                    break
                }
                if (status != OAD_SUCCESS) {
                    throw OadException("Block write failed at block $nextBlock: status=$status")
                }
                if (requestedNext != nextBlock + 1) {
                    Log.d(TAG, "  desync warning: wrote block $nextBlock, device now requests $requestedNext")
                }
                if (nextBlock % 100 == 0) {
                    Log.d(TAG, "  transferred block $nextBlock/$totalBlocks")
                }
                nextBlock = requestedNext
            }

            _progress.value = OadProgress.EnablingImage
            Log.d(TAG, "Sending Enable Image")
            session.writeCharacteristic(extCtrl, byteArrayOf(EXT_ENABLE_IMG), withResponse = false)
            session.awaitDisconnect(10_000L)
        } finally {
            session.close()
        }
    }

    private fun parseBlockRspNotif(data: ByteArray): Pair<Int, Int> {
        if (data.size < 6) throw OadException("Malformed block-response notification: ${data.size} bytes")
        val status = data[1].toInt() and 0xFF
        val nextBlock = ByteBuffer.wrap(data, 2, 4).order(ByteOrder.LITTLE_ENDIAN).int
        return status to nextBlock
    }

    // ------------------------------------------------------------------
    // Scanning
    // ------------------------------------------------------------------
    private suspend fun scanFor(nameSubstring: String, timeoutMs: Long): String? {
        val adapter = adapter ?: return null
        val scanner = adapter.bluetoothLeScanner ?: return null
        var found: String? = null
        val done = Channel<Unit>(Channel.CONFLATED)

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val name = result.device.name ?: result.scanRecord?.deviceName
                if (name != null && name.contains(nameSubstring, ignoreCase = true)) {
                    found = result.device.address
                    done.trySend(Unit)
                }
            }
        }

        scanner.startScan(callback)
        try {
            withTimeout(timeoutMs) { done.receive() }
        } catch (e: Exception) {
            Log.d(TAG, "scanFor($nameSubstring) timed out or was cancelled: ${e.message}")
        } finally {
            scanner.stopScan(callback)
        }
        return found
    }

    /** A single connect/operate/disconnect GATT session with a linear,
     * suspend-based API instead of Android's raw callback interface. */
    private class GattSession(
        private val appContext: Context,
        adapter: BluetoothAdapter?,
        private val address: String,
    ) {
        private val device = adapter?.getRemoteDevice(address)
            ?: throw OadException("Bluetooth adapter unavailable")

        lateinit var gatt: BluetoothGatt
            private set

        private var connectContinuation: ((Result<Unit>) -> Unit)? = null
        private var writeContinuation: ((Result<Unit>) -> Unit)? = null
        private var descriptorContinuation: ((Result<Unit>) -> Unit)? = null
        private var mtuContinuation: ((Result<Unit>) -> Unit)? = null
        private val disconnected = Channel<Unit>(Channel.CONFLATED)
        private val notifications = mutableMapOf<UUID, Channel<ByteArray>>()

        private fun notifyChannel(uuid: UUID): Channel<ByteArray> =
            notifications.getOrPut(uuid) { Channel(Channel.UNLIMITED) }

        private val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> g.discoverServices()
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        disconnected.trySend(Unit)
                        connectContinuation?.invoke(Result.failure(OadException("Disconnected (status=$status)")))
                        connectContinuation = null
                    }
                }
            }

            override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    connectContinuation?.invoke(Result.success(Unit))
                } else {
                    connectContinuation?.invoke(Result.failure(OadException("Service discovery failed: status=$status")))
                }
                connectContinuation = null
            }

            @Suppress("DEPRECATION")
            override fun onCharacteristicWrite(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    writeContinuation?.invoke(Result.success(Unit))
                } else {
                    writeContinuation?.invoke(Result.failure(OadException("Write failed: status=$status")))
                }
                writeContinuation = null
            }

            override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d(TAG, "MTU changed to $mtu")
                    mtuContinuation?.invoke(Result.success(Unit))
                } else {
                    mtuContinuation?.invoke(Result.failure(OadException("MTU request failed: status=$status")))
                }
                mtuContinuation = null
            }

            override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    descriptorContinuation?.invoke(Result.success(Unit))
                } else {
                    descriptorContinuation?.invoke(Result.failure(OadException("Descriptor write failed: status=$status")))
                }
                descriptorContinuation = null
            }

            @Suppress("DEPRECATION")
            override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                notifyChannel(characteristic.uuid).trySend(characteristic.value ?: ByteArray(0))
            }
        }

        suspend fun connect() {
            suspendCancellableCoroutine<Unit> { cont ->
                connectContinuation = { result ->
                    result.fold(onSuccess = { cont.resume(Unit) }, onFailure = { cont.resumeWithException(it) })
                }
                gatt = device.connectGatt(appContext, false, callback)
                cont.invokeOnCancellation { gatt.close() }
            }
        }

        suspend fun requestMtu(mtu: Int) {
            suspendCancellableCoroutine<Unit> { cont ->
                mtuContinuation = { result ->
                    result.fold(onSuccess = { cont.resume(Unit) }, onFailure = { cont.resumeWithException(it) })
                }
                if (!gatt.requestMtu(mtu)) {
                    mtuContinuation = null
                    cont.resumeWithException(OadException("requestMtu() returned false"))
                }
            }
        }

        fun requireCharacteristic(service: UUID, characteristic: UUID): BluetoothGattCharacteristic {
            val svc = gatt.getService(service) ?: throw OadException("Service $service not found")
            return svc.getCharacteristic(characteristic) ?: throw OadException("Characteristic $characteristic not found")
        }

        @Suppress("DEPRECATION")
        suspend fun writeCharacteristic(characteristic: BluetoothGattCharacteristic, value: ByteArray, withResponse: Boolean) {
            characteristic.writeType = if (withResponse) {
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            } else {
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            }
            characteristic.value = value
            suspendCancellableCoroutine<Unit> { cont ->
                writeContinuation = { result ->
                    result.fold(onSuccess = { cont.resume(Unit) }, onFailure = { cont.resumeWithException(it) })
                }
                if (!gatt.writeCharacteristic(characteristic)) {
                    writeContinuation = null
                    cont.resumeWithException(OadException("writeCharacteristic() returned false"))
                }
            }
        }

        @Suppress("DEPRECATION")
        suspend fun enableNotify(characteristic: BluetoothGattCharacteristic) {
            gatt.setCharacteristicNotification(characteristic, true)
            val descriptor = characteristic.getDescriptor(BleUuids.CLIENT_CHARACTERISTIC_CONFIG)
                ?: throw OadException("No CCCD on ${characteristic.uuid}")
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            suspendCancellableCoroutine<Unit> { cont ->
                descriptorContinuation = { result ->
                    result.fold(onSuccess = { cont.resume(Unit) }, onFailure = { cont.resumeWithException(it) })
                }
                if (!gatt.writeDescriptor(descriptor)) {
                    descriptorContinuation = null
                    cont.resumeWithException(OadException("writeDescriptor() returned false"))
                }
            }
        }

        suspend fun awaitNotification(uuid: UUID, timeoutMs: Long, predicate: (ByteArray) -> Boolean): ByteArray {
            val channel = notifyChannel(uuid)
            return withTimeout(timeoutMs) {
                while (true) {
                    val data = channel.receive()
                    if (predicate(data)) return@withTimeout data
                    Log.d(TAG, "  (ignoring unrelated notification on $uuid: ${data.joinToString(",")})")
                }
                @Suppress("UNREACHABLE_CODE")
                throw OadException("unreachable")
            }
        }

        suspend fun awaitDisconnect(timeoutMs: Long) {
            try {
                withTimeout(timeoutMs) { disconnected.receive() }
            } catch (e: Exception) {
                Log.d(TAG, "  no disconnect observed within ${timeoutMs}ms; continuing anyway")
            }
        }

        fun close() {
            if (::gatt.isInitialized) {
                gatt.disconnect()
                gatt.close()
            }
        }
    }

    companion object {
        // MCUboot image magic (source/third_party/mcuboot's IMAGE_MAGIC),
        // little-endian first 4 bytes of every signed image header.
        const val IMAGE_MAGIC = 0x96f3b83d.toInt()
    }
}
