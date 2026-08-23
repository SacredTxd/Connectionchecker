package com.sacredtxd.connectionchecker.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.sacredtxd.connectionchecker.data.LatencyChartModel
import com.sacredtxd.connectionchecker.ui.theme.ChartColors
import com.sacredtxd.connectionchecker.ui.theme.chartColors

private val AxisGutter = 40.dp
private val PlotHeight = 168.dp

/**
 * Latency over time: one series, so the title carries identity and no legend box is
 * needed. Offline samples break the line and are marked on the baseline, so an
 * outage reads as a gap rather than as a dip to zero.
 */
@Composable
fun LatencyChart(
    model: LatencyChartModel,
    modifier: Modifier = Modifier,
) {
    val colors = chartColors
    val textMeasurer = rememberTextMeasurer()
    val axisStyle = TextStyle(fontSize = 10.sp, color = colors.axisText)

    if (!model.hasData) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(PlotHeight),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (model.points.isEmpty()) {
                    "No samples yet"
                } else {
                    "No successful probes to plot"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = colors.axisText,
            )
        }
        return
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(PlotHeight)
            .padding(top = 8.dp, bottom = 4.dp),
    ) {
        drawLatencyChart(model, colors, textMeasurer, axisStyle, AxisGutter)
    }
}

/**
 * Drawing is kept out of the composable so the same code renders both the on-screen
 * chart and the exported image.
 */
internal fun DrawScope.drawLatencyChart(
    model: LatencyChartModel,
    colors: ChartColors,
    textMeasurer: TextMeasurer,
    axisStyle: TextStyle,
    axisGutter: Dp,
) {
    val gutterPx = axisGutter.toPx()
    val plotLeft = gutterPx
    val plotRight = size.width
    val plotTop = 0f
    val plotBottom = size.height - 14.dp.toPx()
    val plotWidth = (plotRight - plotLeft).coerceAtLeast(1f)
    val plotHeight = (plotBottom - plotTop).coerceAtLeast(1f)

    fun yFor(latencyMs: Long): Float =
        plotBottom - (latencyMs.toFloat() / model.yMaxMs.toFloat()) * plotHeight

    fun xFor(index: Int): Float = if (model.points.size <= 1) {
        plotLeft + plotWidth / 2f
    } else {
        plotLeft + plotWidth * index / (model.points.size - 1).toFloat()
    }

    // Grid and axis labels stay recessive: hairlines and muted text behind the data.
    for (gridValue in model.gridLinesMs) {
        val y = yFor(gridValue)
        drawLine(
            color = colors.grid,
            start = Offset(plotLeft, y),
            end = Offset(plotRight, y),
            strokeWidth = 1.dp.toPx(),
        )
        val label = textMeasurer.measure("$gridValue", axisStyle)
        drawText(
            textLayoutResult = label,
            topLeft = Offset(
                x = plotLeft - label.size.width - 6.dp.toPx(),
                y = y - label.size.height / 2f,
            ),
        )
    }

    drawLine(
        color = colors.grid,
        start = Offset(plotLeft, plotBottom),
        end = Offset(plotRight, plotBottom),
        strokeWidth = 1.dp.toPx(),
    )

    // The line is drawn as runs of consecutive online samples, so an offline sample
    // breaks the series rather than interpolating across the gap.
    val runs = mutableListOf<MutableList<Pair<Float, Float>>>()
    var current = mutableListOf<Pair<Float, Float>>()
    model.points.forEachIndexed { index, point ->
        val latency = point.latencyMs
        if (latency == null) {
            if (current.isNotEmpty()) {
                runs += current
                current = mutableListOf()
            }
        } else {
            current += xFor(index) to yFor(latency)
        }
    }
    if (current.isNotEmpty()) runs += current

    for (run in runs) {
        if (run.size >= 2) {
            val area = Path().apply {
                moveTo(run.first().first, plotBottom)
                run.forEach { (x, y) -> lineTo(x, y) }
                lineTo(run.last().first, plotBottom)
                close()
            }
            drawPath(area, color = colors.fill)

            val stroke = Path().apply {
                moveTo(run.first().first, run.first().second)
                run.drop(1).forEach { (x, y) -> lineTo(x, y) }
            }
            drawPath(
                path = stroke,
                color = colors.line,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
            )
        } else {
            val (x, y) = run.first()
            drawCircle(color = colors.line, radius = 4.dp.toPx(), center = Offset(x, y))
        }
    }

    // Outages are marked on the baseline in the reserved critical colour. They are
    // never colour-alone: the caption below the chart names the count.
    model.points.forEachIndexed { index, point ->
        if (!point.online) {
            drawLine(
                color = colors.outage,
                start = Offset(xFor(index), plotBottom),
                end = Offset(xFor(index), plotBottom - 8.dp.toPx()),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }

    // A single direct label on the newest point, rather than a number on every point.
    val newest = model.points.lastOrNull { it.online }
    if (newest?.latencyMs != null) {
        val index = model.points.indexOfLast { it.online }
        val x = xFor(index)
        val y = yFor(newest.latencyMs)
        drawCircle(color = colors.surface, radius = 5.dp.toPx(), center = Offset(x, y))
        drawCircle(color = colors.line, radius = 3.5.dp.toPx(), center = Offset(x, y))

        val label = textMeasurer.measure("${newest.latencyMs} ms", axisStyle)
        val labelX = (x - label.size.width / 2f)
            .coerceIn(plotLeft, plotRight - label.size.width)
        drawText(
            textLayoutResult = label,
            topLeft = Offset(labelX, (y - label.size.height - 8.dp.toPx()).coerceAtLeast(0f)),
        )
    }
}
