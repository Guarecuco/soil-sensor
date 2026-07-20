package com.guarecuco.soilsensor.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guarecuco.soilsensor.data.SoilReadingEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

private val MoistureColor = Color(0xFF2E7D32)
private val TempColor = Color(0xFFEF6C00)

private const val EDGE_PADDING_DP = 6
private const val BOTTOM_MARGIN_DP = 20
private const val AXIS_INTERVALS = 4
private val DAY_TICK_HOURS = listOf(0, 4, 8, 12, 16, 20, 23)
private const val MOISTURE_HARD_FLOOR = 0f
private const val MOISTURE_HARD_CEILING = 2000f
private const val MOISTURE_WINDOW_HALF_WIDTH = 1000f

/**
 * A window of ~2000 units (the seesaw's documented dry-to-submerged span)
 * centered on the actual data, rather than always pinned to the sensor's
 * full 200-2000 range - real readings for a potted plant will likely never
 * get near either extreme, and always showing the full range made genuine
 * changes look flat. Floor/ceiling keep it from drifting into nonsensical
 * territory (negative, or past the sensor's real max).
 */
private fun computeMoistureScale(dataMin: Float, dataMax: Float): AxisScale {
    val center = (dataMin + dataMax) / 2f
    val min = (center - MOISTURE_WINDOW_HALF_WIDTH).coerceIn(MOISTURE_HARD_FLOOR, MOISTURE_HARD_CEILING - 1f)
    val max = (center + MOISTURE_WINDOW_HALF_WIDTH).coerceIn(min + 1f, MOISTURE_HARD_CEILING)
    return AxisScale(min, max, (max - min) / AXIS_INTERVALS)
}

/** A "nice" (1/2/5 x 10^n) axis range spanning exactly [AXIS_INTERVALS] equal steps. */
private data class AxisScale(val min: Float, val max: Float, val step: Float) {
    // Capped at 1 decimal - if the range is so tight the step needs more
    // precision than that to look distinct, that's an acceptable tradeoff
    // versus cluttering the axis with 2-decimal values.
    val decimals: Int get() = if (step >= 0.999f) 0 else 1
}

private fun niceStep(rawStep: Float): Float {
    if (rawStep <= 0f) return 1f
    val exponent = floor(log10(rawStep.toDouble())).toInt()
    val magnitude = 10.0.pow(exponent).toFloat()
    val fraction = rawStep / magnitude
    val niceFraction = when {
        fraction <= 1f -> 1f
        fraction <= 2f -> 2f
        fraction <= 5f -> 5f
        else -> 10f
    }
    return niceFraction * magnitude
}

private fun nextNiceStep(step: Float): Float {
    val exponent = floor(log10(step.toDouble())).toInt()
    val magnitude = 10.0.pow(exponent).toFloat()
    val fraction = step / magnitude
    return when {
        fraction < 2f -> 2f * magnitude
        fraction < 5f -> 5f * magnitude
        else -> 10f * magnitude
    }
}

/**
 * Picks a clean axis range/step (e.g. 532/534/536/538/540, not
 * 532/533/534/535/536) so gridline labels are always distinct and the data
 * uses a sensible fraction of the plot height instead of being squashed
 * into a sliver when the real range is small.
 */
private fun computeAxisScale(dataMin: Float, dataMax: Float, intervals: Int = AXIS_INTERVALS): AxisScale {
    val span = (dataMax - dataMin).coerceAtLeast(0.0001f)
    var step = niceStep(span / intervals)
    var min = floor(dataMin / step) * step
    var max = min + intervals * step
    var guard = 0
    while (max < dataMax - step * 0.001f && guard < 6) {
        step = nextNiceStep(step)
        min = floor(dataMin / step) * step
        max = min + intervals * step
        guard++
    }
    return AxisScale(min, max, step)
}

/**
 * Moisture as hourly/daily-averaged bars (green) with temperature as an
 * averaged line overlay (orange), plotted against the full calendar period
 * rather than just the span of available data. Axis value labels are drawn
 * overlaid on the plot itself (not in a reserved margin column) so the plot
 * can use nearly the full width. Tapping a bin shows its exact values.
 */
