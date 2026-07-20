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
import com.guarecuco.soilsensor.ui.SoilScreen
import com.guarecuco.soilsensor.ui.SoilSensorTheme
import com.guarecuco.soilsensor.ui.SoilViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: SoilViewModel by viewModels()

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (grants.values.all { it }) {
            viewModel.startScan()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SoilSensorTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SoilScreen(viewModel = viewModel, onRequestConnect = ::connectWithPermissions)
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
            viewModel.startScan()
        } else {
            requestPermissions.launch(missing.toTypedArray())
        }
    }
}
