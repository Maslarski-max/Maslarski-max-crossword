package com.maslarski.crossword.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CoroutineScope
import kotlin.math.abs
import kotlin.math.hypot

suspend fun PointerInputScope.detectZoomPan(state: ZoomableGridState, scope: CoroutineScope) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        state.stopAnimation()

        val velocityTracker = VelocityTracker()
        velocityTracker.addPosition(down.uptimeMillis, down.position)
        var pointerCount = 1
        var pastTouchSlop = false
        var accumulatedZoom = 1f
        var accumulatedPan = Offset.Zero
        var lastSinglePointerPanAt: Long? = null
        var endingVelocity: Velocity? = null
        var endingPanAt: Long? = null
        var endingTime = down.uptimeMillis
        var canceled = false

        do {
            val event = awaitPointerEvent()
            val eventTime = event.changes.firstOrNull()?.uptimeMillis ?: endingTime
            endingTime = eventTime
            val pressedCount = event.changes.count { it.pressed }

            if (pressedCount == 0 && pointerCount == 1) {
                endingVelocity = velocityTracker.calculateVelocity()
                endingPanAt = lastSinglePointerPanAt
            }

            if (pressedCount != pointerCount) {
                velocityTracker.resetTracking()
                if (pressedCount != 1) lastSinglePointerPanAt = null
                pointerCount = pressedCount
            }

            pressedCentroid(event.changes)?.let { velocityTracker.addPosition(eventTime, it) }

            canceled = event.changes.any { it.isConsumed }
            if (!canceled) {
                val rawZoom = event.calculateZoom()
                val zoomChange = if (pressedCount == 1 && abs(rawZoom - 1f) < ZOOM_NOISE_THRESHOLD) 1f else rawZoom
                val panChange = event.calculatePan()

                if (!pastTouchSlop) {
                    accumulatedZoom *= zoomChange
                    accumulatedPan += panChange
                    val zoomMotion = abs(1f - accumulatedZoom) * event.calculateCentroidSize(useCurrent = false)
                    if (zoomMotion > viewConfiguration.touchSlop || hypot(accumulatedPan.x, accumulatedPan.y) > viewConfiguration.touchSlop) {
                        pastTouchSlop = true
                    }
                }

                if (pastTouchSlop) {
                    if (pressedCount == 1 && panChange != Offset.Zero) lastSinglePointerPanAt = eventTime
                    val centroid = event.calculateCentroid(useCurrent = false)
                    if (centroid != Offset.Unspecified && (panChange != Offset.Zero || zoomChange != 1f)) {
                        state.onTransform(centroid, panChange, zoomChange)
                    }
                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                }
            } else {
                endingVelocity = null
                endingPanAt = null
            }
        } while (!canceled && event.changes.any { it.pressed })

        val velocity = endingVelocity
        val panAt = endingPanAt
        if (!canceled && velocity != null && panAt != null && state.isZoomed && endingTime - panAt in 0..SINGLE_PAN_RECENCY_MS) {
            if (hypot(velocity.x, velocity.y) > MIN_FLING_SPEED) state.fling(velocity, scope)
        }
    }
}

private fun pressedCentroid(changes: List<PointerInputChange>): Offset? {
    val pressed = changes.filter { it.pressed }
    if (pressed.isEmpty()) return null
    return Offset(
        pressed.sumOf { it.position.x.toDouble() }.toFloat() / pressed.size,
        pressed.sumOf { it.position.y.toDouble() }.toFloat() / pressed.size,
    )
}

private const val ZOOM_NOISE_THRESHOLD = 0.002f
private const val SINGLE_PAN_RECENCY_MS = 100L
private const val MIN_FLING_SPEED = 300f
