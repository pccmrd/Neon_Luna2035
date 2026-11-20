package com.example.keres9.ui.theme

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

object KeresColors {
    val Void = Color(0xFF04070C)
    val Panel = Color(0xED090E16)
    val PanelSolid = Color(0xF70E1520)
    val PanelDark = Color(0xFF0B131D)
    val PanelTab = Color(0xFF0D1520)
    val PanelTabActive = Color(0xFF101C2A)
    val ButtonTop = Color(0xFF17222F)
    val ButtonBottom = Color(0xFF0B131D)
    val CtxTop = Color(0xFF3A2A14)
    val CtxBottom = Color(0xFF1C1208)
    val Steel = Color(0xFF2B3A4D)
    val Steel2 = Color(0xFF41546B)
    val Divider = Color(0xFF16202E)
    val TrackBg = Color(0xFF101823)
    val Amber = Color(0xFFFFB347)
    val AmberBright = Color(0xFFFFE0B0)
    val Ember = Color(0xFFFF8C1A)
    val Rust = Color(0xFFC96F2F)
    val Cyan = Color(0xFF53D9FF)
    val Ice = Color(0xFFA8ECFF)
    val Red = Color(0xFFFF4A3D)
    val RedTint = Color(0xFFFFB0A8)
    val RedDark = Color(0xFF7D1F16)
    val Green = Color(0xFF6DF08F)
    val GreenDark = Color(0xFF2E7D4F)
    val Gold = Color(0xFFFFD166)
    val Mag = Color(0xFFFF5EC4)
    val Text = Color(0xFFD8E2EC)
    val Dim = Color(0xFF7D8FA3)
    val BannerRedBg = Color(0xDB280806)
    val BannerCyanBg = Color(0xDB06161E)
    val Scrim = Color(0xA803060A)
}

val RobotoMonoFamily = FontFamily(
    Font(R.font.roboto_mono, FontWeight.Normal),
    Font(R.font.roboto_mono, FontWeight.Bold)
)

private val KeresDarkScheme = darkColorScheme(
    primary = KeresColors.Amber,
    onPrimary = KeresColors.Void,
    primaryContainer = KeresColors.CtxTop,
    onPrimaryContainer = KeresColors.AmberBright,
    secondary = KeresColors.Cyan,
    onSecondary = KeresColors.Void,
    secondaryContainer = KeresColors.PanelTabActive,
    onSecondaryContainer = KeresColors.Cyan,
    tertiary = KeresColors.Green,
    onTertiary = KeresColors.Void,
    background = KeresColors.Void,
    onBackground = KeresColors.Text,
    surface = KeresColors.PanelSolid,
    onSurface = KeresColors.Text,
    surfaceVariant = KeresColors.PanelDark,
    onSurfaceVariant = KeresColors.Dim,
    outline = KeresColors.Steel2,
    outlineVariant = KeresColors.Steel,
    error = KeresColors.Red,
    onError = KeresColors.Void
)

private val KeresTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = RobotoMonoFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 48.sp,
        letterSpacing = 6.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = RobotoMonoFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        letterSpacing = 2.5.sp
    ),
    titleMedium = TextStyle(
        fontFamily = RobotoMonoFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        letterSpacing = 1.2.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = RobotoMonoFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp
    ),
    bodySmall = TextStyle(
        fontFamily = RobotoMonoFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 10.sp,
        lineHeight = 14.sp
    ),
    labelMedium = TextStyle(
        fontFamily = RobotoMonoFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp,
        letterSpacing = 1.sp
    ),
    labelSmall = TextStyle(
        fontFamily = RobotoMonoFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 8.sp,
        letterSpacing = 0.8.sp
    )
)

@Composable
fun Keres9Theme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = KeresDarkScheme,
        typography = KeresTypography,
        content = content
    )
}
