package com.tuozhu.consumablestatistics.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = ClayOrange,
    onPrimary = Color.White,
    primaryContainer = BurntOrange,
    onPrimaryContainer = Color.White,
    secondary = MossInk,
    onSecondary = Color.White,
    secondaryContainer = MistBlue,
    onSecondaryContainer = Graphite,
    tertiary = WarningAmber,
    onTertiary = Graphite,
    background = IvoryMist,
    onBackground = Graphite,
    surface = Color(0xFFFFFCF8),
    onSurface = Graphite,
    surfaceVariant = Sandstone,
    onSurfaceVariant = SoftStone,
    outline = Color(0xFFCDBAA8),
    error = SignalRed,
    onError = Color.White,
)

@Composable
fun TuoZhuConsumableStatisticsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = Typography,
        content = content,
    )
}