@Composable
fun SoilChart(
    readings: List<SoilReadingEntity>,
    periodStart: Long,
    periodEnd: Long,
    binCount: Int,
    isDayView: Boolean,
    modifier: Modifier = Modifier,
) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val tooltipBgColor = MaterialTheme.colorScheme.inverseSurface
    val tooltipTextColor = MaterialTheme.colorScheme.inverseOnSurface.toArgb()
    var selectedBin by remember(periodStart, isDayView) { mutableStateOf<Int?>(null) }

    if (readings.isEmpty()) return

    // Bucket raw samples into binCount bins and average each one - the raw
    // 10-min samples aren't evenly spaced enough to plot directly without
    // the line looking like a jagged slope instead of a clean trend.
    val binDuration = (periodEnd - periodStart) / binCount
    val bins = Array(binCount) { mutableListOf<SoilReadingEntity>() }
    for (reading in readings) {
        val index = ((reading.timestampMillis - periodStart) / binDuration).toInt().coerceIn(0, binCount - 1)
        bins[index].add(reading)
    }
    val moistureBins = bins.map { bin -> if (bin.isEmpty()) null else bin.map { it.moistureRaw }.average().toFloat() }
    val tempBins = bins.map { bin -> if (bin.isEmpty()) null else bin.map { it.tempCentiC }.average().toFloat() / 100f }

    val moistureValues = moistureBins.filterNotNull()
    val tempValues = tempBins.filterNotNull()
    if (moistureValues.isEmpty()) return

    val moistureScale = computeMoistureScale(moistureValues.min(), moistureValues.max())
    val tempScale = if (tempValues.isEmpty()) AxisScale(0f, 1f, 1f) else computeAxisScale(tempValues.min(), tempValues.max())

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(300.dp)
            .pointerInput(binCount, periodStart) {
                detectTapGestures { offset ->
                    val edgePadding = EDGE_PADDING_DP.dp.toPx()
                    val plotWidth = size.width - edgePadding * 2
                    val barSlotWidth = plotWidth / binCount
                    val index = ((offset.x - edgePadding) / barSlotWidth).toInt().coerceIn(0, binCount - 1)
                    selectedBin = if (selectedBin == index) null else index
                }
            },
    ) {
        val edgePadding = EDGE_PADDING_DP.dp.toPx()
        val bottomMargin = BOTTOM_MARGIN_DP.dp.toPx()
        val plotWidth = size.width - edgePadding * 2
        val plotHeight = size.height - bottomMargin
        val labelSizePx = 11.sp.toPx()
        val barSlotWidth = plotWidth / binCount

        val gridLines = AXIS_INTERVALS
        repeat(gridLines + 1) { i ->
            val y = plotHeight * i / gridLines
            drawLine(gridColor, Offset(edgePadding, y), Offset(edgePadding + plotWidth, y), strokeWidth = 1f)
        }

        // Bars: one per bin, centered in its slot with a small gap between bars.
        // Bars are baselined at the axis minimum (not true zero, since these
        // are trend readings, not cumulative quantities), which means a bin
        // sitting exactly at that minimum would otherwise draw as a
        // zero-height, invisible bar - so every bar gets a minimum height.
        val barWidth = barSlotWidth * 0.6f
        val minBarHeight = 6f
        moistureBins.forEachIndexed { i, value ->
            if (value == null) return@forEachIndexed
            val normalized = (value - moistureScale.min) / (moistureScale.max - moistureScale.min)
            val barTop = (plotHeight * (1f - normalized)).coerceAtMost(plotHeight - minBarHeight)
            val barLeft = edgePadding + barSlotWidth * i + (barSlotWidth - barWidth) / 2f
            val isSelected = selectedBin == i
            drawRect(
                color = if (isSelected) MoistureColor else MoistureColor.copy(alpha = 0.85f),
                topLeft = Offset(barLeft, barTop),
                size = Size(barWidth, plotHeight - barTop),
            )
        }

        // Temperature line: connects bin centers, breaking across gaps with no data.
        // Points are also drawn as dots so an isolated point (nothing to connect
        // to yet) is still visible instead of a bare moveTo that paints nothing.
        val tempPath = Path()
        var penDown = false
        tempBins.forEachIndexed { i, value ->
            if (value == null) {
                penDown = false
                return@forEachIndexed
            }
            val x = edgePadding + barSlotWidth * (i + 0.5f)
            val normalized = (value - tempScale.min) / (tempScale.max - tempScale.min)
            val y = plotHeight * (1f - normalized)
            if (!penDown) {
                tempPath.moveTo(x, y)
                penDown = true
            } else {
                tempPath.lineTo(x, y)
            }
            drawCircle(color = TempColor, radius = if (selectedBin == i) 6f else 4f, center = Offset(x, y))
        }
        drawPath(tempPath, TempColor, style = Stroke(width = 4f, cap = StrokeCap.Round))

        // Selected bin highlight: vertical guide line through its center
        selectedBin?.let { i ->
            val x = edgePadding + barSlotWidth * (i + 0.5f)
            drawLine(labelColorAsColor(labelColor), Offset(x, 0f), Offset(x, plotHeight), strokeWidth = 1.5f)
        }

        val moisturePaint = Paint().apply {
            color = MoistureColor.toArgb(); textSize = labelSizePx; textAlign = Paint.Align.LEFT; isAntiAlias = true
        }
        val tempPaint = Paint().apply {
            color = TempColor.toArgb(); textSize = labelSizePx; textAlign = Paint.Align.RIGHT; isAntiAlias = true
        }
        val timePaint = Paint().apply {
            color = labelColor; textSize = labelSizePx; isAntiAlias = true
        }

        drawContext.canvas.nativeCanvas.apply {
            // Y-axis labels overlaid directly on the plot, left=moisture right=temp.
            // Each gridline is a clean tick from computeAxisScale, so labels are
            // always distinct instead of repeating the same rounded integer.
            repeat(gridLines + 1) { i ->
                val y = plotHeight * i / gridLines
                val moistureValue = moistureScale.max - moistureScale.step * i
                val tempValue = tempScale.max - tempScale.step * i
                val textY = (y + labelSizePx + 2f).coerceAtMost(plotHeight - 2f)
                val moistureLabel = "%.${moistureScale.decimals}f".format(moistureValue)
                val tempLabel = "%.${tempScale.decimals}f°".format(tempValue)
                drawText(moistureLabel, edgePadding + 4f, textY, moisturePaint)
                drawText(tempLabel, edgePadding + plotWidth - 4f, textY, tempPaint)
            }

            // X-axis: always 24h, no AM/PM, to save space
            if (isDayView) {
                DAY_TICK_HOURS.forEachIndexed { i, hour ->
                    val x = edgePadding + plotWidth * hour / 24f
                    timePaint.textAlign = when (i) {
                        0 -> Paint.Align.LEFT
                        DAY_TICK_HOURS.lastIndex -> Paint.Align.RIGHT
                        else -> Paint.Align.CENTER
                    }
                    drawText("%02d".format(hour), x, size.height - 2f, timePaint)
                }
            } else {
                val tickCount = 5
                val dayFormatter = SimpleDateFormat("d", Locale.getDefault())
                repeat(tickCount + 1) { i ->
                    val fraction = i.toFloat() / tickCount
                    val x = edgePadding + plotWidth * fraction
                    val millis = periodStart + ((periodEnd - periodStart) * fraction).toLong()
                    timePaint.textAlign = when (i) {
                        0 -> Paint.Align.LEFT
                        tickCount -> Paint.Align.RIGHT
                        else -> Paint.Align.CENTER
                    }
                    drawText(dayFormatter.format(Date(millis)), x, size.height - 2f, timePaint)
                }
            }
        }

        // Tooltip for the selected bin - box drawn via DrawScope, text via nativeCanvas
        selectedBin?.let { i ->
            val moistureValue = moistureBins[i]
            val tempValue = tempBins[i]
            if (moistureValue == null && tempValue == null) return@let

            val binStartMillis = periodStart + i * binDuration
            val timeLabel = if (isDayView) {
                "%02d:00".format(i)
            } else {
                SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(binStartMillis))
            }
            val lines = listOfNotNull(
                timeLabel,
                moistureValue?.let { "Moisture: ${it.toInt()}" },
                tempValue?.let { "Temp: %.1f°C".format(it) },
            )

            val tooltipPaint = Paint().apply {
                color = tooltipTextColor; textSize = labelSizePx; isAntiAlias = true; textAlign = Paint.Align.LEFT
            }
            val lineHeight = labelSizePx * 1.4f
            val boxWidth = (lines.maxOf { tooltipPaint.measureText(it) }) + 24f
            val boxHeight = lineHeight * lines.size + 12f

            val barX = edgePadding + barSlotWidth * (i + 0.5f)
            val boxLeft = (barX - boxWidth / 2f).coerceIn(edgePadding, edgePadding + plotWidth - boxWidth)
            val boxTop = 4f

            drawRoundRect(
                color = tooltipBgColor,
                topLeft = Offset(boxLeft, boxTop),
                size = Size(boxWidth, boxHeight),
                cornerRadius = CornerRadius(8f, 8f),
            )
            drawContext.canvas.nativeCanvas.apply {
                lines.forEachIndexed { lineIndex, line ->
                    drawText(line, boxLeft + 12f, boxTop + lineHeight * (lineIndex + 1), tooltipPaint)
                }
            }
        }
    }
}

private fun labelColorAsColor(argb: Int): Color = Color(argb)
