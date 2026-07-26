package com.guarecuco.soilsensor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import android.content.Intent
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.guarecuco.soilsensor.R
import com.guarecuco.soilsensor.ble.DiscoveredDevice
import com.guarecuco.soilsensor.ble.OadProgress
import com.guarecuco.soilsensor.ble.SoilConnectionState
import com.guarecuco.soilsensor.ble.parseImageVersion
import com.guarecuco.soilsensor.data.SoilReadingEntity
import com.guarecuco.soilsensor.data.buildCsvShareIntent
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

private enum class ChartRange(val label: String) {
    DAY("Day"),
    MONTH("Month"),
}

private data class Period(val startMillis: Long, val endMillis: Long, val label: String, val binCount: Int)

private fun computePeriod(range: ChartRange, offset: Int): Period {
    val zone = ZoneId.systemDefault()
    return when (range) {
        ChartRange.DAY -> {
            val date = LocalDate.now(zone).plusDays(offset.toLong())
            val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
            val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            Period(start, end, date.format(DateTimeFormatter.ofPattern("d MMM yyyy")), binCount = 24)
        }
        ChartRange.MONTH -> {
            val month = YearMonth.now(zone).plusMonths(offset.toLong())
            val start = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val end = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
            Period(start, end, month.format(DateTimeFormatter.ofPattern("MMMM yyyy")), binCount = month.lengthOfMonth())
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoilScreen(viewModel: SoilViewModel, onRequestConnect: () -> Unit, onRefresh: () -> Unit) {
    val connectionState by viewModel.connectionState.collectAsState()
    val discoveredDevices by viewModel.discoveredDevices.collectAsState()
    val connectedDeviceName by viewModel.connectedDeviceName.collectAsState()
    val connectedDeviceAddress by viewModel.connectedDeviceAddress.collectAsState()
    val readings by viewModel.readings.collectAsState()
    val batteryPercent by viewModel.batteryPercent.collectAsState()
    val batteryCharging by viewModel.batteryCharging.collectAsState()
    val firmwareVersion by viewModel.firmwareVersion.collectAsState()
    val oadProgress by viewModel.oadProgress.collectAsState()
    val isRefreshing = connectionState is SoilConnectionState.Connecting ||
        connectionState is SoilConnectionState.SyncingHistory

    val context = LocalContext.current
    var pendingUpdateVersion by remember { mutableStateOf<String?>(null) }
    // Set once a picked file is parsed successfully; cleared on confirm or
    // cancel. Its presence is what shows the confirmation dialog below -
    // startOadUpdate() isn't called until the user explicitly confirms.
    var pendingUpdateBytes by remember { mutableStateOf<ByteArray?>(null) }
    var pendingUpdateError by remember { mutableStateOf<String?>(null) }
    val firmwarePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes == null) {
                pendingUpdateError = "Could not read the selected file."
            } else {
                val version = parseImageVersion(bytes)
                if (version == null) {
                    pendingUpdateError = "Not a valid signed firmware image."
                } else {
                    pendingUpdateVersion = version
                    pendingUpdateBytes = bytes
                }
            }
        }
    }
    var oadDialogDismissed by remember { mutableStateOf(false) }
    LaunchedEffect(oadProgress) {
        if (oadProgress == OadProgress.ResettingToPersistent) {
            oadDialogDismissed = false
        }
    }

    pendingUpdateBytes?.let { bytes ->
        FirmwareConfirmDialog(
            version = pendingUpdateVersion ?: "unknown",
            sizeBytes = bytes.size,
            onConfirm = {
                viewModel.startOadUpdate(bytes)
                pendingUpdateBytes = null
            },
            onCancel = {
                pendingUpdateBytes = null
                pendingUpdateVersion = null
            },
        )
    }
    pendingUpdateError?.let { message ->
        AlertDialog(
            onDismissRequest = { pendingUpdateError = null },
            confirmButton = { TextButton(onClick = { pendingUpdateError = null }) { Text("OK") } },
            title = { Text("Firmware Update") },
            text = { Text(message) },
        )
    }
    if (oadProgress !is OadProgress.Idle && !oadDialogDismissed) {
        OadProgressDialog(oadProgress, pendingUpdateVersion) { oadDialogDismissed = true }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name), fontWeight = FontWeight.SemiBold) },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Spacer(modifier = Modifier.height(4.dp))

                ConnectionCard(
                    state = connectionState,
                    deviceName = connectedDeviceName,
                    deviceAddress = connectedDeviceAddress,
                    firmwareVersion = firmwareVersion,
                    onConnectClick = onRequestConnect,
                    onDisconnectClick = { viewModel.disconnect() },
                    onFirmwareUpdateClick = { firmwarePicker.launch(arrayOf("*/*")) },
                )

                if (connectionState is SoilConnectionState.Scanning) {
                    DeviceList(discoveredDevices) { device -> viewModel.connectToDevice(device.address, device.name) }
                }

                val latest = readings.lastOrNull()
                if (latest != null) {
                    CurrentReadingRow(latest, batteryPercent, batteryCharging)
                    WateringAdviceCard(latest.moistureRaw)
                }

                if (readings.size >= 2) {
                    var range by remember { mutableStateOf(ChartRange.DAY) }
                    var offset by remember(range) { mutableStateOf(0) }
                    val period = remember(range, offset) { computePeriod(range, offset) }
                    val windowed = readings.filter { it.timestampMillis >= period.startMillis && it.timestampMillis < period.endMillis }
                    val canGoForward = offset < 0
                    val canGoBack = readings.first().timestampMillis < period.startMillis

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "History (${readings.size} samples total)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            IconButton(onClick = {
                                val intent = buildCsvShareIntent(context, readings)
                                context.startActivity(Intent.createChooser(intent, "Export soil history"))
                            }) {
                                Icon(imageVector = Icons.Filled.FileDownload, contentDescription = "Export CSV")
                            }
                        }

                        RangeToggle(range) { range = it }

                        PeriodNavigator(
                            label = period.label,
                            canGoBack = canGoBack,
                            canGoForward = canGoForward,
                            onBack = { offset -= 1 },
                            onForward = { offset += 1 },
                        )

                        ChartLegend()

                        if (windowed.size >= 2) {
                            SoilChart(
                                readings = windowed,
                                periodStart = period.startMillis,
                                periodEnd = period.endMillis,
                                binCount = period.binCount,
                                isDayView = range == ChartRange.DAY,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            Text(
                                text = "Not enough samples in this period",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(vertical = 24.dp),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun ConnectionCard(
    state: SoilConnectionState,
    deviceName: String?,
    deviceAddress: String?,
    firmwareVersion: String?,
    onConnectClick: () -> Unit,
    onDisconnectClick: () -> Unit,
    onFirmwareUpdateClick: () -> Unit,
) {
    Card(colors = CardDefaults.elevatedCardColors(), elevation = CardDefaults.cardElevation(2.dp)) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ConnectionIcon(state)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(text = connectionTitle(state), fontWeight = FontWeight.Medium)
                    connectionSubtitle(state, deviceName, deviceAddress)?.let {
                        Text(text = it, style = MaterialTheme.typography.bodySmall)
                    }
                    if (state is SoilConnectionState.Ready && firmwareVersion != null) {
                        Text(text = "FW v$firmwareVersion", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            when (state) {
                is SoilConnectionState.Ready -> Row(verticalAlignment = Alignment.CenterVertically) {
                    // Dev/testing entry point for pushing a signed firmware
                    // image over BLE OAD - see OadUpdateManager.
                    IconButton(onClick = onFirmwareUpdateClick) {
                        Icon(imageVector = Icons.Filled.SystemUpdate, contentDescription = "Update firmware")
                    }
                    TextButton(onClick = onDisconnectClick) { Text("Disconnect") }
                }
                SoilConnectionState.Scanning, SoilConnectionState.Connecting, SoilConnectionState.SyncingHistory ->
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                else -> TextButton(onClick = onConnectClick) { Text("Scan") }
            }
        }
    }
}

@Composable
private fun ConnectionIcon(state: SoilConnectionState) {
    val (icon, tint) = when (state) {
        is SoilConnectionState.Ready -> Icons.Filled.BluetoothConnected to MaterialTheme.colorScheme.primary
        SoilConnectionState.Scanning -> Icons.Filled.BluetoothSearching to MaterialTheme.colorScheme.primary
        else -> Icons.Filled.Bluetooth to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Icon(imageVector = icon, contentDescription = null, tint = tint)
}

private fun connectionTitle(state: SoilConnectionState): String = when (state) {
    SoilConnectionState.Idle -> "Not connected"
    SoilConnectionState.Scanning -> "Scanning for sensors..."
    SoilConnectionState.Connecting -> "Connecting..."
    SoilConnectionState.SyncingHistory -> "Syncing history..."
    is SoilConnectionState.Ready -> "Connected"
    is SoilConnectionState.Disconnected -> "Disconnected"
    is SoilConnectionState.Error -> "Error"
}

@Composable
private fun connectionSubtitle(state: SoilConnectionState, deviceName: String?, deviceAddress: String?): String? = when (state) {
    is SoilConnectionState.Ready -> nameAndAddress(deviceName, state.deviceAddress)
    is SoilConnectionState.Disconnected -> state.deviceAddress?.let { "Last seen: ${nameAndAddress(deviceName, it)}" }
    is SoilConnectionState.Error -> state.message
    SoilConnectionState.Idle -> deviceAddress?.let { "Showing offline data: ${nameAndAddress(deviceName, it)}" }
    else -> null
}

private fun nameAndAddress(name: String?, address: String): String =
    if (name != null) "$name · ${shortAddress(address)}" else shortAddress(address)

private fun shortAddress(address: String): String = address.takeLast(8)

@Composable
private fun DeviceList(devices: List<DiscoveredDevice>, onSelect: (DiscoveredDevice) -> Unit) {
    Card {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            if (devices.isEmpty()) {
                Text(
                    text = "Looking for sensors nearby...",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                devices.forEach { device ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(device) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(text = device.name ?: "Unknown sensor", fontWeight = FontWeight.Medium)
                            Text(text = shortAddress(device.address), style = MaterialTheme.typography.bodySmall)
                        }
                        Text(text = "${device.rssi} dBm", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun CurrentReadingRow(latest: SoilReadingEntity, batteryPercent: Int?, batteryCharging: Boolean?) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Filled.WaterDrop,
            label = moistureLabel(latest.moistureRaw),
            value = latest.moistureRaw.toString(),
            tint = Color(0xFF1976D2),
        )
        StatCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Filled.Thermostat,
            label = "Temperature",
            value = "%.1f°C".format(latest.tempCentiC / 100f),
            tint = Color(0xFFEF6C00),
        )
    }
    val updatedText = "Updated " + SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(latest.timestampMillis))
    val batteryText = if (batteryPercent != null) {
        val charging = if (batteryCharging == true) " (charging)" else ""
        " · Battery $batteryPercent%$charging"
    } else {
        ""
    }
    Text(
        text = "$updatedText$batteryText",
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(top = 4.dp),
    )
}

// Rough, uncalibrated buckets for this specific sensor/soil - see
// WateringAdviceCard for the reasoning. Single source of truth so the
// "Dry/Moist/Wet" chip and the watering advice message can't disagree.
private const val MOISTURE_DRY_THRESHOLD = 300
private const val MOISTURE_WET_THRESHOLD = 1000

private fun moistureLabel(raw: Int): String = when {
    raw < MOISTURE_DRY_THRESHOLD -> "Dry"
    raw < MOISTURE_WET_THRESHOLD -> "Moist"
    else -> "Wet"
}

/**
 * Monstera deliciosa likes to dry out somewhat between waterings and hates
 * sitting in soggy soil (root rot risk). There's no official calibration for
 * this raw capacitive value against any specific plant - these thresholds
 * are the same rough dry/moist/wet buckets used elsewhere, just phrased for
 * watering advice. Treat it as a rough guide until the sensor is calibrated
 * against known-dry/known-wet soil.
 */
@Composable
private fun WateringAdviceCard(moistureRaw: Int) {
    val (message, tint) = when {
        moistureRaw < MOISTURE_DRY_THRESHOLD -> "Water your Monstera - soil is dry" to Color(0xFFC62828)
        moistureRaw < MOISTURE_WET_THRESHOLD -> "Monstera is fine - no need to water" to Color(0xFF2E7D32)
        else -> "Soil is very wet - hold off watering" to Color(0xFFEF6C00)
    }
    Card(colors = CardDefaults.elevatedCardColors()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = message, fontWeight = FontWeight.Medium, color = tint)
        }
    }
}

@Composable
private fun StatCard(modifier: Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String, tint: Color) {
    Card(modifier = modifier, colors = CardDefaults.elevatedCardColors()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(imageVector = icon, contentDescription = null, tint = tint)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(text = label, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RangeToggle(selected: ChartRange, onSelect: (ChartRange) -> Unit) {
    SingleChoiceSegmentedButtonRow {
        ChartRange.entries.forEachIndexed { index, range ->
            SegmentedButton(
                selected = range == selected,
                onClick = { onSelect(range) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = ChartRange.entries.size),
            ) {
                Text(range.label)
            }
        }
    }
}

@Composable
private fun PeriodNavigator(label: String, canGoBack: Boolean, canGoForward: Boolean, onBack: () -> Unit, onForward: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, enabled = canGoBack) {
            Icon(imageVector = Icons.Filled.ChevronLeft, contentDescription = "Previous period")
        }
        Text(text = label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
        IconButton(onClick = onForward, enabled = canGoForward) {
            Icon(imageVector = Icons.Filled.ChevronRight, contentDescription = "Next period")
        }
    }
}

@Composable
private fun ChartLegend() {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        LegendDot(color = Color(0xFF2E7D32), label = "Moisture")
        LegendDot(color = Color(0xFFEF6C00), label = "Temperature")
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(10.dp).background(color, CircleShape))
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = label, style = MaterialTheme.typography.bodySmall)
    }
}

/**
 * Shown right after a file is picked, before anything is sent to the
 * device - lets the user back out if they grabbed the wrong .bin.
 */
@Composable
private fun FirmwareConfirmDialog(version: String, sizeBytes: Int, onConfirm: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        confirmButton = { TextButton(onClick = onConfirm) { Text("Update") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
        title = { Text("Firmware Update") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = "Version: v$version", fontWeight = FontWeight.Medium)
                Text(text = "Size: ${sizeBytes / 1024} KB")
                Spacer(modifier = Modifier.height(8.dp))
                Text("Update the connected sensor to this firmware?")
            }
        },
    )
}

