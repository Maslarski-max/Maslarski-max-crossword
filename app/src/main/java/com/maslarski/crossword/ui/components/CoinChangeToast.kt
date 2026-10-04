package com.maslarski.crossword.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Toll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.maslarski.crossword.R
import com.maslarski.crossword.domain.model.CoinChange
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.absoluteValue
import kotlin.time.TimeSource

/**
 * Pops a "+20 coins" / "−10 coins" pill at the top of the screen for every committed wallet change ([changes] is
 * [com.maslarski.crossword.domain.repository.WalletRepository.observeChanges]), one at a time in order, and reports
 * each through [onShown] once its full display time has passed. While [paused] emits true the pill stays hidden and
 * its remaining time is kept, e.g. behind a dialog, so none expires unseen.
 */
@Composable
fun CoinChangeToast(
    changes: Flow<List<CoinChange>>,
    onShown: (Long) -> Unit,
    modifier: Modifier = Modifier,
    paused: Flow<Boolean> = flowOf(false),
) {
    var change by remember { mutableStateOf<CoinChange?>(null) }
    var visible by remember { mutableStateOf(false) }
    val currentPaused by rememberUpdatedState(paused)
    val currentOnShown by rememberUpdatedState(onShown)

    LaunchedEffect(changes) {
        var lastShown = Long.MIN_VALUE
        while (true) {
            val next = changes.mapNotNull { pending -> pending.firstOrNull { it.id > lastShown } }.first()
            change = next
            var remaining = SHOW_MS
            while (true) {
                currentPaused.firstOrNull { !it } ?: awaitCancellation()
                visible = true
                val shownAt = TimeSource.Monotonic.markNow()
                val pausedAgain = withTimeoutOrNull(remaining) { currentPaused.firstOrNull { it } ?: awaitCancellation() }
                visible = false
                if (pausedAgain == null) break
                remaining = (remaining - shownAt.elapsedNow().inWholeMilliseconds).coerceAtLeast(MIN_RESUME_MS)
            }
            lastShown = next.id
            currentOnShown(next.id)
            delay(EXIT_MS)
        }
    }

    Box(modifier.fillMaxWidth().statusBarsPadding().padding(top = 8.dp), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically { -it } + fadeIn() + scaleIn(initialScale = 0.8f),
            exit = slideOutVertically { -it } + fadeOut(),
        ) {
            val delta = change?.delta ?: 0
            val gain = delta > 0
            val amount = delta.absoluteValue
            Surface(
                shape = CircleShape,
                color = if (gain) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.errorContainer,
                contentColor = if (gain) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                shadowElevation = 6.dp,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            ) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Rounded.Toll, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(
                        pluralStringResource(if (gain) R.plurals.coin_change_gain else R.plurals.coin_change_spend, amount, amount),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

private const val SHOW_MS = 1_400L
private const val EXIT_MS = 400L
private const val MIN_RESUME_MS = 600L
