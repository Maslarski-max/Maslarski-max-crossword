package com.maslarski.crossword.ui.profile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.maslarski.crossword.R
import com.maslarski.crossword.domain.profile.Achievement
import com.maslarski.crossword.domain.profile.LoginReward
import com.maslarski.crossword.domain.profile.LoginStreaks
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow

/** Daily login bonus: the coins are already in the wallet, this only celebrates them. */
@Composable
fun LoginRewardDialog(reward: LoginReward, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.LocalFireDepartment, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(36.dp)) },
        title = { Text(stringResource(R.string.login_reward_title, reward.cycleDay), textAlign = TextAlign.Center) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                    LoginStreaks.REWARDS.forEachIndexed { index, coins ->
                        RewardDay(day = index + 1, coins = coins, current = reward.cycleDay, modifier = Modifier.weight(1f))
                    }
                }
                Text(
                    pluralStringResource(R.plurals.login_reward_message, reward.coins, reward.coins),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    pluralStringResource(R.plurals.login_reward_streak, reward.streak, reward.streak),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text(stringResource(R.string.login_reward_collect)) } },
    )
}

@Composable
private fun RewardDay(day: Int, coins: Int, current: Int, modifier: Modifier) {
    val claimed = day < current
    val today = day == current
    val container = when {
        today -> MaterialTheme.colorScheme.tertiary
        claimed -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val content = when {
        today -> MaterialTheme.colorScheme.onTertiary
        claimed -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(shape = RoundedCornerShape(8.dp), color = container, contentColor = content, modifier = modifier.aspectRatio(0.62f)) {
        Column(
            Modifier.padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            Text(stringResource(R.string.login_reward_day_short, day), style = MaterialTheme.typography.labelSmall)
            if (claimed) {
                Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp))
            } else {
                Text("+$coins", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Shows "Achievement unlocked" banners at the bottom of the screen, one at a time. */
@Composable
fun AchievementToast(unlocks: Flow<Achievement>, modifier: Modifier = Modifier) {
    var shown by remember { mutableStateOf<Achievement?>(null) }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(unlocks) {
        unlocks.collect { achievement ->
            shown = achievement
            visible = true
            delay(SHOW_MS)
            visible = false
            delay(EXIT_MS)
        }
    }
    Box(modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 24.dp), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically { it } + fadeIn() + scaleIn(initialScale = 0.8f),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            val achievement = shown ?: return@AnimatedVisibility
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shadowElevation = 6.dp,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            ) {
                Row(
                    Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(achievement.icon, contentDescription = null)
                    Column {
                        Text(stringResource(R.string.achievement_unlocked), style = MaterialTheme.typography.labelMedium)
                        Text(stringResource(achievement.titleRes), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private const val SHOW_MS = 2_600L
private const val EXIT_MS = 400L
