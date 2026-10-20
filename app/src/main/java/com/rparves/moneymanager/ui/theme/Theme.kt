package com.rparves.moneymanager.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

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
    val income: Color,
    val expense: Color,
    val onAccent: Color,
)

private val LightExtra = ExtraColors(
    heroBg = Color(0xFF12201B), heroOn = Color.White, heroSub = Color(0xFFB9C7C0), heroTile = Color(0xFF24342D),
    heroIncome = Color(0xFF9FE3C9), heroExpense = Color(0xFFFFB27A),
    warnBg = Color(0xFFFFF4EA), warnOn = Color(0xFFA8440B), warnSub = Color(0xFF6B4A33),
    income = Color(0xFF0F6E56), expense = Color(0xFFA8440B), onAccent = Color.White,
)

private val DarkExtra = ExtraColors(
    heroBg = Color(0xFF1C352C), heroOn = Color.White, heroSub = Color(0xFFB9C7C0), heroTile = Color(0xFF284439),
    heroIncome = Color(0xFF9FE3C9), heroExpense = Color(0xFFFFB27A),
    warnBg = Color(0xFF33261C), warnOn = Color(0xFFFFB27A), warnSub = Color(0xFFE6CBB5),
    income = Color(0xFF6FD6B3), expense = Color(0xFFFFA266), onAccent = Color(0xFF0E1714),
)

val LocalExtra = staticCompositionLocalOf { LightExtra }

private val Light = lightColorScheme(
    primary = Color(0xFF0F6E56), onPrimary = Color.White,
    primaryContainer = Color(0xFFE3F2EC), onPrimaryContainer = Color(0xFF0A4F3E),
    secondary = Color(0xFFA8440B), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE3F2EC), onSecondaryContainer = Color(0xFF0A4F3E),
    background = Color(0xFFF4F6F3), onBackground = Color(0xFF12201B),
    surface = Color.White, onSurface = Color(0xFF12201B),
    surfaceVariant = Color(0xFFE3E8E5), onSurfaceVariant = Color(0xFF56625C),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF9FAF8),
    surfaceContainer = Color.White, surfaceContainerHigh = Color.White, surfaceContainerHighest = Color(0xFFE3E8E5),
    outline = Color(0xFFC9D1CC), outlineVariant = Color(0xFFE6EAE7),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF6FD6B3), onPrimary = Color(0xFF0E1714),
    primaryContainer = Color(0xFF1C352C), onPrimaryContainer = Color(0xFFBFEFDD),
    secondary = Color(0xFFFFA266), onSecondary = Color(0xFF0E1714),
    secondaryContainer = Color(0xFF1C352C), onSecondaryContainer = Color(0xFFBFEFDD),
    background = Color(0xFF0E1714), onBackground = Color(0xFFE6ECE9),
    surface = Color(0xFF17221E), onSurface = Color(0xFFE6ECE9),
    surfaceVariant = Color(0xFF24342D), onSurfaceVariant = Color(0xFFA9B6B0),
    surfaceContainerLowest = Color(0xFF0E1714), surfaceContainerLow = Color(0xFF131D19),
    surfaceContainer = Color(0xFF17221E), surfaceContainerHigh = Color(0xFF1D2A25), surfaceContainerHighest = Color(0xFF24342D),
    outline = Color(0xFF3A4B44), outlineVariant = Color(0xFF26352F),
)

@Composable
fun MoneyTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    CompositionLocalProvider(LocalExtra provides if (dark) DarkExtra else LightExtra) {
        MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
    }
}
