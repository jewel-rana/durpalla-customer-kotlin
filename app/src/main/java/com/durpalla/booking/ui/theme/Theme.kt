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
    val Primary = Color(0xFF106E9D)
    val P50 = Color(0xFFF1F7FB)
    val P100 = Color(0xFFE4F0F7)
    val P300 = Color(0xFF7FB3CF)
    val P600 = Color(0xFF0B5A82)
    val P700 = Color(0xFF084766)
    val Navy = Color(0xFF0A3550)
    val Amber = Color(0xFFF59E0B)
    val AmberDark = Color(0xFFD97706)
    val Success = Color(0xFF16A34A)
    val Error = Color(0xFFDC2626)
    val Ink = Color(0xFF0F172A)
    val Muted = Color(0xFF64748B)
    val Line = Color(0xFFE2E8F0)
    val Canvas = Color(0xFFF6F9FC)

    val HeroGradient = Brush.linearGradient(listOf(Navy, P700, Primary))
    val CtaGradient = Brush.horizontalGradient(listOf(Color(0xFFFBBF24), Amber, AmberDark))
    val PrimaryGradient = Brush.horizontalGradient(listOf(Primary, P600))
    val ShadowTint = Color(0x33106E9D)
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
    secondary = Brand.Navy, onSecondary = Color.White, tertiary = Brand.Amber, onTertiary = Color(0xFF1F1300),
    background = Brand.Canvas, surface = Color.White, onSurface = Brand.Ink, onBackground = Brand.Ink,
    surfaceVariant = Brand.P50, onSurfaceVariant = Brand.Muted, outline = Brand.Line, error = Brand.Error,
)
private val Dark = darkColorScheme(
    primary = Brand.P300, onPrimary = Brand.Navy, primaryContainer = Brand.P700, onPrimaryContainer = Brand.P100,
    secondary = Brand.P100, tertiary = Brand.Amber, background = Color(0xFF07131D), surface = Color(0xFF0E1E2B),
    surfaceVariant = Color(0xFF15293A), onSurfaceVariant = Color(0xFF9FB3C3), outline = Color(0xFF24394B),
)

@Composable
fun DurpallaTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, typography = AppTypography, content = content)
}
