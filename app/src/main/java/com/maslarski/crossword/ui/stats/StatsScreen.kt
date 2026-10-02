package com.maslarski.crossword.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Toll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maslarski.crossword.R
import com.maslarski.crossword.domain.profile.Achievement
import com.maslarski.crossword.ui.components.CoinChip
import com.maslarski.crossword.ui.components.formatElapsed
import com.maslarski.crossword.ui.profile.descriptionRes
import com.maslarski.crossword.ui.profile.icon
import com.maslarski.crossword.ui.profile.titleRes
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** My Stats: career numbers across Classic, Daily and Arena, plus the achievements grid. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(onBack: () -> Unit, viewModel: StatsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.stats_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) }
                },
                actions = { CoinChip(state.coins, Modifier.padding(end = 12.dp)) },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            if (state.loading) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
                return@Box
            }
            BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                val columns = if (maxWidth >= 600.dp) 3 else 2
                val p = state.profile
                val tiles = listOf(
                    StatTileUi(Icons.Rounded.TaskAlt, p.puzzlesSolved.toString(), stringResource(R.string.stats_puzzles_solved)),
                    StatTileUi(
                        Icons.Rounded.SportsEsports,
                        stringResource(R.string.stats_arena_record_value, p.arena.wins, p.arena.losses, p.arena.draws),
                        stringResource(R.string.stats_arena_record),
                    ),
                    StatTileUi(Icons.Rounded.Timer, p.fastestSolveSeconds?.let(::formatElapsed) ?: "—", stringResource(R.string.stats_fastest)),
                    StatTileUi(Icons.Rounded.Toll, p.coinsEarned.toString(), stringResource(R.string.stats_coins_earned)),
                    StatTileUi(Icons.Rounded.CalendarMonth, p.dailySolved.toString(), stringResource(R.string.stats_daily_solved)),
                    StatTileUi(
                        Icons.Rounded.LocalFireDepartment,
                        stringResource(R.string.stats_login_streak_value, p.loginStreak, p.bestLoginStreak),
                        stringResource(R.string.stats_login_streak),
                    ),
                )
                Column(
                    modifier = Modifier
                        .widthIn(max = 840.dp)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Grid(tiles, columns) { StatTile(it, Modifier.fillMaxWidth()) }
                    Text(
                        stringResource(R.string.stats_achievements, state.unlockedCount, state.badges.size),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Grid(state.badges, columns) { BadgeCard(it, Modifier.fillMaxWidth()) }
                }
            }
        }
    }
}

private data class StatTileUi(val icon: ImageVector, val value: String, val label: String)

@Composable
private fun <T> Grid(items: List<T>, columns: Int, content: @Composable (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { Box(Modifier.weight(1f)) { content(it) } }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun StatTile(tile: StatTileUi, modifier: Modifier) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), modifier = modifier) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 16.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(tile.icon, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
            Text(tile.value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(tile.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun BadgeCard(badge: BadgeUi, modifier: Modifier) {
    val unlocked = badge.unlockedAt != null
    val achievement = badge.achievement
    val title = stringResource(achievement.titleRes)
    val description = stringResource(achievement.descriptionRes)
    val status = badge.unlockedAt?.let {
        stringResource(R.string.achievement_unlocked_on, Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)))
    } ?: stringResource(R.string.achievement_locked)
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (unlocked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        modifier = modifier.clearAndSetSemantics { contentDescription = "$title. $description. $status" },
    ) {
        Column(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = if (unlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = if (unlocked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(52.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(if (unlocked) achievement.icon else Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(28.dp))
                }
            }
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(if (unlocked) 1f else 0.8f),
            )
            if (unlocked) {
                Text(status, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            } else {
                LinearProgressIndicator(
                    progress = { badge.progress.toFloat() / achievement.target },
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                )
                Text(progressLabel(badge), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun progressLabel(badge: BadgeUi): String =
    if (badge.achievement == Achievement.SPEED_SOLVER) stringResource(R.string.achievement_locked)
    else stringResource(R.string.achievement_progress, badge.progress, badge.achievement.target)
