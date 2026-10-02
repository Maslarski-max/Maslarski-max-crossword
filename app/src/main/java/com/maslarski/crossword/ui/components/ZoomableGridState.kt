package com.maslarski.crossword.ui.components

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlin.math.floor
import kotlin.math.max

/**
 * Zoom and pan of a grid drawn centred in a viewport, scaled by [zoom] around the viewport centre and shifted by
 * [offset] (screen pixels). Converts viewport-local or root positions back to grid cells for any zoom/pan.
 */
@Stable
class ZoomableGridState {
    var zoom by mutableFloatStateOf(1f)
        private set
    var offset by mutableStateOf(Offset.Zero)
        private set

    var viewport = Size.Zero
        private set
    var cellPx = 0f
        private set
    var rows = 0
        private set
    var cols = 0
        private set

    /** Cell size (px) at which letters read comfortably; drives the double-tap zoom and the maximum zoom. */
    var readableCellPx = 0f
        private set
    internal var originInRoot = Offset.Zero

    val gridPx: Size get() = Size(cellPx * cols, cellPx * rows)

    /** Lets the player zoom until a cell is at least twice its readable size, and never less than [MIN_MAX_ZOOM]. */
    val maxZoom: Float
        get() = if (cellPx <= 0f) MIN_MAX_ZOOM else max(MIN_MAX_ZOOM, readableCellPx * 2f / cellPx)

    /** Zoom used by double-tap: cells at their readable size, or at least [MIN_DOUBLE_TAP_ZOOM]. */
    val focusZoom: Float
        get() = if (cellPx <= 0f) MIN_DOUBLE_TAP_ZOOM else (readableCellPx / cellPx).coerceIn(MIN_DOUBLE_TAP_ZOOM, maxZoom)

    val isZoomed: Boolean get() = zoom > 1.01f

    fun update(viewport: Size, cellPx: Float, rows: Int, cols: Int, readableCellPx: Float) {
        this.viewport = viewport
        this.cellPx = cellPx
        this.rows = rows
        this.cols = cols
        this.readableCellPx = readableCellPx
        zoom = zoom.coerceIn(1f, maxZoom)
        offset = clamp(offset, zoom)
    }

    fun cellAtRoot(position: Offset): Int? = cellAtLocal(position - originInRoot)

    /** Row-major index of the cell under [local] (viewport coordinates), or null outside the grid. */
    fun cellAtLocal(local: Offset): Int? {
        if (cellPx <= 0f) return null
        val p = toGrid(local)
        val col = floor(p.x / cellPx).toInt()
        val row = floor(p.y / cellPx).toInt()
        return if (row in 0 until rows && col in 0 until cols) row * cols + col else null
    }

    /** Pinch around [centroid] by [gestureZoom] and drag by [pan], keeping the content under the fingers fixed. */
    fun onTransform(centroid: Offset, pan: Offset, gestureZoom: Float) {
        val newZoom = (zoom * gestureZoom).coerceIn(1f, maxZoom)
        val anchor = centroid - center() - offset
        offset = clamp(centroid + pan - center() - anchor * (newZoom / zoom), newZoom)
        zoom = newZoom
    }

    /** Double-tap: zoom in on [local] when showing the whole board, otherwise back to the whole board. */
    suspend fun toggleZoom(local: Offset, spec: AnimationSpec<Float> = spring()) {
        if (isZoomed) animateTo(1f, Offset.Zero, spec) else animateTo(focusZoom, focusOffset(local, focusZoom), spec)
    }

    /** Offset that keeps the grid point under [local] in place after zooming to [targetZoom]. */
    fun focusOffset(local: Offset, targetZoom: Float): Offset {
        val anchor = local - center() - offset
        return clamp(local - center() - anchor * (targetZoom / zoom), targetZoom)
    }

    /** Pans just enough to keep the cell at [row], [col] (plus one cell of margin) inside the viewport. */
    fun bringIntoView(row: Int, col: Int) {
        if (!isZoomed || cellPx <= 0f) return
        val cell = cellPx * zoom
        val cx = ((col + 0.5f) * cellPx - gridPx.width / 2f) * zoom + offset.x
        val cy = ((row + 0.5f) * cellPx - gridPx.height / 2f) * zoom + offset.y
        val marginX = max(0f, viewport.width / 2f - cell)
        val marginY = max(0f, viewport.height / 2f - cell)
        val dx = when {
            cx > marginX -> marginX - cx
            cx < -marginX -> -marginX - cx
            else -> 0f
        }
        val dy = when {
            cy > marginY -> marginY - cy
            cy < -marginY -> -marginY - cy
            else -> 0f
        }
        if (dx != 0f || dy != 0f) offset = clamp(offset + Offset(dx, dy), zoom)
    }

    fun reset() {
        zoom = 1f
        offset = Offset.Zero
    }

    private suspend fun animateTo(targetZoom: Float, targetOffset: Offset, spec: AnimationSpec<Float>) {
        val startZoom = zoom
        val startOffset = offset
        animate(0f, 1f, animationSpec = spec) { t, _ ->
            zoom = startZoom + (targetZoom - startZoom) * t
            offset = clamp(startOffset + (targetOffset - startOffset) * t, zoom)
        }
    }

    private fun toGrid(local: Offset): Offset =
        (local - center() - offset) / zoom + Offset(gridPx.width / 2f, gridPx.height / 2f)

    private fun center() = Offset(viewport.width / 2f, viewport.height / 2f)

    internal fun clamp(value: Offset, scale: Float): Offset {
        val maxX = max(0f, (gridPx.width * scale - viewport.width) / 2f)
        val maxY = max(0f, (gridPx.height * scale - viewport.height) / 2f)
        return Offset(value.x.coerceIn(-maxX, maxX), value.y.coerceIn(-maxY, maxY))
    }

    companion object {
        const val MIN_MAX_ZOOM = 4f
        const val MIN_DOUBLE_TAP_ZOOM = 2f
    }
}
