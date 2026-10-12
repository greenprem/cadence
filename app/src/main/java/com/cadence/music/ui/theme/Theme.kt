package com.cadence.music.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.cadence.music.data.ThemeMode

val AccentColors = listOf(
    Color(0xFFFA2D48), // Apple Music red
    Color(0xFFFF375F),
    Color(0xFFFF9F0A),
    Color(0xFF30D158),
    Color(0xFF0A84FF),
    Color(0xFF5E5CE6),
    Color(0xFFBF5AF2),
)
val AccentNames = listOf("Red", "Pink", "Orange", "Green", "Blue", "Indigo", "Purple")

@Immutable
data class ExtraColors(
    val groupedBackground: Color,
    val groupedCell: Color,
    val secondaryText: Color,
    val tertiaryText: Color,
    val separator: Color,
    val fill: Color,
    val placeholder: Color,
    val bar: Color,
    val isDark: Boolean,
)

private val LightExtra = ExtraColors(
    groupedBackground = Color(0xFFF2F2F7),
    groupedCell = Color.White,
    secondaryText = Color(0xFF8A8A8E),
    tertiaryText = Color(0xFFC4C4C6),
    separator = Color(0xFFDCDCDF),
    fill = Color(0xFFEFEFF2),
    placeholder = Color(0xFFE3E3E8),
    bar = Color(0xF7F9F9F9),
    isDark = false,
)

private val DarkExtra = ExtraColors(
    groupedBackground = Color.Black,
    groupedCell = Color(0xFF1C1C1E),
    secondaryText = Color(0xFF98989F),
    tertiaryText = Color(0xFF6C6C70),
    separator = Color(0xFF2C2C2E),
    fill = Color(0xFF1C1C1E),
    placeholder = Color(0xFF2C2C2E),
    bar = Color(0xF2161616),
    isDark = true,
)

val LocalExtraColors = staticCompositionLocalOf { LightExtra }

object Cadence {
    val colors: ExtraColors
        @Composable get() = LocalExtraColors.current
}

private val AppTypography = Typography().let { t ->
    t.copy(
        headlineLarge = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.2.sp),
        titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
        titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = TextStyle(fontSize = 16.sp),
        bodyMedium = TextStyle(fontSize = 14.sp),
        labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium),
    )
}

@Composable
fun CadenceTheme(themeMode: ThemeMode, accentIndex: Int, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val accent = AccentColors.getOrElse(accentIndex) { AccentColors[0] }
    val scheme = if (dark) {
        darkColorScheme(
            primary = accent,
            onPrimary = Color.White,
            secondary = accent,
            tertiary = accent,
            background = Color.Black,
            onBackground = Color.White,
            surface = Color.Black,
            onSurface = Color.White,
            surfaceVariant = Color(0xFF1C1C1E),
            onSurfaceVariant = Color(0xFF8D8D93),
            surfaceContainer = Color(0xFF1C1C1E),
            surfaceContainerHigh = Color(0xFF2C2C2E),
            surfaceContainerHighest = Color(0xFF3A3A3C),
            outline = Color(0xFF38383A),
            outlineVariant = Color(0xFF2C2C2E),
        )
    } else {
        lightColorScheme(
            primary = accent,
            onPrimary = Color.White,
            secondary = accent,
            tertiary = accent,
            background = Color.White,
            onBackground = Color.Black,
            surface = Color.White,
            onSurface = Color.Black,
            surfaceVariant = Color(0xFFF2F2F7),
            onSurfaceVariant = Color(0xFF8A8A8E),
            surfaceContainer = Color(0xFFF2F2F7),
            surfaceContainerHigh = Color(0xFFF7F7F9),
            surfaceContainerHighest = Color(0xFFEDEDF0),
            outline = Color(0xFFC6C6C8),
            outlineVariant = Color(0xFFE5E5EA),
        )
    }
    CompositionLocalProvider(LocalExtraColors provides if (dark) DarkExtra else LightExtra) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography) {
            // Text and icons without an explicit color follow the theme (white in dark mode). Without
            // this they fall back to black, which is invisible on the dark background.
            CompositionLocalProvider(LocalContentColor provides scheme.onBackground, content = content)
        }
    }
}
