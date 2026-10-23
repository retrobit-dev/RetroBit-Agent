package dev.retrobit.agent.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import dev.retrobit.agent.R

/**
 * Retro cyberdeck palette: grayscale "e-paper" ink & paper, no hue.
 * Dark mode = black glass terminal, light mode = warm paper.
 */
object Palette {
    val Bg = Color(0xFF0A0A0A)
    val BgSoft = Color(0xFF0E0E0D)
    val Surface = Color(0xFF131312)
    val SurfaceHigh = Color(0xFF1B1B1A)
    val Stroke = Color(0xFF31312F)
    val StrokeSoft = Color(0xFF232322)

    val Primary = Color(0xFFDEDED5)
    val PrimaryDeep = Color(0xFFA9A9A1)
    val Accent = Color(0xFFF4F4EC)
    val Pink = Color(0xFFBDBDB4)

    val Text = Color(0xFFEAEAE2)
    val Muted = Color(0xFF8E8E86)

    val Success = Color(0xFFCFCFC6)
    val Warning = Color(0xFFEFEFE6)
    val Danger = Color(0xFF9C9C94)

    val LightBg = Color(0xFFEDEDE7)
    val LightSurface = Color(0xFFF8F8F2)
    val LightSurfaceHigh = Color(0xFFE3E3DB)
    val LightStroke = Color(0xFFC9C9C0)
    val LightText = Color(0xFF14140F)
    val LightMuted = Color(0xFF5F5F57)
    val LightPrimary = Color(0xFF1B1B16)

    /** Faint paper grain / scanline tone used by backgrounds. */
    val Ink = Color(0xFF000000)
    val Paper = Color(0xFFF4F4EC)
}

/** Pixel Operator (CC0) bundled in res/font. */
val PixelFont: FontFamily = FontFamily(
    Font(R.font.pixel_operator, FontWeight.Normal),
    Font(R.font.pixel_operator_bold, FontWeight.Bold),
)

/** Monospace role — Pixel Operator already sits on a pixel grid. */
val MonoFont: FontFamily = PixelFont

internal val DarkColors = darkColorScheme(
    primary = Palette.Primary,
    onPrimary = Color(0xFF0A0A0A),
    primaryContainer = Color(0xFF1E1E1C),
    onPrimaryContainer = Palette.Text,
    secondary = Palette.Accent,
    onSecondary = Color(0xFF0A0A0A),
    secondaryContainer = Palette.SurfaceHigh,
    onSecondaryContainer = Palette.Text,
    tertiary = Palette.Pink,
    onTertiary = Palette.Bg,
    tertiaryContainer = Palette.SurfaceHigh,
    onTertiaryContainer = Palette.Text,
    background = Palette.Bg,
    onBackground = Palette.Text,
    surface = Palette.Surface,
    onSurface = Palette.Text,
    surfaceVariant = Palette.SurfaceHigh,
    onSurfaceVariant = Palette.Muted,
    outline = Palette.Stroke,
    outlineVariant = Palette.StrokeSoft,
    error = Palette.Danger,
    onError = Color(0xFF0A0A0A),
    errorContainer = Palette.SurfaceHigh,
    onErrorContainer = Palette.Text,
    inverseSurface = Palette.LightSurface,
    inverseOnSurface = Palette.LightText,
    inversePrimary = Palette.LightPrimary,
    surfaceTint = Palette.Primary,
    surfaceBright = Palette.SurfaceHigh,
    surfaceDim = Palette.Bg,
    surfaceContainerLowest = Palette.Bg,
    surfaceContainerLow = Palette.Surface,
    surfaceContainer = Palette.Surface,
    surfaceContainerHigh = Palette.SurfaceHigh,
    surfaceContainerHighest = Palette.StrokeSoft,
)

