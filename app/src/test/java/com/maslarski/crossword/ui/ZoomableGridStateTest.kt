package com.maslarski.crossword.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.maslarski.crossword.ui.components.ZoomableGridState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Cell hit-testing must stay exact through any pinch/pan, on boards of any size. */
class ZoomableGridStateTest {

    // 20x20 board, 20px cells, in a 400x600 viewport: the grid is centred with 100px above and below.
    private fun state(rows: Int = 20, cols: Int = 20, cell: Float = 20f, viewport: Size = Size(400f, 600f)) =
        ZoomableGridState().apply { update(viewport, cell, rows, cols, readableCellPx = 50f) }

    /** Viewport position of the centre of [row], [col] under the state's current zoom/pan (the drawing transform). */
    private fun ZoomableGridState.screenOf(row: Int, col: Int): Offset {
        val grid = Offset((col + 0.5f) * cellPx, (row + 0.5f) * cellPx)
        val center = Offset(viewport.width / 2f, viewport.height / 2f)
        return center + offset + (grid - Offset(gridPx.width / 2f, gridPx.height / 2f)) * zoom
    }

    private fun ZoomableGridState.assertEveryVisibleCellHits() {
        var visible = 0
        for (row in 0 until rows) for (col in 0 until cols) {
            val p = screenOf(row, col)
            if (p.x in 0f..viewport.width && p.y in 0f..viewport.height) {
                visible++
                assertEquals("cell ($row,$col) at zoom $zoom offset $offset", row * cols + col, cellAtLocal(p))
            }
        }
        assertTrue(visible > 0)
    }

    @Test
    fun `unzoomed taps map to cells and miss outside the grid`() {
        val s = state()
        assertEquals(0, s.cellAtLocal(Offset(1f, 101f)))
        assertEquals(19, s.cellAtLocal(Offset(399f, 101f)))
        assertEquals(20 * 20 - 1, s.cellAtLocal(Offset(399f, 499f)))
        assertEquals(5 * 20 + 7, s.cellAtLocal(Offset(7 * 20f + 10f, 100f + 5 * 20f + 10f)))
        assertNull(s.cellAtLocal(Offset(200f, 99f)))
        assertNull(s.cellAtLocal(Offset(200f, 501f)))
        s.assertEveryVisibleCellHits()
    }

    @Test
    fun `pinch keeps the cell under the fingers fixed`() {
        val s = state()
        val focus = s.screenOf(3, 4)
        s.onTransform(focus, Offset.Zero, 3f)
        assertEquals(3f, s.zoom, 0.0001f)
        assertEquals(3 * 20 + 4, s.cellAtLocal(focus))
        s.assertEveryVisibleCellHits()
    }

    @Test
    fun `taps stay exact after panning across a zoomed board`() {
        val s = state()
        s.onTransform(Offset(200f, 300f), Offset.Zero, 4f)
        repeat(12) { s.onTransform(Offset(200f, 300f), Offset(-60f, -45f), 1f) }
        s.assertEveryVisibleCellHits()
        // Panned towards the bottom-right corner: that cell is now on screen and hit exactly.
        val corner = s.screenOf(19, 19)
        assertTrue(corner.x <= s.viewport.width && corner.y <= s.viewport.height)
        assertEquals(399, s.cellAtLocal(corner))
    }

    @Test
    fun `pan is clamped to the board edges`() {
        val s = state()
        s.onTransform(Offset(200f, 300f), Offset(10_000f, 10_000f), 2f)
        // At 2x the 400x400 grid is 800x800: at most 200px of horizontal and 100px of vertical slack.
        assertEquals(Offset(200f, 100f), s.offset)
        assertEquals(0, s.cellAtLocal(Offset(1f, 1f)))
        s.onTransform(Offset(200f, 300f), Offset.Zero, 0.01f)
        assertEquals(1f, s.zoom, 0f)
        assertEquals(Offset.Zero, s.offset)
    }

    @Test
    fun `zoom limits scale with the board`() {
        // Tiny cells: zoom up to twice the readable size (50px -> 100px / 5px = 20x), double-tap to readable (10x).
        val big = state(rows = 25, cols = 25, cell = 5f)
        assertEquals(20f, big.maxZoom, 0.0001f)
        assertEquals(10f, big.focusZoom, 0.0001f)
        big.onTransform(Offset(200f, 300f), Offset.Zero, 100f)
        assertEquals(20f, big.zoom, 0.0001f)
        big.assertEveryVisibleCellHits()
        // Large cells keep the default limits.
        val small = state(rows = 5, cols = 5, cell = 80f)
        assertEquals(ZoomableGridState.MIN_MAX_ZOOM, small.maxZoom, 0f)
        assertEquals(ZoomableGridState.MIN_DOUBLE_TAP_ZOOM, small.focusZoom, 0f)
    }

    @Test
    fun `focus offset zooms in around the tapped point`() {
        val s = state()
        val tap = s.screenOf(15, 2)
        val target = s.focusOffset(tap, 2.5f)
        s.onTransform(Offset(200f, 300f), Offset.Zero, 2.5f)
        s.onTransform(Offset(200f, 300f), target - s.offset, 1f)
        assertEquals(15 * 20 + 2, s.cellAtLocal(tap))
    }

    @Test
    fun `bring into view pans to the selected cell only when zoomed`() {
        val s = state()
        s.bringIntoView(19, 19)
        assertEquals(Offset.Zero, s.offset)
        s.onTransform(Offset(200f, 300f), Offset.Zero, 4f)
        assertTrue(s.isZoomed)
        s.bringIntoView(19, 19)
        val p = s.screenOf(19, 19)
        assertTrue(p.x in 0f..s.viewport.width && p.y in 0f..s.viewport.height)
        s.reset()
        assertFalse(s.isZoomed)
        assertEquals(Offset.Zero, s.offset)
    }
}
