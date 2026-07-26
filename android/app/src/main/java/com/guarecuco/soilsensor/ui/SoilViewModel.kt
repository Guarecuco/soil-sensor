package com.guarecuco.soilsensor.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.guarecuco.soilsensor.ble.DiscoveredDevice
import com.guarecuco.soilsensor.ble.OadProgress
import com.guarecuco.soilsensor.ble.OadUpdateManager
import com.guarecuco.soilsensor.ble.SoilConnectionState
import com.guarecuco.soilsensor.data.SoilReadingEntity
import com.guarecuco.soilsensor.data.SoilRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SoilViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SoilRepository(application, viewModelScope)
    private val oadUpdateManager = OadUpdateManager(application)

    val connectionState: StateFlow<SoilConnectionState> = repository.connectionState
    val discoveredDevices: StateFlow<List<DiscoveredDevice>> = repository.discoveredDevices
    val connectedDeviceAddress: StateFlow<String?> = repository.connectedDeviceAddress
    val connectedDeviceName: StateFlow<String?> = repository.connectedDeviceName
    val readings: StateFlow<List<SoilReadingEntity>> = repository.readings
    val batteryPercent: StateFlow<Int?> = repository.batteryPercent
    val batteryCharging: StateFlow<Boolean?> = repository.batteryCharging
    val firmwareVersion: StateFlow<String?> = repository.firmwareVersion
    // Dev/testing feature: pushing a signed firmware image over BLE OAD.
    // Not part of the normal sensing flow - see OadUpdateManager for the protocol.
    val oadProgress: StateFlow<OadProgress> = oadUpdateManager.progress

    fun startScan() = repository.startScan()

    fun connectToDevice(address: String, name: String?) = repository.connectToDevice(address, name)

    fun disconnect() = repository.disconnect()

    fun startOadUpdate(imageBytes: ByteArray) {
        val address = connectedDeviceAddress.value ?: return
        viewModelScope.launch {
            try {
                oadUpdateManager.update(address, imageBytes)
            } catch (e: Exception) {
                // oadUpdateManager.progress already reflects OadProgress.Failed
            }
        }
    }

    override fun onCleared() {
        repository.disconnect()
        super.onCleared()
    }
}
