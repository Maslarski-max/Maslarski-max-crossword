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

/** Colors of the arena arrow-word grid and letter tiles. */
@Immutable
data class ArenaColors(
    val gridLine: Color,
    val cell: Color,
    val clueCell: Color,
    val activeClueCell: Color,
    val clueText: Color,
    val filler: Color,
    val activeWord: Color,
    val hint: Color,
    val tile: Color,
    val tileEdge: Color,
    val onTile: Color,
    val player: Color,
    val opponent: Color,
    val given: Color,
    val bonus: Color,
    val onBonus: Color,
    val miss: Color,
)

private fun ColorScheme.arenaColors(dark: Boolean) = ArenaColors(
    gridLine = outlineVariant,
    cell = if (dark) surfaceContainerHigh else surfaceContainerLowest,
    clueCell = if (dark) Color(0xFF26324A) else Color(0xFFDCE7F7),
    activeClueCell = if (dark) Color(0xFF34466A) else Color(0xFFBFD4F2),
    clueText = if (dark) Color(0xFFC9D6EE) else Color(0xFF2B3A55),
    filler = if (dark) Color(0xFF1C2333) else Color(0xFFEAF0F9),
    activeWord = primary.copy(alpha = if (dark) 0.3f else 0.16f).compositeOver(if (dark) surfaceContainerHigh else surfaceContainerLowest),
    hint = if (dark) Color(0xFF2F5A2A) else Color(0xFFBDE8A9),
    tile = if (dark) Color(0xFFE9CC9A) else Color(0xFFF7E0BA),
    tileEdge = if (dark) Color(0xFFB08A4E) else Color(0xFFD7B47C),
    onTile = Color(0xFF3B2A12),
    player = primary,
    opponent = if (dark) Color(0xFFFFB960) else Color(0xFFC05A00),
    given = onSurface,
    bonus = primary,
    onBonus = onPrimary,
    miss = error,
)

val LocalArenaColors = staticCompositionLocalOf { LightColors.arenaColors(dark = false) }

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
    val arenaColors = remember(colorScheme, dark) { colorScheme.arenaColors(dark) }
    CompositionLocalProvider(LocalGridColors provides gridColors, LocalArenaColors provides arenaColors) {
        MaterialTheme(colorScheme = colorScheme, typography = CrosswordTypography, content = content)
    }
}