/**
 * Dev/testing dialog for the BLE OAD firmware update flow (see
 * OadUpdateManager). Only shown once a transfer has actually started;
 * dismissible once it reaches a terminal state (Success/Failed).
 */
@Composable
private fun OadProgressDialog(progress: OadProgress, targetVersion: String?, onDismiss: () -> Unit) {
    val message = when (progress) {
        OadProgress.Idle -> return
        OadProgress.ResettingToPersistent -> "Rebooting device into update mode..."
        OadProgress.ReconnectingToPersistent -> "Waiting for device to re-advertise..."
        is OadProgress.Transferring -> "Transferring block ${progress.block}/${progress.totalBlocks}"
        OadProgress.EnablingImage -> "Finishing up - enabling new image..."
        OadProgress.Success -> "Update complete. Device is rebooting into the new firmware."
        is OadProgress.Failed -> "Update failed: ${progress.message}"
    }
    val dismissible = progress is OadProgress.Success || progress is OadProgress.Failed

    AlertDialog(
        onDismissRequest = { if (dismissible) onDismiss() },
        confirmButton = {
            if (dismissible) {
                TextButton(onClick = onDismiss) { Text("OK") }
            }
        },
        title = { Text("Firmware Update") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (targetVersion != null) {
                    Text(text = "Updating to v$targetVersion", fontWeight = FontWeight.Medium)
                }
                Text(message)
                if (!dismissible) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            }
        },
    )
}
