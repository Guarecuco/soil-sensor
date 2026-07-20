package com.guarecuco.soilsensor.data

import android.content.Context

/**
 * Persists, per device address, the absolute sequence number of the last
 * history record synced from that sensor - so a reconnect only needs to
 * fetch what's new instead of re-downloading the whole on-device buffer.
 * A handful of longs keyed by MAC address doesn't need a relational store.
 */
class SyncStateStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("soil_sync_state", Context.MODE_PRIVATE)

    fun getLastSyncedSeq(deviceAddress: String): Long? {
        val value = prefs.getLong(key(deviceAddress), -1L)
        return if (value < 0) null else value
    }

    fun setLastSyncedSeq(deviceAddress: String, seq: Long) {
        prefs.edit().putLong(key(deviceAddress), seq).apply()
    }

    /**
     * The most recently connected device, so history can be shown from
     * Room on a cold app start even before (or without ever) reconnecting.
     */
    fun getLastDevice(): Pair<String, String?>? {
        val address = prefs.getString("lastDeviceAddress", null) ?: return null
        val name = prefs.getString("lastDeviceName", null)
        return address to name
    }

    fun setLastDevice(deviceAddress: String, name: String?) {
        prefs.edit()
            .putString("lastDeviceAddress", deviceAddress)
            .putString("lastDeviceName", name)
            .apply()
    }

    private fun key(deviceAddress: String) = "lastSeq_$deviceAddress"
}
