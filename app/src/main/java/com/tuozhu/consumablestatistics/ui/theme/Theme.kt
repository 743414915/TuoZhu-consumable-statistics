package com.tuozhu.consumablestatistics.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = SlateBlue,
    onPrimary = Color.White,
    primaryContainer = SlateMuted,
    onPrimaryContainer = SlateBlueDark,
    secondary = SlateBlueDark,
    onSecondary = Color.White,
    secondaryContainer = SlateMuted,
    onSecondaryContainer = TextPrimary,
    tertiary = WarningAmber,
    onTertiary = TextPrimary,
    background = PageBg,
    onBackground = TextPrimary,
    surface = SurfaceWhite,
    onSurface = TextPrimary,
    surfaceVariant = Color(0xFFF3F4F6),
    onSurfaceVariant = TextSecondary,
    outline = BorderDefault,
    outlineVariant = BorderLight,
    error = SignalRed,
    onError = Color.White,
    errorContainer = RedMuted,
    onErrorContainer = SignalRed,
)

@Composable
fun TuoZhuConsumableStatisticsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = Typography,
        content = content,
    )
}
