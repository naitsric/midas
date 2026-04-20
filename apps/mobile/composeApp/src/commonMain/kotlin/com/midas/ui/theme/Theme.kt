package com.midas.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import midasapp.composeapp.generated.resources.*
import org.jetbrains.compose.resources.Font

// Brand colors
val MidasGreen = Color(0xFF00E676)
val MidasGreenDark = Color(0xFF00C853)
val MidasGreenSubtle = Color(0xFF1B5E20)
val MidasDarkBg = Color(0xFF0D0D0D)
val MidasDarkSurface = Color(0xFF1A1A1A)
val MidasDarkCard = Color(0xFF1E1E1E)
val MidasDarkCardBorder = Color(0xFF2A2A2A)
val MidasGray = Color(0xFF9E9E9E)
val MidasLightGray = Color(0xFFE0E0E0)
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
fun MidasTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = MidasTypography(),
        shapes = MidasShapes,
        content = content,
    )
}
