package app.screenbalance.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.screenbalance.model.ThemePreference

/*
 * Design language: printed matter, not a dashboard. Warm paper, ink, one ember accent used
 * sparingly (the full stop in the logo, active states, the number that matters). One typeface,
 * the system sans: semibold for headlines and figures, regular for everything you read or tap.
 */

private object Palette {
    val paper = Color(0xFFF4F1EA)
    val paperDeep = Color(0xFFEBE6DB)
    val paperCard = Color(0xFFFBF9F4)
    val ink = Color(0xFF1A1A17)
    val inkSoft = Color(0xFF5E5B52)
    val inkFaint = Color(0xFF8D897D)
    val rule = Color(0xFFD9D3C5)
    val ember = Color(0xFFE0532B)
    val moss = Color(0xFF4E7A5A)

    val night = Color(0xFF121210)
    val nightCard = Color(0xFF1B1B18)
    val nightRaised = Color(0xFF24241F)
    val nightText = Color(0xFFEDE9E0)
    val nightSoft = Color(0xFFA9A497)
    val nightRule = Color(0xFF34332D)
    val emberNight = Color(0xFFF0754C)
    val mossNight = Color(0xFF8CBF97)
}

private val LightColors = lightColorScheme(
    primary = Palette.ink,
    onPrimary = Palette.paper,
    primaryContainer = Palette.paperDeep,
    onPrimaryContainer = Palette.ink,
    secondary = Palette.ember,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF8DED3),
    onSecondaryContainer = Color(0xFF4A170A),
    tertiary = Palette.moss,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDDE8DC),
    onTertiaryContainer = Color(0xFF16301E),
    background = Palette.paper,
    onBackground = Palette.ink,
    surface = Palette.paper,
    onSurface = Palette.ink,
    surfaceVariant = Palette.paperDeep,
    onSurfaceVariant = Palette.inkSoft,
    surfaceContainerLowest = Palette.paperCard,
    surfaceContainerLow = Palette.paperCard,
    surfaceContainer = Palette.paperCard,
    surfaceContainerHigh = Palette.paperDeep,
    surfaceContainerHighest = Color(0xFFE3DDD0),
    inverseSurface = Palette.ink,
    inverseOnSurface = Palette.paper,
    outline = Palette.inkFaint,
    outlineVariant = Palette.rule,
    error = Color(0xFFB3261E),
    scrim = Color(0x99000000),
)

private val DarkColors = darkColorScheme(
    primary = Palette.nightText,
    onPrimary = Palette.night,
    primaryContainer = Palette.nightRaised,
    onPrimaryContainer = Palette.nightText,
    secondary = Palette.emberNight,
    onSecondary = Color(0xFF2A0D04),
    secondaryContainer = Color(0xFF4A2214),
    onSecondaryContainer = Color(0xFFFFDBCD),
    tertiary = Palette.mossNight,
    onTertiary = Color(0xFF0E2615),
    tertiaryContainer = Color(0xFF223A28),
    onTertiaryContainer = Color(0xFFD3EBD6),
    background = Palette.night,
    onBackground = Palette.nightText,
    surface = Palette.night,
    onSurface = Palette.nightText,
    surfaceVariant = Palette.nightRaised,
    onSurfaceVariant = Palette.nightSoft,
    surfaceContainerLowest = Palette.nightCard,
    surfaceContainerLow = Palette.nightCard,
    surfaceContainer = Palette.nightCard,
    surfaceContainerHigh = Palette.nightRaised,
    surfaceContainerHighest = Color(0xFF2C2C26),
    inverseSurface = Palette.nightText,
    inverseOnSurface = Palette.night,
    outline = Color(0xFF6E6A5F),
    outlineVariant = Palette.nightRule,
    error = Color(0xFFF2B8B5),
    scrim = Color(0xCC000000),
)

private val AppTypography = Typography().run {
    copy(
        displayLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 56.sp, lineHeight = 60.sp, letterSpacing = (-2).sp),
        displayMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 44.sp, lineHeight = 48.sp, letterSpacing = (-1.5).sp),
        displaySmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-1).sp),
        headlineLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.6).sp),
        headlineMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.4).sp),
        headlineSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.2).sp),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.Medium, fontSize = 20.sp),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.Medium),
        titleSmall = titleSmall.copy(fontWeight = FontWeight.Medium),
        bodyLarge = bodyLarge.copy(lineHeight = 24.sp, letterSpacing = 0.1.sp),
        bodyMedium = bodyMedium.copy(lineHeight = 21.sp, letterSpacing = 0.1.sp),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp),
        labelMedium = labelMedium.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
        labelSmall = labelSmall.copy(fontWeight = FontWeight.Medium, letterSpacing = 1.2.sp),
    )
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Immutable
data class Accents(val ember: Color, val moss: Color, val rule: Color, val faint: Color)

val LocalAccents = staticCompositionLocalOf {
    Accents(Palette.ember, Palette.moss, Palette.rule, Palette.inkFaint)
}

@Composable
fun isDarkTheme(preference: ThemePreference): Boolean = when (preference) {
    ThemePreference.SYSTEM -> isSystemInDarkTheme()
    ThemePreference.LIGHT -> false
    ThemePreference.DARK -> true
}

@Composable
fun GlanceTheme(preference: ThemePreference = ThemePreference.SYSTEM, content: @Composable () -> Unit) {
    val dark = isDarkTheme(preference)
    val accents = if (dark) {
        Accents(Palette.emberNight, Palette.mossNight, Palette.nightRule, Color(0xFF7D796D))
    } else {
        Accents(Palette.ember, Palette.moss, Palette.rule, Palette.inkFaint)
    }
    val colors = if (dark) DarkColors else LightColors
    CompositionLocalProvider(LocalAccents provides accents) {
        MaterialTheme(colorScheme = colors, typography = AppTypography, shapes = AppShapes) {
            // Compose's default content colour is black. Screens drawn outside a Scaffold/Surface
            // (onboarding, the intervention overlay) would otherwise render black text in dark mode.
            CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides colors.onBackground, content = content)
        }
    }
}

/** Small caps-style label used above sections, like a magazine kicker. */
val Kicker: TextStyle
    @Composable get() = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp)
