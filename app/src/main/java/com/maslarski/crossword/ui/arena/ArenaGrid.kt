package com.maslarski.crossword.ui.arena

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.maslarski.crossword.domain.arena.ArenaLayout
import com.maslarski.crossword.domain.arena.ArenaState
import com.maslarski.crossword.domain.arena.ArenaWord
import com.maslarski.crossword.domain.arena.Side
import com.maslarski.crossword.domain.model.Direction
import com.maslarski.crossword.ui.components.ZoomableGridState
import com.maslarski.crossword.ui.theme.ArenaColors
import com.maslarski.crossword.ui.theme.LocalArenaColors
import kotlin.math.max

private val READABLE_CELL = 56.dp

/** Zoom steps at which clue text is re-fitted, so clues stay sharp and use the extra room when zoomed in. */
private val CLUE_SCALES = floatArrayOf(1f, 1.5f, 2f, 3f, 4f, 6f, 8f)

/** Points just earned on [cells]; [sequence] distinguishes consecutive flashes. */
data class ScoreFlash(val side: Side, val cells: List<Int>, val points: List<Int>, val sequence: Int)

/**
 * Arrow-word grid of any size; pinch to zoom, drag to pan while zoomed, double-tap to zoom in on a spot or back out.
 * Taps and rack-tile drops map to cells through the current zoom/pan via [ZoomableGridState].
 *
 * Arrow-word grid: clue cells carry their clue text and an arrow into the answer; letter cells show owned letters
 * tinted by who placed them, pending rack tiles, opponent tiles being laid, and "+2"/"+3" bonus badges.
 */
