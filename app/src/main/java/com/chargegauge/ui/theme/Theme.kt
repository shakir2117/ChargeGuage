package com.chargegauge.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

const val MATERIAL_YOU = "material_you"

data class GaugePalette(
    val id: String,
    val label: String,
    val swatch: Color,
    val light: ColorScheme,
    val dark: ColorScheme,
)

private val Ink = Color(0xFF161410)
private val Paper = Color(0xFFF6F3EE)
private val Muted = Color(0xFF5C574C)

private fun palette(id: String, swatch: Color, darkPrimary: Color, lightPrimary: Color, secondaryDark: Color, secondaryLight: Color) =
    GaugePalette(
        id = id,
        label = id.replaceFirstChar { it.uppercase() },
        swatch = swatch,
        dark = darkColorScheme(
            primary = darkPrimary,
            onPrimary = Ink,
            secondary = secondaryDark,
            background = Color(0xFF121212),
            surface = Color(0xFF1A1A1A),
            surfaceVariant = Color(0xFF262626),
            onBackground = Color(0xFFF3EFE4),
            onSurface = Color(0xFFF3EFE4),
            onSurfaceVariant = Color(0xFFC9C3B4),
        ),
        light = lightColorScheme(
            primary = lightPrimary,
            onPrimary = Color.White,
            secondary = secondaryLight,
            background = Paper,
            surface = Color.White,
            surfaceVariant = Color(0xFFE8E2D6),
            onBackground = Ink,
            onSurface = Ink,
            onSurfaceVariant = Muted,
        ),
    )

val gaugePalettes = listOf(
    palette("amber", Color(0xFFE0A045), Color(0xFFE0A045), Color(0xFF8A5A12), Color(0xFFB7C4A4), Color(0xFF3E5230)),
    palette("teal", Color(0xFF3DB8A8), Color(0xFF5DCEC0), Color(0xFF0F6E62), Color(0xFFB7D0C8), Color(0xFF1F4A44)),
    palette("blue", Color(0xFF7EB6FF), Color(0xFF8EBAFF), Color(0xFF1D4E89), Color(0xFFC5D4EA), Color(0xFF243E62)),
    palette("green", Color(0xFF7DCA8A), Color(0xFF8FCB9A), Color(0xFF2E6B3A), Color(0xFFC5D9C4), Color(0xFF2A4630)),
    palette("violet", Color(0xFFC4B5FD), Color(0xFFC4B5FD), Color(0xFF5B3FA0), Color(0xFFD4CCE8), Color(0xFF3C3158)),
)

fun paletteById(id: String): GaugePalette = gaugePalettes.firstOrNull { it.id == id } ?: gaugePalettes.first()

fun knownThemeId(id: String): String {
    if (id == MATERIAL_YOU) return MATERIAL_YOU
    return paletteById(id).id
}

private fun ColorScheme.asAmoled(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceVariant = Color(0xFF141414),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color.Black,
    surfaceContainer = Color.Black,
    surfaceContainerHigh = Color(0xFF141414),
    surfaceContainerHighest = Color(0xFF1C1C1C),
)

private val GaugeShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
)

@Composable
fun ChargeGaugeTheme(
    themeId: String = "amber",
    amoled: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val materialYou = themeId == MATERIAL_YOU && Build.VERSION.SDK_INT >= 31
    var colors = when {
        materialYou && dark -> dynamicDarkColorScheme(context)
        materialYou -> dynamicLightColorScheme(context)
        dark -> paletteById(themeId).dark
        else -> paletteById(themeId).light
    }
    if (amoled && dark) colors = colors.asAmoled()
    MaterialTheme(colorScheme = colors, shapes = GaugeShapes, content = content)
}
