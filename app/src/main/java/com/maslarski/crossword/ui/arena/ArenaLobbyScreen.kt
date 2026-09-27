package com.maslarski.crossword.ui.arena

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
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maslarski.crossword.R
import com.maslarski.crossword.domain.arena.ArenaRules
import com.maslarski.crossword.domain.arena.ArenaStats
import com.maslarski.crossword.domain.model.Difficulty
import com.maslarski.crossword.ui.components.CoinChip
import com.maslarski.crossword.ui.game.difficultyLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArenaLobbyScreen(
    onBack: () -> Unit,
    onOpenMatch: (matchId: Long) -> Unit,
    viewModel: ArenaLobbyViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    var confirm by rememberSaveable { mutableStateOf<Difficulty?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ArenaLobbyEvent.Open -> onOpenMatch(event.matchId)
                ArenaLobbyEvent.NoPuzzles -> snackbar.showSnackbar(resources.getString(R.string.arena_no_puzzles))
            }
        }
    }

    val start: (Difficulty) -> Unit = { difficulty ->
        if (state.active != null) confirm = difficulty else viewModel.start(difficulty)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.mode_arena_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) }
                },
                actions = { CoinChip(state.coins, Modifier.padding(horizontal = 8.dp)) },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            if (state.loading) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
                return@Box
            }
            Column(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    stringResource(R.string.arena_tagline),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.active?.let { active ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(stringResource(R.string.arena_resume_title), style = MaterialTheme.typography.titleMedium)
                                Text(
                                    stringResource(
                                        R.string.arena_resume_meta,
                                        opponentName(active.state.difficulty),
                                        active.state.playerScore,
                                        active.state.opponentScore,
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            Button(onClick = { onOpenMatch(active.id) }) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text(stringResource(R.string.arena_resume), Modifier.padding(start = 4.dp))
                            }
                        }
                    }
                }

                Text(stringResource(R.string.arena_choose_opponent), style = MaterialTheme.typography.titleMedium)
                Difficulty.entries.forEach { difficulty ->
                    OpponentCard(
                        difficulty = difficulty,
                        unlocked = difficulty in state.stats.unlocked,
                        enabled = !state.starting,
                        onClick = { start(difficulty) },
                    )
                }

                StatsCard(state.stats)

                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.arena_how_to_title), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.arena_how_to_body), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }

    confirm?.let { difficulty ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(stringResource(R.string.arena_new_match_title)) },
            text = { Text(stringResource(R.string.arena_new_match_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    viewModel.start(difficulty)
                }) { Text(stringResource(R.string.arena_new_match_confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun OpponentCard(difficulty: Difficulty, unlocked: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val summary = stringResource(
        when {
            difficulty == Difficulty.MEDIUM && !unlocked -> R.string.arena_locked_medium
            difficulty == Difficulty.HARD && !unlocked -> R.string.arena_locked_hard
            difficulty == Difficulty.EASY -> R.string.arena_opponent_easy_summary
            difficulty == Difficulty.MEDIUM -> R.string.arena_opponent_medium_summary
            else -> R.string.arena_opponent_hard_summary
        },
    )
    ElevatedCard(onClick = onClick, enabled = unlocked && enabled, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (unlocked) Icons.Rounded.SportsEsports else Icons.Rounded.Lock,
                contentDescription = null,
                tint = if (unlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(Modifier.weight(1f).padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "${opponentName(difficulty)} · ${difficultyLabel(difficulty)}",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (unlocked) {
                    Text(
                        stringResource(R.string.arena_win_reward, ArenaRules.WIN_REWARD),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatsCard(stats: ArenaStats) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.arena_stats_title), style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Stat(stats.played, stringResource(R.string.arena_stat_played))
                Stat(stats.wins, stringResource(R.string.arena_stat_wins))
                Stat(stats.losses, stringResource(R.string.arena_stat_losses))
                Stat(stats.bestScore, stringResource(R.string.arena_stat_best))
                Stat(stats.currentStreak, stringResource(R.string.arena_stat_streak))
            }
        }
    }
}

@Composable
private fun Stat(value: Int, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
