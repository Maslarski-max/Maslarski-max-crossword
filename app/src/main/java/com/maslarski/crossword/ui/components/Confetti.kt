package com.maslarski.crossword.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.sin
import kotlin.random.Random

private class Particle(
    val x: Float,
    val delay: Float,
    val speed: Float,
    val sway: Float,
    val spin: Float,
    val size: Float,
    val color: Color,
)

/** One-shot confetti burst for puzzle completion. */
@Composable
fun Confetti(colors: List<Color>, modifier: Modifier = Modifier, count: Int = 90) {
    val particles = remember {
        val random = Random(System.nanoTime())
        List(count) {
            Particle(
                x = random.nextFloat(),
                delay = random.nextFloat() * 0.35f,
                speed = 0.8f + random.nextFloat() * 0.7f,
                sway = 0.02f + random.nextFloat() * 0.05f,
                spin = random.nextFloat() * 720f - 360f,
                size = 6f + random.nextFloat() * 8f,
                color = colors[random.nextInt(colors.size)],
            )
        }
    }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(durationMillis = 3200, easing = LinearEasing)) }

    Canvas(modifier) {
        val t = progress.value
        if (t >= 1f) return@Canvas
        particles.forEach { p ->
            val local = ((t - p.delay) / (1f - p.delay)).coerceIn(0f, 1f)
            if (local <= 0f) return@forEach
            val y = -0.05f + local * p.speed * 1.1f
            val x = p.x + sin(local * 12f + p.x * 10f) * p.sway
            val center = Offset(x * size.width, y * size.height)
            val alpha = if (local > 0.8f) (1f - local) / 0.2f else 1f
            rotate(p.spin * local, center) {
                drawRect(
                    color = p.color.copy(alpha = alpha),
                    topLeft = center - Offset(p.size / 2, p.size / 4),
                    size = Size(p.size, p.size / 2),
                )
            }
        }
    }
}
