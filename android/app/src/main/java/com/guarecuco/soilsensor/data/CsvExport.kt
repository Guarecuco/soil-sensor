package com.guarecuco.soilsensor.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Writes readings to a CSV in the app's cache dir and hands back a share
 * Intent (via FileProvider) so the caller can launch a chooser. Raw values
 * only (moisture 0-65535, temp in hundredths of a degree) - kept
 * unconverted so this is exactly what's needed to work out real thresholds
 * later, e.g. in a spreadsheet.
 */
fun buildCsvShareIntent(context: Context, readings: List<SoilReadingEntity>): Intent {
    val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
    val fileName = "soil_history_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.csv"
    val file = File(exportsDir, fileName)

    file.bufferedWriter().use { writer ->
        writer.write("timestamp_iso8601,timestamp_millis,device_address,uptime_sec,moisture_raw,temp_centic\n")
        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        readings.forEach { r ->
            writer.write(
                "${isoFormat.format(Date(r.timestampMillis))}," +
                    "${r.timestampMillis}," +
                    "${r.deviceAddress}," +
                    "${r.uptimeSec}," +
                    "${r.moistureRaw}," +
                    "${r.tempCentiC}\n",
            )
        }
    }

    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    return Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
