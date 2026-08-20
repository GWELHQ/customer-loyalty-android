package com.example.loyaltyapp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

private val LightColorScheme = lightColorScheme(
    primary = ColorPrimary,
    onPrimary = GwGray0,
    primaryContainer = ColorPrimaryTint,
    onPrimaryContainer = GwGreen700,
    secondary = ColorSecondary,
    onSecondary = GwGray0,
    secondaryContainer = ColorSecondaryTint,
    onSecondaryContainer = GwBlue700,
    background = ColorBg,
    onBackground = ColorText,
    surface = ColorSurface,
    onSurface = ColorText,
    surfaceVariant = ColorSurfaceSunken,
    onSurfaceVariant = ColorTextSecondary,
    outline = ColorBorderStrong,
    outlineVariant = ColorBorder,
    error = ColorDanger,
    onError = GwGray0,
    errorContainer = ColorDangerTint,
    onErrorContainer = ColorDanger
)

/** Extra semantic colors the Material3 scheme has no slot for (warning, success, status pills). */
data class GwExtendedColors(
    val success: androidx.compose.ui.graphics.Color,
    val successTint: androidx.compose.ui.graphics.Color,
    val warning: androidx.compose.ui.graphics.Color,
    val warningTint: androidx.compose.ui.graphics.Color,
    val danger: androidx.compose.ui.graphics.Color,
    val dangerTint: androidx.compose.ui.graphics.Color,
    val info: androidx.compose.ui.graphics.Color,
    val infoTint: androidx.compose.ui.graphics.Color,
    val textMuted: androidx.compose.ui.graphics.Color
)

val LocalGwExtendedColors = staticCompositionLocalOf {
    GwExtendedColors(
        success = ColorSuccess,
        successTint = ColorSuccessTint,
        warning = ColorWarning,
        warningTint = ColorWarningTint,
        danger = ColorDanger,
        dangerTint = ColorDangerTint,
        info = ColorInfo,
        infoTint = ColorInfoTint,
        textMuted = ColorTextMuted
    )
}

@Composable
fun LoyaltyAppTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}

object GwTheme {
    val extended: GwExtendedColors
        @Composable get() = LocalGwExtendedColors.current
}
