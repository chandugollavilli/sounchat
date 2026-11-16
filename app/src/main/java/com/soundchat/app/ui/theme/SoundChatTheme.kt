package com.soundchat.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

val ObsidianBlack = Color(0xFF070B0E)
val SurfaceDarkGrey = Color(0xFF0F161C)
val SurfaceElevatedGrey = Color(0xFF162029)
val SurfaceCardBorder = Color(0xFF21313F)
val UltrasonicCyan = Color(0xFF00E5FF)
val UltrasonicEmerald = Color(0xFF00E676)
val AmberWarning = Color(0xFFFFB300)
val CoralError = Color(0xFFFF5252)
val TextPrimaryWhite = Color(0xFFECF4F8)
val TextSecondaryMuted = Color(0xFF8EA4B5)

val SpaceGroteskFontFamily = FontFamily(
    Font(R.font.space_grotesk, FontWeight.Normal),
    Font(R.font.space_grotesk, FontWeight.Medium),
    Font(R.font.space_grotesk, FontWeight.Bold)
)

val JetBrainsMonoFontFamily = FontFamily(
    Font(R.font.jetbrains_mono, FontWeight.Normal),
    Font(R.font.jetbrains_mono, FontWeight.Medium),
    Font(R.font.jetbrains_mono, FontWeight.Bold)
)

private val SoundChatDarkColorScheme = darkColorScheme(
    primary = UltrasonicCyan,
    onPrimary = ObsidianBlack,
    primaryContainer = Color(0xFF003842),
    onPrimaryContainer = UltrasonicCyan,
    secondary = UltrasonicEmerald,
    onSecondary = ObsidianBlack,
    secondaryContainer = Color(0xFF00391A),
    onSecondaryContainer = UltrasonicEmerald,
    tertiary = AmberWarning,
    onTertiary = ObsidianBlack,
    error = CoralError,
    background = ObsidianBlack,
    onBackground = TextPrimaryWhite,
    surface = SurfaceDarkGrey,
    onSurface = TextPrimaryWhite,
    surfaceVariant = SurfaceElevatedGrey,
    onSurfaceVariant = TextSecondaryMuted,
    outline = SurfaceCardBorder
)

val SoundChatTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = SpaceGroteskFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = SpaceGroteskFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleLarge = TextStyle(
        fontFamily = SpaceGroteskFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),
    titleMedium = TextStyle(
        fontFamily = SpaceGroteskFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 20.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = SpaceGroteskFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = SpaceGroteskFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp
    ),
    labelLarge = TextStyle(
        fontFamily = JetBrainsMonoFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),
    labelMedium = TextStyle(
        fontFamily = JetBrainsMonoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp
    ),
    labelSmall = TextStyle(
        fontFamily = JetBrainsMonoFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 10.sp,
        lineHeight = 14.sp
    )
)

@Composable
fun SoundChatTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SoundChatDarkColorScheme,
        typography = SoundChatTypography,
        content = content
    )
}
