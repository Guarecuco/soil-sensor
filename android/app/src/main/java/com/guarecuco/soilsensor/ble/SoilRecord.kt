package com.guarecuco.soilsensor.ble

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Raw 8-byte record as sent by the firmware's CURRENT_READING / HISTORY_RECORD
 * characteristics: little-endian { uint32 uptimeSec; uint16 moistureRaw; int16 tempCentiC }.
 */
data class SoilRecord(
    val uptimeSec: Long,
    val moistureRaw: Int,
    val tempCentiC: Int,
) {
    companion object {
        const val BYTE_SIZE = 8

        fun parse(bytes: ByteArray): SoilRecord? {
            if (bytes.size < BYTE_SIZE) return null
            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val uptimeSec = buf.int.toLong() and 0xFFFFFFFFL
            val moistureRaw = buf.short.toInt() and 0xFFFF
            val tempCentiC = buf.short.toInt()
            return SoilRecord(uptimeSec, moistureRaw, tempCentiC)
        }
    }
}

fun ByteArray.toUInt16LE(): Int? {
    if (size < 2) return null
    return (this[0].toInt() and 0xFF) or ((this[1].toInt() and 0xFF) shl 8)
}

fun Int.toUInt16LEBytes(): ByteArray =
    byteArrayOf((this and 0xFF).toByte(), ((this shr 8) and 0xFF).toByte())

fun ByteArray.toUInt32LE(): Long? {
    if (size < 4) return null
    val buf = ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN)
    return buf.int.toLong() and 0xFFFFFFFFL
}
