package com.sacredtxd.connectionchecker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * Chart palette. The two marks that carry meaning — the latency line and the outage
 * marker — are validated against both surfaces for colour-vision deficiency
 * separation and 3:1 contrast, with each mode's steps chosen for that mode rather
 * than flipped automatically.
 */
data class ChartColors(
    val line: Color,
    val fill: Color,
    val outage: Color,
    val grid: Color,
    val axisText: Color,
    val surface: Color,
)

private val LightChartColors = ChartColors(
    line = Color(0xFF2A78D6),
    fill = Color(0x1A2A78D6),
    outage = Color(0xFFD03B3B),
    grid = Color(0x1F000000),
    axisText = Color(0xFF52514E),
    surface = Color(0xFFFCFCFB),
)

private val DarkChartColors = ChartColors(
    line = Color(0xFF3987E5),
    fill = Color(0x243987E5),
    outage = Color(0xFFD03B3B),
    grid = Color(0x24FFFFFF),
    axisText = Color(0xFFC3C2B7),
    surface = Color(0xFF1A1A19),
)

val chartColors: ChartColors
    @Composable
    @ReadOnlyComposable
    get() = if (isSystemInDarkTheme()) DarkChartColors else LightChartColors
