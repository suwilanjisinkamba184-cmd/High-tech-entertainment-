package com.hightechentertainment.app.ui.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object HteColors {
    val Obsidian = Color(0xFF090909)
    val Panel = Color(0xFF151515)
    val PanelRaised = Color(0xFF202020)
    val Hairline = Color(0xFF393939)
    val Gold = Color(0xFFE5C158)
    val Silver = Color(0xFFC4CAD2)
    val Muted = Color(0xFF8B929D)
    val Cyan = Color(0xFF4FD1E8)
    val NeonLime = Color(0xFFD7F36A)
    val Violet = Color(0xFF9B5DE5)
    val Magenta = Color(0xFFE45BBF)
    val PinkGlow = Color(0xFFFF75CF)
    val Danger = Color(0xFFFF8A80)
    val OnDark = Color(0xFFF2F2F2)
}
object HteFonts {
    val heading: FontFamily = FontFamily.SansSerif
    val body: FontFamily = FontFamily.SansSerif
}
val HteTypography = Typography(
    headlineLarge = TextStyle(fontFamily = HteFonts.heading, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 36.sp),
    headlineMedium = TextStyle(fontFamily = HteFonts.heading, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontFamily = HteFonts.heading, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp),
    titleMedium = TextStyle(fontFamily = HteFonts.body, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = HteFonts.body, fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = HteFonts.body, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = HteFonts.body, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = HteFonts.body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = HteFonts.body, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = HteFonts.body, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp),
)
val HteShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp), small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp), large = RoundedCornerShape(20.dp), extraLarge = RoundedCornerShape(28.dp),
)
val LocalHteReduceMotion = staticCompositionLocalOf { false }

@Composable
fun HteTheme(reduceMotion: Boolean = false, content: @Composable () -> Unit) {
    val colors = darkColorScheme(
        primary = HteColors.Gold, onPrimary = HteColors.Obsidian,
        secondary = HteColors.Gold, onSecondary = HteColors.Obsidian,
        tertiary = HteColors.Silver, onTertiary = HteColors.Obsidian,
        background = HteColors.Obsidian, onBackground = HteColors.OnDark,
        surface = HteColors.Panel, onSurface = HteColors.OnDark,
        surfaceVariant = HteColors.PanelRaised, onSurfaceVariant = HteColors.Muted,
        outline = HteColors.Hairline, error = HteColors.Danger,
    )
    CompositionLocalProvider(LocalHteReduceMotion provides reduceMotion) {
        MaterialTheme(colorScheme = colors, typography = HteTypography, shapes = HteShapes, content = content)
    }
}
