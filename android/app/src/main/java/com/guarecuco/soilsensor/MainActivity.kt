package com.guarecuco.soilsensor

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.guarecuco.soilsensor.ble.SoilConnectionState
import com.guarecuco.soilsensor.ui.SoilScreen
import com.guarecuco.soilsensor.ui.SoilSensorTheme
import com.guarecuco.soilsensor.ui.SoilViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: SoilViewModel by viewModels()

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (grants.values.all { it }) {
            scanOrReconnect()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SoilSensorTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SoilScreen(
                        viewModel = viewModel,
                        onRequestConnect = ::connectWithPermissions,
                        onRefresh = ::connectWithPermissions,
                    )
                }
            }
        }
    }

    private fun connectWithPermissions() {
        val required = arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        val missing = required.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            scanOrReconnect()
        } else {
            requestPermissions.launch(missing.toTypedArray())
        }
    }

    // A known device (from a previous session) is reconnected to directly,
    // pulling fresh data straight away - only an unpaired app falls back to
    // scanning so the user can pick a sensor.
    private fun scanOrReconnect() {
        val address = viewModel.connectedDeviceAddress.value
        if (address != null) {
            if (viewModel.connectionState.value is SoilConnectionState.Ready) {
                viewModel.disconnect()
            }
            viewModel.connectToDevice(address, viewModel.connectedDeviceName.value)
        } else {
            viewModel.startScan()
        }
    }
}