internal val LightColors = lightColorScheme(
    primary = Palette.LightPrimary,
    onPrimary = Color(0xFFF8F8F2),
    primaryContainer = Color(0xFFDCDCD3),
    onPrimaryContainer = Color(0xFF14140F),
    secondary = Color(0xFF3A3A34),
    onSecondary = Color(0xFFF8F8F2),
    secondaryContainer = Palette.LightSurfaceHigh,
    onSecondaryContainer = Palette.LightText,
    tertiary = Color(0xFF4A4A43),
    onTertiary = Palette.LightSurface,
    tertiaryContainer = Palette.LightSurfaceHigh,
    onTertiaryContainer = Palette.LightText,
    background = Palette.LightBg,
    onBackground = Palette.LightText,
    surface = Palette.LightSurface,
    onSurface = Palette.LightText,
    surfaceVariant = Palette.LightSurfaceHigh,
    onSurfaceVariant = Palette.LightMuted,
    outline = Palette.LightStroke,
    outlineVariant = Color(0xFFDCDCD4),
    error = Color(0xFF3A3A34),
    onError = Color(0xFFF8F8F2),
    errorContainer = Palette.LightSurfaceHigh,
    onErrorContainer = Palette.LightText,
    inverseSurface = Palette.Surface,
    inverseOnSurface = Palette.Text,
    inversePrimary = Palette.Primary,
    surfaceTint = Palette.LightPrimary,
    surfaceBright = Palette.LightSurface,
    surfaceDim = Palette.LightBg,
    surfaceContainerLowest = Palette.LightSurface,
    surfaceContainerLow = Palette.LightBg,
    surfaceContainer = Palette.LightSurface,
    surfaceContainerHigh = Palette.LightSurfaceHigh,
    surfaceContainerHighest = Palette.LightStroke,
)

private val AppTypography = Typography(
    displayLarge = TextStyle(fontFamily = PixelFont, fontWeight = FontWeight.Bold, fontSize = 44.sp, lineHeight = 50.sp, letterSpacing = 0.sp),
    displayMedium = TextStyle(fontFamily = PixelFont, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = 0.sp),
    headlineLarge = TextStyle(fontFamily = PixelFont, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = 0.sp),
    headlineMedium = TextStyle(fontFamily = PixelFont, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 29.sp, letterSpacing = 0.sp),
    headlineSmall = TextStyle(fontFamily = PixelFont, fontWeight = FontWeight.Bold, fontSize = 19.sp, lineHeight = 26.sp, letterSpacing = 0.sp),
    titleLarge = TextStyle(fontFamily = PixelFont, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 23.sp, letterSpacing = 0.sp),
    titleMedium = TextStyle(fontFamily = PixelFont, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.sp),
    titleSmall = TextStyle(fontFamily = PixelFont, fontWeight = FontWeight.Normal, fontSize = 12.5.sp, lineHeight = 18.sp, letterSpacing = 0.sp),
    bodyLarge = TextStyle(fontFamily = PixelFont, fontWeight = FontWeight.Normal, fontSize = 14.5.sp, lineHeight = 22.sp, letterSpacing = 0.sp),
    bodyMedium = TextStyle(fontFamily = PixelFont, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 20.sp, letterSpacing = 0.sp),
    bodySmall = TextStyle(fontFamily = PixelFont, fontWeight = FontWeight.Normal, fontSize = 11.5.sp, lineHeight = 17.sp, letterSpacing = 0.sp),
    labelLarge = TextStyle(fontFamily = PixelFont, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, lineHeight = 17.sp, letterSpacing = 0.4.sp),
    labelMedium = TextStyle(fontFamily = PixelFont, fontWeight = FontWeight.Normal, fontSize = 11.5.sp, lineHeight = 16.sp, letterSpacing = 0.6.sp),
    labelSmall = TextStyle(fontFamily = PixelFont, fontWeight = FontWeight.Normal, fontSize = 10.sp, lineHeight = 14.sp, letterSpacing = 0.8.sp),
)

@Composable
fun RetroBitTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }
    MaterialTheme(
        colorScheme = colors,
        typography = AppTypography,
        shapes = Shapes(
            extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(2.dp),
            small = androidx.compose.foundation.shape.RoundedCornerShape(3.dp),
            medium = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
            large = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
            extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
        ),
        content = content,
    )
}
