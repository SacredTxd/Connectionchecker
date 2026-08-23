package com.sacredtxd.connectionchecker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Ocean = Color(0xFF0B3D5C)
private val OceanLight = Color(0xFF4A90B8)
private val Signal = Color(0xFF1B8A5A)
private val Alert = Color(0xFFB3261E)

private val LightColors = lightColorScheme(
    primary = Ocean,
    secondary = OceanLight,
    tertiary = Signal,
    error = Alert,
)

private val DarkColors = darkColorScheme(
    primary = OceanLight,
    secondary = Ocean,
    tertiary = Signal,
    error = Alert,
)

@Composable
fun ConnectionCheckerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
