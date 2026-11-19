package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.data.AppearanceMode

private val LightColors = lightColorScheme(
    primary = AppleBlueLight,
    onPrimary = Color.White,
    primaryContainer = AppleBlueContainerLight,
    onPrimaryContainer = AppleBlueLight,
    secondary = AppleGreenLight,
    onSecondary = Color.White,
    secondaryContainer = AppleGreenContainerLight,
    onSecondaryContainer = AppleGreenLight,
    tertiary = AppleAmberLight,
    onTertiary = Color.White,
    tertiaryContainer = AppleAmberContainerLight,
    onTertiaryContainer = AppleAmberLight,
    background = AppleCanvasLight,
    onBackground = AppleInkLight,
    surface = AppleCardLight,
    onSurface = AppleInkLight,
    surfaceVariant = AppleTonalLight,
    onSurfaceVariant = AppleMutedLight,
    outline = AppleHairlineLight,
    error = AppleRedLight,
    onError = Color.White,
    errorContainer = AppleRedContainerLight,
    onErrorContainer = AppleRedLight
)

private val DarkColors = darkColorScheme(
    primary = AppleBlueDark,
    onPrimary = Color.Black,
    primaryContainer = AppleBlueContainerDark,
    onPrimaryContainer = AppleBlueDark,
    secondary = AppleGreenDark,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF0E2F18),
    onSecondaryContainer = AppleGreenDark,
    tertiary = AppleAmberDark,
    onTertiary = Color.Black,
    tertiaryContainer = Color(0xFF3B2809),
    onTertiaryContainer = AppleAmberDark,
    background = AppleCanvasDark,
    onBackground = AppleInkDark,
    surface = AppleCardDark,
    onSurface = AppleInkDark,
    surfaceVariant = AppleTonalDark,
    onSurfaceVariant = AppleMutedDark,
    outline = AppleHairlineDark,
    error = AppleRedDark,
    onError = Color.Black,
    errorContainer = Color(0xFF3F1313),
    onErrorContainer = AppleRedDark
)

val GoLogisticsShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

@Composable
fun GoLogisticsTheme(
    appearanceMode: AppearanceMode = AppearanceMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val isDark = when (appearanceMode) {
        AppearanceMode.SYSTEM -> isSystemInDarkTheme()
        AppearanceMode.LIGHT -> false
        AppearanceMode.DARK -> true
    }

    MaterialTheme(
        colorScheme = if (isDark) DarkColors else LightColors,
        typography = GoLogisticsTypography,
        shapes = GoLogisticsShapes,
        content = content
    )
}