@Composable
fun ArenaGrid(
    layout: ArenaLayout,
    match: ArenaState,
    pending: Map<Int, Char>,
    opponentTiles: Map<Int, Char>,
    activeWord: ArenaWord?,
    focusCell: Int?,
    missCells: Set<Int>,
    dropTarget: Int?,
    flash: ScoreFlash?,
    state: ZoomableGridState,
    onCellTap: (Int) -> Unit,
    modifier: Modifier = Modifier,
    description: String = "",
) {
    val colors = LocalArenaColors.current
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer(cacheSize = 128)
    val currentOnTap by rememberUpdatedState(onCellTap)
    val scope = rememberCoroutineScope()

    val flashAlpha = remember { Animatable(0f) }
    LaunchedEffect(flash?.sequence) {
        if (flash != null) {
            flashAlpha.snapTo(1f)
            flashAlpha.animateTo(0f, tween(durationMillis = 1800, delayMillis = 600))
        }
    }

    BoxWithConstraints(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clipToBounds()
            .semantics { contentDescription = description }
            .onGloballyPositioned { state.originInRoot = it.positionInRoot() }
            .pointerInput(state) { detectTransformGestures { centroid, pan, zoom, _ -> state.onTransform(centroid, pan, zoom) } }
            .pointerInput(state) {
                detectTapGestures(
                    onTap = { pos -> state.cellAtLocal(pos)?.let(currentOnTap) },
                    onDoubleTap = { pos -> state.toggleZoom(scope, pos) },
                )
            },
    ) {
        val cellDp = min(maxWidth / layout.cols, maxHeight / layout.rows)
        val gridWidth = cellDp * layout.cols
        val gridHeight = cellDp * layout.rows
        val cellPx = with(density) { cellDp.toPx() }
        val viewport = with(density) { Size(maxWidth.toPx(), maxHeight.toPx()) }
        val readablePx = with(density) { READABLE_CELL.toPx() }
        SideEffect { state.update(viewport, cellPx, layout.rows, layout.cols, readablePx) }
        val clueScale by remember(state) { derivedStateOf { CLUE_SCALES.last { it <= state.zoom } } }

        val letterStyle = remember(cellPx) {
            TextStyle(fontSize = with(density) { (cellPx * 0.56f).toSp() }, fontWeight = FontWeight.Bold)
        }
        val badgeStyle = remember(cellPx) {
            TextStyle(fontSize = with(density) { (cellPx * 0.2f).toSp() }, fontWeight = FontWeight.Bold)
        }
        val clueCaches = remember(layout, cellPx, colors) { HashMap<Float, HashMap<String, TextLayoutResult>>() }
        val activeCells = remember(activeWord) { activeWord?.cells?.toHashSet() ?: emptySet() }
        val flashPoints = remember(flash) { flash?.let { f -> f.cells.zip(f.points).toMap() } ?: emptyMap() }

        Canvas(
            modifier = Modifier
                .size(gridWidth, gridHeight)
                .graphicsLayer {
                    scaleX = state.zoom
                    scaleY = state.zoom
                    translationX = state.offset.x
                    translationY = state.offset.y
                },
        ) {
            drawRect(colors.gridLine)
            val line = max(1f, cellPx * 0.025f)
            val cellSize = Size(cellPx - line, cellPx - line)

            for (index in 0 until layout.cellCount) {
                val topLeft = Offset(layout.colOf(index) * cellPx + line / 2f, layout.rowOf(index) * cellPx + line / 2f)

                if (!layout.isLetter(index)) {
                    val clue = layout.clueAt(index)
                    if (clue == null) {
                        drawRect(colors.filler, topLeft, cellSize)
                    } else {
                        val active = activeWord != null && (clue.across == activeWord || clue.down == activeWord)
                        drawRect(if (active) colors.activeClueCell else colors.clueCell, topLeft, cellSize)
                        drawClues(clue.across, clue.down, topLeft, cellSize, clueScale, colors, textMeasurer, clueCaches.getOrPut(clueScale) { HashMap() })
                    }
                    continue
                }

                val background = when {
                    index in activeCells -> colors.activeWord
                    else -> colors.cell
                }
                drawRect(background, topLeft, cellSize)

                val tile = pending[index] ?: opponentTiles[index]
                when {
                    tile != null -> drawTile(tile, index in opponentTiles, topLeft, cellSize, colors, textMeasurer, letterStyle)
                    match.isFilled(index) -> {
                        val color = when (match.owners[index]) {
                            ArenaState.PLAYER -> colors.player
                            ArenaState.OPPONENT -> colors.opponent
                            else -> colors.given
                        }
                        drawCentered(textMeasurer.measure(layout.solutionAt(index).toString(), letterStyle.copy(color = color)), topLeft, cellSize)
                    }
                }

                val value = match.valueAt(index)
                if (value > 1 && match.isEmpty(index)) {
                    drawBadge("+$value", topLeft, cellSize, colors.bonus, colors.onBonus, textMeasurer, badgeStyle)
                }

                val earned = flashPoints[index]
                if (earned != null && flashAlpha.value > 0f) {
                    val tint = if (flash?.side == Side.PLAYER) colors.player else colors.opponent
                    val a = flashAlpha.value
                    drawRect(tint.copy(alpha = 0.18f * a), topLeft, cellSize)
                    drawRect(tint.copy(alpha = a), topLeft, cellSize, style = Stroke(width = cellPx * 0.07f))
                    drawBadge("+$earned", topLeft, cellSize, tint.copy(alpha = a), colors.onBonus.copy(alpha = a), textMeasurer, badgeStyle)
                }
                if (index in missCells) {
                    drawRect(colors.miss, topLeft, cellSize, style = Stroke(width = cellPx * 0.08f))
                }
                if (index == focusCell && match.isEmpty(index) && index !in pending) {
                    drawRect(colors.player.copy(alpha = 0.6f), topLeft, cellSize, style = Stroke(width = cellPx * 0.06f))
                }
                if (index == dropTarget && match.isEmpty(index)) {
                    drawRect(colors.player, topLeft, cellSize, style = Stroke(width = cellPx * 0.09f))
                }
            }
        }
    }
}

private fun DrawScope.drawCentered(text: TextLayoutResult, topLeft: Offset, size: Size) {
    drawText(
        text,
        topLeft = Offset(
            topLeft.x + (size.width - text.size.width) / 2f,
            topLeft.y + (size.height - text.size.height) / 2f,
        ),
    )
}

private fun DrawScope.drawTile(
    letter: Char,
    opponent: Boolean,
    topLeft: Offset,
    size: Size,
    colors: ArenaColors,
    textMeasurer: TextMeasurer,
    style: TextStyle,
) {
    val inset = size.width * 0.07f
    val corner = CornerRadius(size.width * 0.14f)
    val tileSize = Size(size.width - inset * 2, size.height - inset * 2)
    val origin = topLeft + Offset(inset, inset)
    drawRoundRect(colors.tileEdge, origin, tileSize, corner)
    drawRoundRect(colors.tile, origin, Size(tileSize.width, tileSize.height - size.height * 0.07f), corner)
    if (opponent) drawRoundRect(colors.opponent, origin, tileSize, corner, style = Stroke(width = size.width * 0.06f))
    drawCentered(textMeasurer.measure(letter.toString(), style.copy(color = colors.onTile)), topLeft, size)
}

