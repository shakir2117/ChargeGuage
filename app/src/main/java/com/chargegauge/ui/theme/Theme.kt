package com.chargegauge.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Amber = Color(0xFFE0A045)
private val AmberDark = Color(0xFF8A5A12)
private val Ink = Color(0xFF161810)
private val Paper = Color(0xFFF6F1E6)
private val PaperCard = Color(0xFFFFFBF4)

private val DarkColors = darkColorScheme(
    primary = Amber,
    onPrimary = Ink,
    secondary = Color(0xFFB7C4A4),
    background = Color(0xFF12140F),
    surface = Color(0xFF1C1F18),
    surfaceVariant = Color(0xFF2A2E24),
    onBackground = Color(0xFFF3EFE4),
    onSurface = Color(0xFFF3EFE4),
    onSurfaceVariant = Color(0xFFC9C3B4),
)

private val LightColors = lightColorScheme(
    primary = AmberDark,
    onPrimary = Color.White,
    secondary = Color(0xFF3E5230),
    background = Paper,
    surface = PaperCard,
    surfaceVariant = Color(0xFFE7E0D2),
    onBackground = Ink,
    onSurface = Ink,
    onSurfaceVariant = Color(0xFF5C574C),
)

@Composable
fun ChargeGaugeTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = if (Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (dark) DarkColors else LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
