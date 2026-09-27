package com.maslarski.crossword.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.min
import com.maslarski.crossword.domain.model.BoardState
import com.maslarski.crossword.domain.model.Puzzle
import com.maslarski.crossword.domain.model.Word
import com.maslarski.crossword.ui.theme.LocalGridColors
import kotlin.math.abs
import kotlin.math.max

/** Marks the most recent keystroke so the grid can play a "pop" animation on that cell. */
@Immutable
data class InputEvent(val index: Int, val sequence: Long)

private const val MAX_ZOOM = 4f

/**
 * Crossword board drawn on a single Canvas (one draw pass for the whole grid, no per-cell composables).
 * Supports pinch-to-zoom, panning while zoomed, double-tap to reset zoom, and keeps the selected cell
 * in view while zoomed.
 */
@Composable
fun CrosswordGrid(
    puzzle: Puzzle,
    board: BoardState,
    activeWord: Word?,
    lastInput: InputEvent?,
    solved: Boolean,
    onCellTap: (Int) -> Unit,
    modifier: Modifier = Modifier,
    description: String = "",
) {
    val colors = LocalGridColors.current
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer(cacheSize = 64)
    val currentOnTap by rememberUpdatedState(onCellTap)

    val pop = remember { Animatable(1f) }
    LaunchedEffect(lastInput) {
        if (lastInput != null) {
            pop.snapTo(0.4f)
            pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium))
        }
    }
    val wave = remember { Animatable(if (solved) 2f else 0f) }
    LaunchedEffect(solved) {
        if (solved && wave.value < 2f) wave.animateTo(2f, tween(durationMillis = 1600)) else if (!solved) wave.snapTo(0f)
    }

    BoxWithConstraints(
        modifier = modifier.clipToBounds().semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        val cellDp = min(maxWidth / puzzle.cols, maxHeight / puzzle.rows)
        val gridWidth = cellDp * puzzle.cols
        val gridHeight = cellDp * puzzle.rows
        val viewport = with(density) { Size(maxWidth.toPx(), maxHeight.toPx()) }
        val gridPx = with(density) { Size(gridWidth.toPx(), gridHeight.toPx()) }
        val cellPx = with(density) { cellDp.toPx() }

        var zoom by remember(puzzle.id) { mutableFloatStateOf(1f) }
        var offset by remember(puzzle.id) { mutableStateOf(Offset.Zero) }

        fun clamp(value: Offset, scale: Float): Offset {
            val maxX = max(0f, (gridPx.width * scale - viewport.width) / 2f)
            val maxY = max(0f, (gridPx.height * scale - viewport.height) / 2f)
            return Offset(value.x.coerceIn(-maxX, maxX), value.y.coerceIn(-maxY, maxY))
        }

        // Keep the selected cell on screen when zoomed in.
        LaunchedEffect(board.selected, zoom > 1f) {
            if (zoom <= 1f) return@LaunchedEffect
            val row = puzzle.rowOf(board.selected)
            val col = puzzle.colOf(board.selected)
            val cx = ((col + 0.5f) * cellPx - gridPx.width / 2f) * zoom + offset.x
            val cy = ((row + 0.5f) * cellPx - gridPx.height / 2f) * zoom + offset.y
            val marginX = viewport.width / 2f - cellPx * zoom
            val marginY = viewport.height / 2f - cellPx * zoom
            var dx = 0f
            var dy = 0f
            if (cx > marginX) dx = marginX - cx else if (cx < -marginX) dx = -marginX - cx
            if (cy > marginY) dy = marginY - cy else if (cy < -marginY) dy = -marginY - cy
            if (dx != 0f || dy != 0f) offset = clamp(offset + Offset(dx, dy), zoom)
        }

        val letterStyle = remember(cellPx) {
            TextStyle(fontSize = with(density) { (cellPx * 0.58f).toSp() }, fontWeight = FontWeight.SemiBold)
        }
        val numberStyle = remember(cellPx) {
            TextStyle(fontSize = with(density) { (cellPx * 0.27f).toSp() }, fontWeight = FontWeight.Medium)
        }
        val letterLayouts = remember(letterStyle, colors) { HashMap<Char, TextLayoutResult>() }
        val numberLayouts = remember(numberStyle, colors) { HashMap<Int, TextLayoutResult>() }
        val activeCells = remember(activeWord) { activeWord?.cells?.toHashSet() ?: emptySet() }

        Canvas(
            modifier = Modifier
                .size(gridWidth, gridHeight)
                .pointerInput(puzzle.id, viewport) {
                    detectTransformGestures { centroid, pan, gestureZoom, _ ->
                        val newZoom = (zoom * gestureZoom).coerceIn(1f, MAX_ZOOM)
                        val factor = newZoom / zoom
                        // centroid is in the unscaled canvas space; express it relative to the grid centre.
                        val c = (centroid - Offset(gridPx.width / 2f, gridPx.height / 2f)) * zoom
                        offset = clamp(offset * factor + c * (1 - factor) + pan * zoom, newZoom)
                        zoom = newZoom
                    }
                }
                .pointerInput(puzzle.id) {
                    detectTapGestures(
                        onTap = { pos ->
                            val col = (pos.x / cellPx).toInt()
                            val row = (pos.y / cellPx).toInt()
                            if (row in 0 until puzzle.rows && col in 0 until puzzle.cols) {
                                val index = puzzle.index(row, col)
                                if (!puzzle.isBlock(index)) currentOnTap(index)
                            }
                        },
                        onDoubleTap = {
                            zoom = 1f
                            offset = Offset.Zero
                        },
                    )
                }
                .graphicsLayer {
                    scaleX = zoom
                    scaleY = zoom
                    translationX = offset.x
                    translationY = offset.y
                },
        ) {
            val inset = max(1f, cellPx * 0.03f)
            val corner = CornerRadius(cellPx * 0.08f)
            val cellSize = Size(cellPx - inset * 2, cellPx - inset * 2)
            val diagonal = (puzzle.rows + puzzle.cols - 2).coerceAtLeast(1)

            for (index in 0 until puzzle.cellCount) {
                val row = puzzle.rowOf(index)
                val col = puzzle.colOf(index)
                val topLeft = Offset(col * cellPx + inset, row * cellPx + inset)

                if (puzzle.isBlock(index)) {
                    drawRoundRect(colors.block, topLeft, cellSize, corner)
                    continue
                }

                val selected = index == board.selected && !solved
                val background = when {
                    selected -> colors.selectedCell
                    index in activeCells && !solved -> colors.wordHighlight
                    else -> colors.cell
                }
                drawRoundRect(background, topLeft, cellSize, corner)

                if (wave.value > 0f) {
                    val d = (row + col).toFloat() / diagonal
                    val band = (1f - abs(wave.value - 0.5f - d) / 0.3f).coerceIn(0f, 1f)
                    val settled = if (wave.value >= 2f) 0.35f else 0f
                    val alpha = max(band, settled)
                    if (alpha > 0f) drawRoundRect(colors.solvedGlow.copy(alpha = colors.solvedGlow.alpha * alpha), topLeft, cellSize, corner)
                }

                val number = puzzle.numberAt(index)
                if (number > 0) {
                    val layout = numberLayouts.getOrPut(number) { textMeasurer.measure(number.toString(), numberStyle) }
                    drawText(
                        layout,
                        color = if (selected) colors.onSelectedCell else colors.number,
                        topLeft = topLeft + Offset(cellPx * 0.07f, cellPx * 0.03f),
                    )
                }

                if (index in board.revealed) drawRevealedMark(topLeft, cellSize, colors.revealedMark)

                val letter = board.entryAt(index)
                if (letter != Puzzle.EMPTY) {
                    val layout = letterLayouts.getOrPut(letter) { textMeasurer.measure(letter.toString(), letterStyle) }
                    val wrong = index in board.incorrect
                    val letterColor = when {
                        wrong -> colors.incorrect
                        selected -> colors.onSelectedCell
                        else -> colors.letter
                    }
                    val center = Offset(col * cellPx + cellPx / 2f, row * cellPx + cellPx * 0.56f)
                    val letterScale = if (lastInput?.index == index) pop.value else 1f
                    scale(letterScale, center) {
                        drawText(
                            layout,
                            color = letterColor,
                            topLeft = center - Offset(layout.size.width / 2f, layout.size.height / 2f),
                        )
                    }
                    if (wrong) {
                        drawLine(
                            colors.incorrect,
                            start = topLeft + Offset(cellSize.width * 0.15f, cellSize.height * 0.85f),
                            end = topLeft + Offset(cellSize.width * 0.85f, cellSize.height * 0.15f),
                            strokeWidth = max(1.5f, cellPx * 0.05f),
                        )
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawRevealedMark(topLeft: Offset, cellSize: Size, color: Color) {
    val s = cellSize.width * 0.24f
    val path = Path().apply {
        moveTo(topLeft.x + cellSize.width - s, topLeft.y)
        lineTo(topLeft.x + cellSize.width, topLeft.y)
        lineTo(topLeft.x + cellSize.width, topLeft.y + s)
        close()
    }
    drawPath(path, color)
}
