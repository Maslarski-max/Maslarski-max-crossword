package com.maslarski.crossword.ui.hub

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maslarski.crossword.R
import com.maslarski.crossword.ui.components.CoinChip

/** Start screen: the Daily Challenge, Classic Crossword, Crossword Arena and My Stats. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HubScreen(
    onClassic: () -> Unit,
    onArena: () -> Unit,
    onDaily: () -> Unit,
    onStats: () -> Unit,
    onSettings: () -> Unit,
    viewModel: HubViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.app_name), fontWeight = FontWeight.Bold) },
                actions = {
                    CoinChip(state.coins, Modifier.padding(horizontal = 4.dp))
                    IconButton(onClick = onStats) { Icon(Icons.Rounded.Insights, stringResource(R.string.stats_title)) }
                    IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, stringResource(R.string.settings_title)) }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            if (state.loading) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
                return@Box
            }
            AnimatedVisibility(visible = true, enter = fadeIn() + slideInVertically { it / 8 }) {
                BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    val wide = maxWidth >= 720.dp
                    Column(
                        modifier = Modifier
                            .widthIn(max = 960.dp)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(
                            stringResource(R.string.hub_subtitle),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val classic = @Composable { modifier: Modifier ->
                            ModeCard(
                                title = stringResource(R.string.mode_classic_title),
                                summary = stringResource(R.string.mode_classic_summary),
                                stats = stringResource(R.string.mode_classic_stats, state.levelsSolved, state.levelsTotal, state.classic.dailyStreak),
                                icon = Icons.Rounded.GridOn,
                                accent = MaterialTheme.colorScheme.primary,
                                container = MaterialTheme.colorScheme.primaryContainer,
                                action = stringResource(R.string.mode_play),
                                onClick = onClassic,
                                modifier = modifier,
                            )
                        }
                        val arena = @Composable { modifier: Modifier ->
                            ModeCard(
                                title = stringResource(R.string.mode_arena_title),
                                summary = stringResource(R.string.mode_arena_summary),
                                stats = stringResource(R.string.mode_arena_stats, state.arena.wins, state.arena.losses, state.arena.bestScore),
                                icon = Icons.Rounded.SportsEsports,
                                accent = MaterialTheme.colorScheme.tertiary,
                                container = MaterialTheme.colorScheme.tertiaryContainer,
                                action = stringResource(if (state.arenaInProgress) R.string.arena_resume else R.string.mode_play),
                                onClick = onArena,
                                modifier = modifier,
                            )
                        }
                        val daily = @Composable { modifier: Modifier ->
                            ModeCard(
                                title = stringResource(R.string.daily_title),
                                summary = stringResource(R.string.mode_daily_summary),
                                stats = stringResource(
                                    if (state.dailySolvedToday) R.string.mode_daily_stats_done else R.string.mode_daily_stats_open,
                                    state.classic.dailyStreak,
                                ),
                                icon = Icons.Rounded.CalendarMonth,
                                accent = MaterialTheme.colorScheme.secondary,
                                container = MaterialTheme.colorScheme.secondaryContainer,
                                action = stringResource(R.string.mode_daily_action),
                                onClick = onDaily,
                                modifier = modifier,
                            )
                        }
                        val stats = @Composable { modifier: Modifier ->
                            ModeCard(
                                title = stringResource(R.string.stats_title),
                                summary = stringResource(R.string.mode_stats_summary),
                                stats = stringResource(R.string.mode_stats_stats, state.achievementsUnlocked, state.achievementsTotal),
                                icon = Icons.Rounded.Insights,
                                accent = MaterialTheme.colorScheme.primary,
                                container = MaterialTheme.colorScheme.surfaceContainerHighest,
                                action = stringResource(R.string.mode_stats_action),
                                onClick = onStats,
                                modifier = modifier,
                            )
                        }
                        if (wide) {
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                classic(Modifier.weight(1f))
                                arena(Modifier.weight(1f))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                daily(Modifier.weight(1f))
                                stats(Modifier.weight(1f))
                            }
                        } else {
                            daily(Modifier.fillMaxWidth())
                            classic(Modifier.fillMaxWidth())
                            arena(Modifier.fillMaxWidth())
                            stats(Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeCard(
    title: String,
    summary: String,
    stats: String,
    icon: ImageVector,
    accent: Color,
    container: Color,
    action: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(onClick = onClick, colors = CardDefaults.elevatedCardColors(), modifier = modifier) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = container, modifier = Modifier.size(56.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(32.dp))
                    }
                }
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 16.dp),
                )
            }
            Text(summary, style = MaterialTheme.typography.bodyMedium)
            Text(stats, style = MaterialTheme.typography.labelLarge, color = accent)
            Button(onClick = onClick, modifier = Modifier.align(Alignment.End)) { Text(action) }
        }
    }
}
