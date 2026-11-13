package com.hblaze.apexy.ui.neu

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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.hblaze.apexy.data.AppSettings
import com.hblaze.apexy.data.ThemeMode

/** Every neumorphic colour is derived from one surface so shadows always match the background. */
@Immutable
data class NeuColors(
    val isDark: Boolean,
    val surface: Color,
    val shadowDark: Color,
    val shadowLight: Color,
    val text: Color,
    val textMuted: Color,
    val primary: Color,
    val onPrimary: Color,
    val danger: Color,
    val success: Color,
) {
    /** The primary colour nudged until it reads clearly as text or an icon on [surface]. */
    val accent: Color
        get() = when {
            isDark && primary.luminance() < 0.32f -> androidx.compose.ui.graphics.lerp(primary, Color.White, 0.38f)
            !isDark && primary.luminance() > 0.42f -> androidx.compose.ui.graphics.lerp(primary, Color.Black, 0.28f)
            else -> primary
        }
}

fun lightNeu(primary: Color) = NeuColors(
    isDark = false,
    surface = Color(0xFFE4E8EE),
    shadowDark = Color(0xFFA3B1C6).copy(alpha = 0.75f),
    shadowLight = Color.White.copy(alpha = 0.95f),
    text = Color(0xFF2B3240),
    textMuted = Color(0xFF606B80),
    primary = primary,
    onPrimary = if (primary.luminance() > 0.55f) Color(0xFF1B1F27) else Color.White,
    danger = Color(0xFFD9485F),
    success = Color(0xFF2E9E6B),
)

fun darkNeu(primary: Color) = NeuColors(
    isDark = true,
    surface = Color(0xFF262A31),
    shadowDark = Color(0xFF0F1114).copy(alpha = 0.85f),
    shadowLight = Color(0xFF3A3F48).copy(alpha = 0.7f),
    text = Color(0xFFF1F3F7),
    textMuted = Color(0xFFADB5C3),
    primary = primary,
    onPrimary = if (primary.luminance() > 0.55f) Color(0xFF1B1F27) else Color.White,
    danger = Color(0xFFFF6B81),
    success = Color(0xFF4FD19A),
)

val PrimaryPresets = listOf(
    0xFF5B6CF0 to "Indigo",
    0xFF14B8A6 to "Teal",
    0xFFFF6F61 to "Coral",
    0xFFF5A524 to "Amber",
    0xFF8B5CF6 to "Violet",
    0xFF10B981 to "Emerald",
    0xFFF43F72 to "Rose",
    0xFF38BDF8 to "Sky",
)

/** The same palette with a different accent, e.g. one alarm's own colour. */
fun NeuColors.withAccent(argb: Long): NeuColors {
    if (argb == 0L) return this
    val p = Color(argb)
    return copy(primary = p, onPrimary = if (p.luminance() > 0.55f) Color(0xFF1B1F27) else Color.White)
}

@Composable
fun WithAccent(argb: Long, content: @Composable () -> Unit) {
    val c = LocalNeu.current
    CompositionLocalProvider(LocalNeu provides c.withAccent(argb), content = content)
}

val LocalNeu = staticCompositionLocalOf { lightNeu(Color(0xFF5B6CF0)) }

object Neu {
    val colors: NeuColors @Composable get() = LocalNeu.current
}

@Composable
fun isDarkFor(settings: AppSettings) = when (settings.themeMode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

private val ApexyTypography = Typography(
    displayLarge = TextStyle(fontSize = 64.sp, fontWeight = FontWeight.Light, letterSpacing = (-1).sp),
    displayMedium = TextStyle(fontSize = 44.sp, fontWeight = FontWeight.Light),
    headlineMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.3.sp),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
)

@Composable
fun ApexyTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val primary = Color(settings.primaryColor)
    val neu = if (isDarkFor(settings)) darkNeu(primary) else lightNeu(primary)
    val scheme = if (neu.isDark) {
        darkColorScheme(primary = primary, onPrimary = neu.onPrimary, background = neu.surface, surface = neu.surface, onSurface = neu.text, onBackground = neu.text)
    } else {
        lightColorScheme(primary = primary, onPrimary = neu.onPrimary, background = neu.surface, surface = neu.surface, onSurface = neu.text, onBackground = neu.text)
    }
    MaterialTheme(colorScheme = scheme, typography = ApexyTypography) {
        CompositionLocalProvider(LocalNeu provides neu, LocalContentColor provides neu.text, content = content)
    }
}