private fun DrawScope.drawBadge(
    label: String,
    topLeft: Offset,
    size: Size,
    background: Color,
    content: Color,
    textMeasurer: TextMeasurer,
    style: TextStyle,
) {
    val text = textMeasurer.measure(label, style.copy(color = content))
    val pad = size.width * 0.05f
    val badge = Size(text.size.width + pad * 2, text.size.height.toFloat())
    val origin = Offset(topLeft.x + size.width - badge.width - pad / 2f, topLeft.y + pad / 2f)
    drawRoundRect(background, origin, badge, CornerRadius(badge.height / 2f))
    drawText(text, topLeft = origin + Offset(pad, 0f))
}

private fun DrawScope.drawClues(
    across: ArenaWord?,
    down: ArenaWord?,
    topLeft: Offset,
    size: Size,
    textScale: Float,
    colors: ArenaColors,
    textMeasurer: TextMeasurer,
    cache: HashMap<String, TextLayoutResult>,
) {
    val both = across != null && down != null
    val half = if (both) size.height / 2f else size.height
    if (both) {
        drawLine(colors.gridLine, Offset(topLeft.x, topLeft.y + half), Offset(topLeft.x + size.width, topLeft.y + half), strokeWidth = max(1f, size.width * 0.02f))
    }
    var top = topLeft.y
    for (word in listOfNotNull(across, down)) {
        val box = Size(size.width, half)
        // Fit at the on-screen size (box * textScale), then draw scaled back into the unzoomed box.
        val scaledBox = Size(box.width * textScale, box.height * textScale)
        val text = cache.getOrPut(word.key) { fitClue(word.clue, scaledBox, colors.clueText, textMeasurer) }
        val origin = Offset(topLeft.x, top)
        scale(1f / textScale, pivot = origin) { drawCentered(text, origin, scaledBox) }
        drawArrow(word.direction, Offset(topLeft.x, top), box, size.width, colors.clueText)
        top += half
    }
}

private fun DrawScope.drawArrow(direction: Direction, topLeft: Offset, box: Size, cell: Float, color: Color) {
    val s = cell * 0.09f
    val path = Path()
    if (direction == Direction.ACROSS) {
        val x = topLeft.x + box.width
        val y = topLeft.y + box.height / 2f
        path.moveTo(x, y)
        path.lineTo(x - s, y - s)
        path.lineTo(x - s, y + s)
    } else {
        val x = topLeft.x + box.width / 2f
        val y = topLeft.y + box.height
        path.moveTo(x, y)
        path.lineTo(x - s, y - s)
        path.lineTo(x + s, y - s)
    }
    path.close()
    drawPath(path, color)
}

/** Largest font (down to a floor) at which [clue] fits [box]; ellipsised at the floor. */
private fun DrawScope.fitClue(clue: String, box: Size, color: Color, textMeasurer: TextMeasurer): TextLayoutResult {
    val pad = box.width * 0.1f
    val constraints = Constraints(maxWidth = max(1, (box.width - pad * 2).toInt()), maxHeight = max(1, (box.height - pad).toInt()))
    val floorSp = max(5f, (box.width * 0.11f).toSp().value)
    var sizeSp = max(floorSp, (box.width * 0.2f).toSp().value)
    while (true) {
        val style = TextStyle(
            color = color,
            fontSize = sizeSp.sp,
            lineHeight = (sizeSp * 1.05f).sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
        )
        val result = textMeasurer.measure(clue, style, TextOverflow.Ellipsis, constraints = constraints)
        if ((!result.hasVisualOverflow && !result.breaksWord(clue)) || sizeSp <= floorSp) return result
        sizeSp = max(floorSp, sizeSp - 0.5f)
    }
}

private fun TextLayoutResult.breaksWord(text: String): Boolean = (0 until lineCount - 1).any { line ->
    val end = getLineEnd(line)
    end in 1 until text.length && text[end - 1].isLetterOrDigit() && text[end].isLetterOrDigit()
}
