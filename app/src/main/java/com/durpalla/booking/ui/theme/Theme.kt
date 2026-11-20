package com.durpalla.booking.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.sp
import com.durpalla.booking.R

object Brand {
    val Primary = Color(0xFF006D9C)
    val P50 = Color(0xFFF6F9FB)
    val P100 = Color(0xFFE6F4F9)
    val P200 = Color(0xFFB8DDEB)
    val P300 = Color(0xFF3393BA)
    val P600 = Color(0xFF005A82)
    val P700 = Color(0xFF054C70)
    val Navy = Color(0xFF06263A)
    val NavyMid = Color(0xFF063A55)
    val NavyDeep = Color(0xFF04202F)
    val HeroMid = Color(0xFF054C70)
    val Sun = Color(0xFFFBBF24)
    val Amber = Color(0xFFF59E0B)
    val AmberDark = Color(0xFFB45309)
    val SoftYellow = Color(0xFFFFF7E6)
    val Success = Color(0xFF16A34A)
    val Error = Color(0xFFC62828)
    val ErrorBg = Color(0xFFFEF2F2)
    val Info = Color(0xFF0284C7)
    val Ink = Color(0xFF0F172A)
    val TextStrong = Color(0xFF334155)
    val TextBody = Color(0xFF475569)
    val Muted = Color(0xFF64748B)
    val MutedSoft = Color(0xFF94A3B8)
    val Border = Color(0xFFE5E7EB)
    val Line = Color(0xFFE2E8F0)
    val BorderStrong = Color(0xFFCBD5E1)
    val Canvas = Color(0xFFF4F7FA)
    val CanvasAlt = Color(0xFFF1F5F9)
    val CanvasDeep = Color(0xFFEEF2F6)

    val HeroGradient = Brush.linearGradient(listOf(Navy, HeroMid, Primary))
    val CtaGradient = Brush.horizontalGradient(listOf(Sun, Amber, AmberDark))
    val PrimaryGradient = Brush.horizontalGradient(listOf(Primary, P600))
    val ShadowTint = Primary.copy(alpha = 0.2f)
}

@OptIn(ExperimentalTextApi::class)
private fun inter(w: Int) = Font(R.font.inter, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))

@OptIn(ExperimentalTextApi::class)
val Inter = FontFamily(inter(400), inter(500), inter(600), inter(700), inter(800))

private val base = Typography()
val AppTypography = Typography(
    displaySmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp),
    headlineSmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 20.sp),
    titleMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
    titleSmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
    bodyLarge = TextStyle(fontFamily = Inter, fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = Inter, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Inter, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 15.sp),
    labelMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 12.sp),
    labelSmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 11.sp),
    headlineMedium = base.headlineMedium.copy(fontFamily = Inter, fontWeight = FontWeight.Bold),
)

private val Light = lightColorScheme(
    primary = Brand.Primary, onPrimary = Color.White, primaryContainer = Brand.P100, onPrimaryContainer = Brand.P700,
    secondary = Brand.Navy, onSecondary = Color.White, tertiary = Brand.Amber, onTertiary = Brand.AmberDark,
    background = Brand.Canvas, surface = Color.White, onSurface = Brand.Ink, onBackground = Brand.Ink,
    surfaceVariant = Brand.P50, onSurfaceVariant = Brand.Muted, outline = Brand.Line, error = Brand.Error,
)
private val Dark = darkColorScheme(
    primary = Brand.P300, onPrimary = Brand.Navy, primaryContainer = Brand.P700, onPrimaryContainer = Brand.P100,
    secondary = Brand.P100, tertiary = Brand.Amber, background = Brand.NavyDeep, surface = Brand.Navy,
    surfaceVariant = Brand.NavyMid, onSurfaceVariant = Brand.MutedSoft, outline = Brand.NavyMid,
)

@Composable
fun DurpallaTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, typography = AppTypography, content = content)
}
