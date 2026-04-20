package com.midas.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import midasapp.composeapp.generated.resources.*
import org.jetbrains.compose.resources.Font

// Brand accents (stable across modes)
val MidasGreen = Color(0xFF00E676)
val MidasGreenDark = Color(0xFF00C853)
val MidasGreenSubtle = Color(0xFF1B5E20)

// Dark mode tokens
val MidasDarkBg = Color(0xFF0D0D0D)
val MidasDarkSurface = Color(0xFF1A1A1A)
val MidasDarkCard = Color(0xFF1E1E1E)
val MidasDarkCardBorder = Color(0xFF2A2A2A)
val MidasGray = Color(0xFF9E9E9E)
val MidasLightGray = Color(0xFFE0E0E0)

// Light mode tokens — aligned with Claude Design Variant A
val MidasLightBg = Color(0xFFF6F6F4)
val MidasLightSurface = Color(0xFFFFFFFF)
val MidasLightCard = Color(0xFFFFFFFF)
val MidasLightCardBorder = Color(0xFFE5E5E0)
val MidasLightMuted = Color(0xFF6B6B6B)
val MidasLightTextPrimary = Color(0xFF111111)
val MidasGreenLight = Color(0xFF00A859)

val MidasOrange = Color(0xFFFF9800)
val MidasBlue = Color(0xFF42A5F5)
val MidasPurple = Color(0xFFB388FF)

private val DarkColorScheme = darkColorScheme(
    primary = MidasGreen,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF1A3D2A),
    onPrimaryContainer = MidasGreen,
    secondary = MidasGreenDark,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF1A3D2A),
    onSecondaryContainer = MidasGreen,
    tertiary = MidasBlue,
    onTertiary = Color.Black,
    tertiaryContainer = Color(0xFF1A2D3D),
    onTertiaryContainer = MidasBlue,
    background = MidasDarkBg,
    onBackground = Color.White,
    surface = MidasDarkSurface,
    onSurface = Color.White,
    surfaceVariant = MidasDarkCard,
    onSurfaceVariant = MidasGray,
    outline = MidasDarkCardBorder,
    outlineVariant = Color(0xFF333333),
    error = Color(0xFFEF5350),
    onError = Color.White,
    errorContainer = Color(0xFF3D1A1A),
    onErrorContainer = Color(0xFFEF5350),
    inverseSurface = Color.White,
    inverseOnSurface = Color.Black,
    surfaceTint = MidasGreen,
)

private val LightColorScheme = lightColorScheme(
    primary = MidasGreenLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6F5E3),
    onPrimaryContainer = Color(0xFF0B4A27),
    secondary = MidasGreenDark,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD6F5E3),
    onSecondaryContainer = Color(0xFF0B4A27),
    tertiary = MidasBlue,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDBEBFA),
    onTertiaryContainer = Color(0xFF0A2A4A),
    background = MidasLightBg,
    onBackground = MidasLightTextPrimary,
    surface = MidasLightSurface,
    onSurface = MidasLightTextPrimary,
    surfaceVariant = MidasLightCard,
    onSurfaceVariant = MidasLightMuted,
    outline = MidasLightCardBorder,
    outlineVariant = Color(0xFFECEEEA),
    error = Color(0xFFD32F2F),
    onError = Color.White,
    errorContainer = Color(0xFFFDE7E7),
    onErrorContainer = Color(0xFF8A1A1A),
    inverseSurface = MidasLightTextPrimary,
    inverseOnSurface = Color.White,
    surfaceTint = MidasGreenLight,
)

// Extra semantic tokens that don't fit cleanly into Material color scheme.
// Components read these via LocalMidasColors so a single switch flips everything.
data class MidasColors(
    val isDark: Boolean,
    val bg: Color,
    val textPrimary: Color,
    val backgroundGradientTop: Color,
    val backgroundGradientBottom: Color,
    val cardBackground: Color,
    val cardBorder: Color,
    val muted: Color,
    val subtleMuted: Color,
    val primaryAccent: Color,
    val primaryAccentOn: Color,
    val statusPositive: Color,
    val statusNegative: Color,
    val tickerBackground: Color,
    val inputBackground: Color,
    val pasteCardBackground: Color,
)

