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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.absoluteValue

private data class CoinChange(val delta: Int, val id: Long)

/**
 * Pops a "+20 coins" / "−10 coins" pill at the top of the screen whenever the stored balance changes, so every
 * earn, spend and refund anywhere in the app is confirmed from the database value itself.
 */
@Composable
fun CoinChangeToast(coins: Flow<Int>, modifier: Modifier = Modifier) {
    var change by remember { mutableStateOf<CoinChange?>(null) }
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(coins) {
        var previous: Int? = null
        var sequence = 0L
        coins.distinctUntilChanged().collect { balance ->
            val last = previous
            previous = balance
            if (last != null) {
                val delta = balance - last
                change = CoinChange(if (change != null && visible) delta + change!!.delta else delta, ++sequence)
                visible = true
            }
        }
    }
    LaunchedEffect(change?.id) {
        if (change == null) return@LaunchedEffect
        delay(SHOW_MS)
        visible = false
        delay(EXIT_MS)
        if (!visible) change = null
    }

    Box(modifier.fillMaxWidth().statusBarsPadding().padding(top = 8.dp), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(
            visible = visible && change?.delta != 0,
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

private const val SHOW_MS = 1_600L
private const val EXIT_MS = 400L
