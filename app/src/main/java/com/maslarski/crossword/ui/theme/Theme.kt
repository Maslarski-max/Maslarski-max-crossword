package com.maslarski.crossword.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import com.maslarski.crossword.domain.model.ThemeMode

/** Colors used by the crossword grid, derived from the active Material color scheme. */
@Immutable
data class GridColors(
    val block: Color,
    val cell: Color,
    val gridLine: Color,
    val letter: Color,
    val number: Color,
    val wordHighlight: Color,
    val selectedCell: Color,
    val onSelectedCell: Color,
    val revealedMark: Color,
    val incorrect: Color,
    val solvedGlow: Color,
)

private fun ColorScheme.gridColors(dark: Boolean) = GridColors(
    block = if (dark) Color(0xFF050508) else Color(0xFF1F1D2B),
    cell = if (dark) surfaceContainerHigh else surfaceContainerLowest,
    gridLine = outline.copy(alpha = 0.6f),
    letter = onSurface,
    number = onSurfaceVariant,
    wordHighlight = primary.copy(alpha = if (dark) 0.35f else 0.22f).compositeOver(if (dark) surfaceContainerHigh else surfaceContainerLowest),
    selectedCell = primary,
    onSelectedCell = onPrimary,
    revealedMark = tertiary,
    incorrect = error,
    solvedGlow = tertiary.copy(alpha = 0.55f),
)

val LocalGridColors = staticCompositionLocalOf { LightColors.gridColors(dark = false) }

@Composable
fun CrosswordTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    val gridColors = remember(colorScheme, dark) { colorScheme.gridColors(dark) }
    CompositionLocalProvider(LocalGridColors provides gridColors) {
        MaterialTheme(colorScheme = colorScheme, typography = CrosswordTypography, content = content)
    }
}