private val DarkMidasColors = MidasColors(
    isDark = true,
    bg = Color(0xFF0D0D0D),
    textPrimary = Color.White,
    backgroundGradientTop = Color(0xFF0A0F0B),
    backgroundGradientBottom = MidasDarkBg,
    cardBackground = Color(0xFF121512),
    cardBorder = MidasDarkCardBorder,
    muted = MidasGray,
    subtleMuted = Color(0xFF6E7570),
    primaryAccent = MidasGreen,
    primaryAccentOn = Color.Black,
    statusPositive = MidasGreen,
    statusNegative = Color(0xFFEF5350),
    tickerBackground = Color(0xFF121512),
    inputBackground = Color(0xFF121512),
    pasteCardBackground = Color(0xFF0E1A13),
)

private val LightMidasColors = MidasColors(
    isDark = false,
    bg = MidasLightBg,
    textPrimary = MidasLightTextPrimary,
    backgroundGradientTop = Color(0xFFE8F5EB),
    backgroundGradientBottom = MidasLightBg,
    cardBackground = MidasLightCard,
    cardBorder = MidasLightCardBorder,
    muted = MidasLightMuted,
    subtleMuted = Color(0xFF9AA0A6),
    primaryAccent = MidasGreenLight,
    primaryAccentOn = Color.White,
    statusPositive = MidasGreenLight,
    statusNegative = Color(0xFFD0342C),
    tickerBackground = Color(0xFFFFFFFF),
    inputBackground = Color(0xFFFAFAF8),
    pasteCardBackground = Color(0xFFF1FBF4),
)

val LocalMidasColors = compositionLocalOf { DarkMidasColors }

private val MidasShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
private fun InterFontFamily() = FontFamily(
    Font(Res.font.inter_regular, FontWeight.Normal),
    Font(Res.font.inter_medium, FontWeight.Medium),
    Font(Res.font.inter_semibold, FontWeight.SemiBold),
    Font(Res.font.inter_bold, FontWeight.Bold),
)

@Composable
private fun MidasTypography(): Typography {
    val inter = InterFontFamily()
    return Typography(
        displayLarge = TextStyle(
            fontFamily = inter,
            fontWeight = FontWeight.Bold,
            fontSize = 57.sp,
            lineHeight = 64.sp,
            letterSpacing = (-0.25).sp,
        ),
        displayMedium = TextStyle(
            fontFamily = inter,
            fontWeight = FontWeight.Bold,
            fontSize = 45.sp,
            lineHeight = 52.sp,
        ),
        displaySmall = TextStyle(
            fontFamily = inter,
            fontWeight = FontWeight.Bold,
            fontSize = 36.sp,
            lineHeight = 44.sp,
        ),
        headlineLarge = TextStyle(
            fontFamily = inter,
            fontWeight = FontWeight.Bold,
            fontSize = 32.sp,
            lineHeight = 40.sp,
        ),
        headlineMedium = TextStyle(
            fontFamily = inter,
            fontWeight = FontWeight.SemiBold,
            fontSize = 28.sp,
            lineHeight = 36.sp,
        ),
        headlineSmall = TextStyle(
            fontFamily = inter,
            fontWeight = FontWeight.SemiBold,
            fontSize = 24.sp,
            lineHeight = 32.sp,
        ),
        titleLarge = TextStyle(
            fontFamily = inter,
            fontWeight = FontWeight.SemiBold,
            fontSize = 22.sp,
            lineHeight = 28.sp,
        ),
        titleMedium = TextStyle(
            fontFamily = inter,
            fontWeight = FontWeight.Medium,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            letterSpacing = 0.15.sp,
        ),
        titleSmall = TextStyle(
            fontFamily = inter,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.1.sp,
        ),
        bodyLarge = TextStyle(
            fontFamily = inter,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            letterSpacing = 0.5.sp,
        ),
        bodyMedium = TextStyle(
            fontFamily = inter,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.25.sp,
        ),
        bodySmall = TextStyle(
            fontFamily = inter,
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.4.sp,
        ),
        labelLarge = TextStyle(
            fontFamily = inter,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.1.sp,
        ),
        labelMedium = TextStyle(
            fontFamily = inter,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.5.sp,
        ),
        labelSmall = TextStyle(
            fontFamily = inter,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.5.sp,
        ),
    )
}

@Composable
fun MidasTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val midasColors = if (darkTheme) DarkMidasColors else LightMidasColors
    CompositionLocalProvider(LocalMidasColors provides midasColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = MidasTypography(),
            shapes = MidasShapes,
            content = content,
        )
    }
}
