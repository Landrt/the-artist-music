package com.theartist.music.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.theartist.music.platform.appFontFamily

// Exact dark OLED + Crimson palette matched from user screenshots
val ArtistCrimson = Color(0xFFF81E49)
val ArtistCrimsonDim = Color(0xFF42121D)
val ArtistSplashBlue = Color(0xFF4F6EF7)
val ArtistSplashPurple = Color(0xFFC875F5)
val ArtistSplashCyan = Color(0xFF56B4FF)

val OledBlack = Color(0xFF000000)
val MiniPlayerSurfaceDark = Color(0xFF141414)
val ElevatedDark = Color(0xFF1A1A1A)
val HairlineDark = Color(0xFF181818)
val PrimaryTextDark = Color(0xFFEAEAEA)
val SecondaryTextDark = Color(0xFF828282)
val MutedIconDark = Color(0xFF8C8C8C)

private val DarkColorScheme = darkColorScheme(
    primary = ArtistCrimson,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF2B0E16),
    onPrimaryContainer = Color(0xFFFF7A96),
    secondary = ArtistSplashBlue,
    onSecondary = Color.White,
    background = OledBlack,
    onBackground = PrimaryTextDark,
    surface = ElevatedDark,
    onSurface = PrimaryTextDark,
    surfaceVariant = Color(0xFF1C1C1E),
    onSurfaceVariant = SecondaryTextDark,
    outlineVariant = HairlineDark
)

private val LightColorScheme = lightColorScheme(
    primary = ArtistCrimson,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE5EB),
    onPrimaryContainer = ArtistCrimson,
    secondary = ArtistSplashBlue,
    onSecondary = Color.White,
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF141414),
    surface = Color(0xFFF7F7F7),
    onSurface = Color(0xFF141414),
    surfaceVariant = Color(0xFFEEEEEE),
    onSurfaceVariant = Color(0xFF6E6E6E),
    outlineVariant = Color(0xFFE6E6E6)
)

@Composable
fun TheArtistMusicTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    val fontFamily = remember { appFontFamily() }
    val typography = remember(fontFamily) {
        Typography(
            displayLarge = TextStyle(fontFamily = fontFamily, fontSize = 36.sp, fontWeight = FontWeight.Bold),
            displayMedium = TextStyle(fontFamily = fontFamily, fontSize = 30.sp, fontWeight = FontWeight.Bold),
            headlineSmall = TextStyle(fontFamily = fontFamily, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
            titleLarge = TextStyle(fontFamily = fontFamily, fontSize = 20.sp, fontWeight = FontWeight.Medium),
            titleMedium = TextStyle(fontFamily = fontFamily, fontSize = 16.sp, fontWeight = FontWeight.Medium),
            titleSmall = TextStyle(fontFamily = fontFamily, fontSize = 14.sp, fontWeight = FontWeight.Medium),
            bodyLarge = TextStyle(fontFamily = fontFamily, fontSize = 15.5.sp, fontWeight = FontWeight.Normal),
            bodyMedium = TextStyle(fontFamily = fontFamily, fontSize = 13.sp, fontWeight = FontWeight.Normal),
            bodySmall = TextStyle(fontFamily = fontFamily, fontSize = 12.sp, fontWeight = FontWeight.Normal),
            labelLarge = TextStyle(fontFamily = fontFamily, fontSize = 14.sp, fontWeight = FontWeight.Medium),
            labelMedium = TextStyle(fontFamily = fontFamily, fontSize = 12.sp, fontWeight = FontWeight.Normal),
            labelSmall = TextStyle(fontFamily = fontFamily, fontSize = 10.sp, fontWeight = FontWeight.Medium)
        )
    }

    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = typography,
        content = content
    )
}
