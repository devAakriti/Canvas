package com.example.canvas.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * ONE source of truth for every color in the app.
 * Same property names your code already uses, plus a few new ones.
 * (Delete your old `object CanvasEditorColors` and old `CanvasTheme` when you add this file.)
 */
object CanvasEditorColors {
    // Surfaces: three clearly separated depth levels
    val BackgroundDark = Color(0xFF0B0B0F)      // app background
    val SurfaceDark = Color(0xFF14141A)         // preview frame, timeline card
    val CardDark = Color(0xFF1C1C24)            // panels, inputs, unselected chips
    val CardDarkElevated = Color(0xFF262631)    // selected / pressed / raised
    val Outline = Color(0xFF34343F)             // 1dp borders & dividers

    // Text
    val TextPrimary = Color(0xFFF4F4F8)
    val TextSecondary = Color(0xFF9A9AAB)
    val TextTertiary = Color(0xFF6B6B7A)

    // Accent
    val AccentPurple = Color(0xFF8B5CF6)
    val AccentPurpleSoft = Color(0x338B5CF6)    // 20% tint for selected backgrounds

    // Status
    val DeleteBadge = Color(0xFFEF4444)
    val Success = Color(0xFF10B981)

    // Timeline tracks: muted, so the accent and the video thumbnails stand out
    val TrackVideo = Color(0xFF23233A)
    val TrackAudio = Color(0xFF17403F)
    val TrackText = Color(0xFF42331B)
    val TrackOverlay = Color(0xFF3F1F3B)
}

/** Spacing scale. Use these instead of random 6/10/12/14/20 values. */
object CanvasDimens {
    val ScreenPadding = 16.dp
    val Gap = 8.dp
    val GapLarge = 16.dp
    val PanelRadius = 20.dp
    val ControlRadius = 12.dp
    val MinTouch = 48.dp
}

private val CanvasColorScheme = darkColorScheme(
    primary = CanvasEditorColors.AccentPurple,
    onPrimary = Color.White,
    secondary = CanvasEditorColors.AccentPurple,
    onSecondary = Color.White,
    background = CanvasEditorColors.BackgroundDark,
    onBackground = CanvasEditorColors.TextPrimary,
    surface = CanvasEditorColors.SurfaceDark,
    onSurface = CanvasEditorColors.TextPrimary,
    surfaceVariant = CanvasEditorColors.CardDark,
    onSurfaceVariant = CanvasEditorColors.TextSecondary,
    surfaceContainer = CanvasEditorColors.CardDark,
    surfaceContainerHigh = CanvasEditorColors.CardDarkElevated,
    outline = CanvasEditorColors.Outline,
    outlineVariant = CanvasEditorColors.Outline,
    error = CanvasEditorColors.DeleteBadge
)

private val CanvasTypography = Typography(
    headlineMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 26.sp, letterSpacing = (-0.5).sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 20.sp, letterSpacing = (-0.3).sp),
    titleMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    titleSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 14.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 12.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 0.2.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.2.sp)
)

private val CanvasShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp)
)

/** Always dark: video editors look best (and are easiest on the eyes) in dark mode. */
@Composable
fun CanvasTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CanvasColorScheme,
        typography = CanvasTypography,
        shapes = CanvasShapes,
        content = content
    )
}