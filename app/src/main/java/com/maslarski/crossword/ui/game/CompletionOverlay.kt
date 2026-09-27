package com.maslarski.crossword.ui.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.maslarski.crossword.R
import com.maslarski.crossword.domain.model.CompletionResult
import com.maslarski.crossword.ui.components.Confetti
import com.maslarski.crossword.ui.components.formatElapsed
import kotlinx.coroutines.delay

@Composable
fun CompletionOverlay(
    result: CompletionResult,
    onNext: (() -> Unit)?,
    onDone: () -> Unit,
    onViewBoard: () -> Unit,
) {
    val card = remember { Animatable(0f) }
    LaunchedEffect(Unit) { card.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow)) }
    var target by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { target = result.score }
    val score by animateIntAsState(target, tween(durationMillis = 1200), label = "score")

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
    ) {
        Confetti(
            colors = listOf(
                MaterialTheme.colorScheme.primary,
                MaterialTheme.colorScheme.tertiary,
                MaterialTheme.colorScheme.secondary,
                MaterialTheme.colorScheme.error,
                MaterialTheme.colorScheme.inversePrimary,
            ),
            modifier = Modifier.fillMaxSize(),
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            modifier = Modifier
                .padding(24.dp)
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .graphicsLayer {
                    val v = card.value
                    scaleX = 0.7f + 0.3f * v
                    scaleY = 0.7f + 0.3f * v
                    alpha = v.coerceIn(0f, 1f)
                },
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(24.dp),
            ) {
                Text(stringResource(R.string.completion_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                val starsLabel = stringResource(R.string.completion_stars, result.stars)
                Row(Modifier.semantics { contentDescription = starsLabel }) {
                    repeat(3) { i -> AnimatedStar(filled = i < result.stars, delayMillis = 300L + i * 250L) }
                }
                Text(score.toString(), style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.primary)
                if (result.isNewBest) {
                    Text(stringResource(R.string.completion_new_best), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary)
                }
                Text(
                    stringResource(R.string.completion_time, formatElapsed(result.elapsedSeconds)),
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (result.coinsEarned > 0) {
                    Text(pluralStringResource(R.plurals.completion_coins, result.coinsEarned, result.coinsEarned), style = MaterialTheme.typography.bodyLarge)
                }
                if (onNext != null) {
                    Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.completion_next)) }
                }
                OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.completion_done)) }
                TextButton(onClick = onViewBoard) { Text(stringResource(R.string.completion_view_board)) }
            }
        }
    }
}

@Composable
private fun AnimatedStar(filled: Boolean, delayMillis: Long) {
    val scale = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(delayMillis)
        scale.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMediumLow))
    }
    Icon(
        imageVector = if (filled) Icons.Rounded.Star else Icons.Rounded.StarOutline,
        contentDescription = null,
        tint = if (filled) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline,
        modifier = Modifier.size(48.dp).graphicsLayer { scaleX = scale.value; scaleY = scale.value },
    )
}
