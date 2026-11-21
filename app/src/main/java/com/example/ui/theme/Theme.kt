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

private val GlasyerDarkColorScheme = darkColorScheme(
    primary = GlasyerDarkAccent,
    onPrimary = Color(0xFF041C28),
    primaryContainer = Color(0xFF10374A),
    onPrimaryContainer = Color(0xFFC9ECFF),
    secondary = Color(0xFF39A2DB),
    onSecondary = Color(0xFF041C28),
    secondaryContainer = GlasyerDarkSurfaceVariant,
    onSecondaryContainer = GlasyerDarkOnBackground,
    tertiary = GlasyerLiveCoral,
    onTertiary = Color.White,
    background = GlasyerDarkBackground,
    onBackground = GlasyerDarkOnBackground,
    surface = GlasyerDarkSurface,
    onSurface = GlasyerDarkOnBackground,
    surfaceVariant = GlasyerDarkSurfaceVariant,
    onSurfaceVariant = GlasyerDarkOnSurfaceVariant,
    outline = GlasyerDarkOutline,
    error = GlasyerLiveCoral,
    onError = Color.White
)

private val GlasyerLightColorScheme = lightColorScheme(
    primary = GlasyerLightAccent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD2EEFC),
    onPrimaryContainer = Color(0xFF042638),
    secondary = Color(0xFF196C96),
    onSecondary = Color.White,
    secondaryContainer = GlasyerLightSurfaceVariant,
    onSecondaryContainer = GlasyerLightOnBackground,
    tertiary = GlasyerLiveCoral,
    onTertiary = Color.White,
    background = GlasyerLightBackground,
    onBackground = GlasyerLightOnBackground,
    surface = GlasyerLightSurface,
    onSurface = GlasyerLightOnBackground,
    surfaceVariant = GlasyerLightSurfaceVariant,
    onSurfaceVariant = GlasyerLightOnSurfaceVariant,
    outline = GlasyerLightOutline,
    error = GlasyerLiveCoral,
    onError = Color.White
)

val GlasyerShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun GlasyerTheme(
    themeMode: String = "system",
    content: @Composable () -> Unit
) {
    val useDark = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val colors = if (useDark) GlasyerDarkColorScheme else GlasyerLightColorScheme

    MaterialTheme(
        colorScheme = colors,
        typography = GlasyerTypography,
        shapes = GlasyerShapes,
        content = content
    )
}
