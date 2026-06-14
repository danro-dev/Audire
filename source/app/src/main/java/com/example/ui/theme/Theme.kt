package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

private val LightColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = Color.White,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    secondary = Secondary,
    secondaryContainer = SecondaryContainer,
    onSecondaryContainer = OnSecondaryContainer,
    tertiary = Tertiary,
    tertiaryContainer = TertiaryContainer,
    onTertiaryContainer = OnTertiaryContainer,
    background = Background,
    onBackground = OnSurface,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceContainer,
    onSurfaceVariant = OnSurfaceVariant,
    outline = Outline,
    outlineVariant = OutlineVariant,
    error = Error,
    onError = OnError,
    errorContainer = ErrorContainer,
    onErrorContainer = OnErrorContainer
)

private val DarkColorScheme = darkColorScheme(
    primary = Primary,
    onPrimary = Color.White,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    background = Color(0xFF111318),
    onBackground = Color(0xFFE1E2EC),
    surface = Color(0xFF1A1C22),
    onSurface = Color(0xFFE1E2EC),
    surfaceVariant = Color(0xFF2E3036),
    onSurfaceVariant = Color(0xFFC2C6D1)
)

fun parseHexColor(hex: String, fallback: Color): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (e: Exception) {
        fallback
    }
}

private fun getLighterBg(color: Color): Color {
    return Color(
        red = (color.red + 0.08f).coerceAtMost(1f),
        green = (color.green + 0.08f).coerceAtMost(1f),
        blue = (color.blue + 0.10f).coerceAtMost(1f),
        alpha = 1f
    )
}

private fun getDarkerBg(color: Color): Color {
    return Color(
        red = (color.red - 0.06f).coerceAtLeast(0f),
        green = (color.green - 0.06f).coerceAtLeast(0f),
        blue = (color.blue - 0.04f).coerceAtLeast(0f),
        alpha = 1f
    )
}

private fun buildDynamicColorScheme(
    primaryColor: Color,
    bgColor: Color,
    secondaryColor: Color
): ColorScheme {
    val isLight = bgColor.luminance() > 0.45f
    val textColor = if (isLight) Color(0xFF1A1C22) else Color(0xFFE1E2EC)
    val textVariantColor = if (isLight) Color(0xFF43474E) else Color(0xFFC2C6D1)
    val containerColor = if (isLight) getDarkerBg(bgColor) else getLighterBg(bgColor)

    return if (isLight) {
        lightColorScheme(
            primary = primaryColor,
            onPrimary = if (primaryColor.luminance() > 0.5f) Color.Black else Color.White,
            primaryContainer = primaryColor.copy(alpha = 0.15f),
            onPrimaryContainer = primaryColor,
            secondary = secondaryColor,
            secondaryContainer = containerColor,
            onSecondaryContainer = textColor,
            background = bgColor,
            onBackground = textColor,
            surface = bgColor,
            onSurface = textColor,
            surfaceVariant = containerColor,
            onSurfaceVariant = textVariantColor,
            outline = textVariantColor.copy(alpha = 0.4f),
            outlineVariant = textVariantColor.copy(alpha = 0.2f),
            error = Color(0xFFBA1A1A),
            onError = Color.White
        )
    } else {
        darkColorScheme(
            primary = primaryColor,
            onPrimary = if (primaryColor.luminance() > 0.5f) Color.Black else Color.White,
            primaryContainer = primaryColor.copy(alpha = 0.3f),
            onPrimaryContainer = Color.White,
            secondary = secondaryColor,
            secondaryContainer = containerColor,
            onSecondaryContainer = textColor,
            background = bgColor,
            onBackground = textColor,
            surface = bgColor,
            onSurface = textColor,
            surfaceVariant = containerColor,
            onSurfaceVariant = textVariantColor,
            outline = textVariantColor.copy(alpha = 0.4f),
            outlineVariant = textVariantColor.copy(alpha = 0.2f),
            error = Color(0xFFBA1A1A),
            onError = Color.White
        )
    }
}

@Composable
fun MyApplicationTheme(
    themeMode: String = "dark",
    customPrimaryHex: String = "#0061A4",
    customBgHex: String = "#111318",
    customSecondaryHex: String = "#43474E",
    content: @Composable () -> Unit
) {
    val colorScheme = when (themeMode) {
        "light" -> LightColorScheme
        "dark" -> DarkColorScheme
        "preset_peach" -> buildDynamicColorScheme(
            primaryColor = Color(0xFFFA5B4A),
            bgColor = Color(0xFF261D1C),
            secondaryColor = Color(0xFF8B4F30)
        )
        "preset_ocean" -> buildDynamicColorScheme(
            primaryColor = Color(0xFF00ADB5),
            bgColor = Color(0xFF0D1B2A),
            secondaryColor = Color(0xFF1B4965)
        )
        "preset_emerald" -> buildDynamicColorScheme(
            primaryColor = Color(0xFF2ECC71),
            bgColor = Color(0xFF0F1E15),
            secondaryColor = Color(0xFF27AE60)
        )
        "preset_cosmic" -> buildDynamicColorScheme(
            primaryColor = Color(0xFF9B5DE5),
            bgColor = Color(0xFF0F0C1B),
            secondaryColor = Color(0xFF5A189A)
        )
        "custom" -> {
            val p = parseHexColor(customPrimaryHex, Primary)
            val bg = parseHexColor(customBgHex, Color(0xFF111318))
            val s = parseHexColor(customSecondaryHex, Secondary)
            buildDynamicColorScheme(p, bg, s)
        }
        else -> DarkColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
