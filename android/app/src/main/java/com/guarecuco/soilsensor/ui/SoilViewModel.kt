package com.guarecuco.soilsensor.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.guarecuco.soilsensor.ble.DiscoveredDevice
import com.guarecuco.soilsensor.ble.SoilConnectionState
import com.guarecuco.soilsensor.data.SoilReadingEntity
import com.guarecuco.soilsensor.data.SoilRepository
import kotlinx.coroutines.flow.StateFlow

class SoilViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SoilRepository(application, viewModelScope)

    val connectionState: StateFlow<SoilConnectionState> = repository.connectionState
    val discoveredDevices: StateFlow<List<DiscoveredDevice>> = repository.discoveredDevices
    val connectedDeviceAddress: StateFlow<String?> = repository.connectedDeviceAddress
    val connectedDeviceName: StateFlow<String?> = repository.connectedDeviceName
    val readings: StateFlow<List<SoilReadingEntity>> = repository.readings

    fun startScan() = repository.startScan()

    fun connectToDevice(address: String, name: String?) = repository.connectToDevice(address, name)

    fun disconnect() = repository.disconnect()

    override fun onCleared() {
        repository.disconnect()
        super.onCleared()
    }
}
