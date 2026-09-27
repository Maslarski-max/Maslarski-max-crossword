package com.maslarski.crossword.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maslarski.crossword.R
import com.maslarski.crossword.ui.components.BannerAdSlot
import com.maslarski.crossword.ui.components.CoinChip
import com.maslarski.crossword.ui.game.difficultyLabel
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onBack: () -> Unit,
    onPlay: (sessionId: String, puzzleId: String) -> Unit,
    onLevels: () -> Unit,
    onSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.mode_classic_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) }
                },
                actions = {
                    CoinChip(state.coins, Modifier.padding(horizontal = 4.dp))
                    IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, stringResource(R.string.settings_title)) }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = { BannerAdSlot() },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            if (state.loading) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
                return@Box
            }
            AnimatedVisibility(visible = true, enter = fadeIn() + slideInVertically { it / 8 }) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 720.dp)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    state.daily?.let { daily -> DailyCard(daily, state, onPlay) }
                    state.continuePlaying?.let { card ->
                        PlayCard(stringResource(R.string.home_continue), card, Icons.Rounded.PlayArrow, onPlay)
                    }
                    state.nextLevel?.let { card ->
                        PlayCard(stringResource(R.string.home_next_level), card, Icons.Rounded.PlayArrow, onPlay)
                    }
                    StatsRow(state)
                    OutlinedCard(onClick = onLevels, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.GridView, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                                Text(stringResource(R.string.levels_title), style = MaterialTheme.typography.titleMedium)
                                Text(
                                    stringResource(R.string.home_levels_progress, state.levelsSolved, state.levelsTotal),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DailyCard(daily: PuzzleCard, state: HomeUiState, onPlay: (String, String) -> Unit) {
    val date = state.date?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)).orEmpty()
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CalendarToday, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(
                    stringResource(R.string.home_daily_title, date),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Text(daily.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(difficultyLabel(daily.difficulty), style = MaterialTheme.typography.bodyMedium)
            if (daily.completed) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null)
                    Text(
                        pluralStringResource(R.plurals.home_daily_done, daily.score, daily.score),
                        modifier = Modifier.padding(start = 8.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            } else if (daily.progress > 0f) {
                LinearProgressIndicator(progress = { daily.progress }, modifier = Modifier.fillMaxWidth())
            }
            Button(onClick = { onPlay(daily.sessionId, daily.puzzleId) }) {
                Text(
                    stringResource(
                        when {
                            daily.completed -> R.string.home_view
                            daily.progress > 0f -> R.string.home_resume
                            else -> R.string.home_play
                        },
                    ),
                )
            }
        }
    }
}

@Composable
private fun PlayCard(label: String, card: PuzzleCard, icon: ImageVector, onPlay: (String, String) -> Unit) {
    ElevatedCard(onClick = { onPlay(card.sessionId, card.puzzleId) }, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(card.title, style = MaterialTheme.typography.titleLarge)
                Text(
                    difficultyLabel(card.difficulty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (card.progress > 0f) {
                    LinearProgressIndicator(progress = { card.progress }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                }
            }
            FilledTonalButton(onClick = { onPlay(card.sessionId, card.puzzleId) }, modifier = Modifier.padding(start = 16.dp)) {
                Icon(icon, contentDescription = null)
            }
        }
    }
}

@Composable
private fun StatsRow(state: HomeUiState) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        StatTile(Icons.Rounded.TaskAlt, state.stats.puzzlesSolved.toString(), stringResource(R.string.stat_solved), Modifier.weight(1f))
        StatTile(Icons.Rounded.EmojiEvents, state.stats.bestScore.toString(), stringResource(R.string.stat_best), Modifier.weight(1f))
        StatTile(Icons.Rounded.LocalFireDepartment, state.stats.dailyStreak.toString(), stringResource(R.string.stat_streak), Modifier.weight(1f))
    }
}

@Composable
private fun StatTile(icon: ImageVector, value: String, label: String, modifier: Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = modifier,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 16.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
