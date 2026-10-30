package com.rparves.moneymanager.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.rparves.moneymanager.R

/** "Night vault" palette: near-black, one warm orange accent for spending, violet for income. */
@Immutable
data class ExtraColors(
    val heroBg: Color,
    val heroOn: Color,
    val heroSub: Color,
    val heroTile: Color,
    val heroIncome: Color,
    val heroExpense: Color,
    val warnBg: Color,
    val warnOn: Color,
    val warnSub: Color,
    /** money in */
    val income: Color,
    /** money out, and the app's main accent */
    val expense: Color,
    val onAccent: Color,
    val ringTrack: Color,
    val border: Color,
    val muted: Color,
)

private val DarkExtra = ExtraColors(
    heroBg = Color(0xFF15151C), heroOn = Color(0xFFF2F2F7), heroSub = Color(0xFF8E8E9A), heroTile = Color(0xFF1C1C24),
    heroIncome = Color(0xFF8B7CFF), heroExpense = Color(0xFFFF8A3D),
    warnBg = Color(0xFF231A14), warnOn = Color(0xFFFF8A3D), warnSub = Color(0xFFE8C8B2),
    income = Color(0xFF9D90FF), expense = Color(0xFFFF8A3D), onAccent = Color(0xFF0D0D12),
    ringTrack = Color(0xFF1C1C24), border = Color(0xFF26262F), muted = Color(0xFF8E8E9A),
)

private val LightExtra = ExtraColors(
    heroBg = Color(0xFFFFFFFF), heroOn = Color(0xFF0D0D12), heroSub = Color(0xFF5F5F6B), heroTile = Color(0xFFEEEEF3),
    heroIncome = Color(0xFF5B4BD6), heroExpense = Color(0xFFC2510F),
    warnBg = Color(0xFFFFF0E6), warnOn = Color(0xFFB0470A), warnSub = Color(0xFF6B4A33),
    income = Color(0xFF5B4BD6), expense = Color(0xFFC2510F), onAccent = Color(0xFFFFFFFF),
    ringTrack = Color(0xFFE6E6EE), border = Color(0xFFE3E3EA), muted = Color(0xFF5F5F6B),
)

val LocalExtra = staticCompositionLocalOf { DarkExtra }
val LocalDark = staticCompositionLocalOf { true }

private val Dark = darkColorScheme(
    primary = Color(0xFFFF8A3D), onPrimary = Color(0xFF0D0D12),
    primaryContainer = Color(0xFF2A1B10), onPrimaryContainer = Color(0xFFFFD2B3),
    secondary = Color(0xFF9D90FF), onSecondary = Color(0xFF0D0D12),
    secondaryContainer = Color(0xFF221E3D), onSecondaryContainer = Color(0xFFD9D3FF),
    background = Color(0xFF0D0D12), onBackground = Color(0xFFF2F2F7),
    surface = Color(0xFF15151C), onSurface = Color(0xFFF2F2F7),
    surfaceVariant = Color(0xFF1C1C24), onSurfaceVariant = Color(0xFF8E8E9A),
    surfaceContainerLowest = Color(0xFF0D0D12), surfaceContainerLow = Color(0xFF111117),
    surfaceContainer = Color(0xFF15151C), surfaceContainerHigh = Color(0xFF1A1A22), surfaceContainerHighest = Color(0xFF22222B),
    outline = Color(0xFF3A3A46), outlineVariant = Color(0xFF26262F),
    error = Color(0xFFFF6B6B),
)

private val Light = lightColorScheme(
    primary = Color(0xFFC2510F), onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE6D6), onPrimaryContainer = Color(0xFF5A2405),
    secondary = Color(0xFF5B4BD6), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE9E6FF), onSecondaryContainer = Color(0xFF2A1F7A),
    background = Color(0xFFF6F6F9), onBackground = Color(0xFF0D0D12),
    surface = Color.White, onSurface = Color(0xFF0D0D12),
    surfaceVariant = Color(0xFFEEEEF3), onSurfaceVariant = Color(0xFF5F5F6B),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF9F9FB),
    surfaceContainer = Color.White, surfaceContainerHigh = Color.White, surfaceContainerHighest = Color(0xFFEEEEF3),
    outline = Color(0xFFC9C9D3), outlineVariant = Color(0xFFE3E3EA),
)

@OptIn(ExperimentalTextApi::class)
private fun sora(w: Int) = Font(R.font.sora, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))

@OptIn(ExperimentalTextApi::class)
val Sora = FontFamily(sora(300), sora(400), sora(500), sora(600), sora(700), sora(800))

private fun TextStyle.sora() = copy(fontFamily = Sora)

private val SoraType = Typography().let { t ->
    Typography(
        displayLarge = t.displayLarge.sora(), displayMedium = t.displayMedium.sora(), displaySmall = t.displaySmall.sora(),
        headlineLarge = t.headlineLarge.sora(), headlineMedium = t.headlineMedium.sora(), headlineSmall = t.headlineSmall.sora(),
        titleLarge = t.titleLarge.sora(), titleMedium = t.titleMedium.sora(), titleSmall = t.titleSmall.sora(),
        bodyLarge = t.bodyLarge.sora(), bodyMedium = t.bodyMedium.sora(), bodySmall = t.bodySmall.sora(),
        labelLarge = t.labelLarge.sora(), labelMedium = t.labelMedium.sora(), labelSmall = t.labelSmall.sora(),
    )
}

/** mode: "dark", "light" or "system" */
@Composable
fun isDarkMode(mode: String): Boolean = when (mode) {
    "light" -> false
    "system" -> isSystemInDarkTheme()
    else -> true
}

@Composable
fun MoneyTheme(dark: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalExtra provides if (dark) DarkExtra else LightExtra, LocalDark provides dark) {
        MaterialTheme(colorScheme = if (dark) Dark else Light, typography = SoraType, content = content)
    }
}
