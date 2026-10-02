package com.maslarski.crossword.ui.levels

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.Toll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maslarski.crossword.R
import com.maslarski.crossword.domain.engine.GameRules
import com.maslarski.crossword.ui.components.CoinChip
import com.maslarski.crossword.ui.components.CoinPrompt
import com.maslarski.crossword.ui.components.CoinPromptDialog
import com.maslarski.crossword.ui.components.formatElapsed
import com.maslarski.crossword.ui.game.difficultyLabel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LevelsScreen(
    onBack: () -> Unit,
    onPlay: (sessionId: String, puzzleId: String) -> Unit,
    onShop: () -> Unit,
    viewModel: LevelsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    var coinPrompt by remember { mutableStateOf<CoinPrompt?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is LevelsEvent.Open -> onPlay(event.level.sessionId, event.level.puzzleId)
                is LevelsEvent.Prompt -> coinPrompt = event.prompt
                LevelsEvent.PreviousLocked -> {
                    snackbar.currentSnackbarData?.dismiss()
                    launch { snackbar.showSnackbar(resources.getString(R.string.level_locked)) }
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.levels_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) }
                },
                actions = { CoinChip(state.coins, Modifier.padding(horizontal = 8.dp)) },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (state.loading) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
                return@Box
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 300.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.levels, key = { it.puzzleId }) { level ->
                    LevelCard(level, onClick = { viewModel.onLevelTap(level) })
                }
            }
        }
    }

    coinPrompt?.let { prompt ->
        CoinPromptDialog(
            prompt = prompt,
            balance = state.coins,
            onUnlock = { puzzleId ->
                coinPrompt = null
                viewModel.unlock(puzzleId)
            },
            onDismiss = { coinPrompt = null },
            onShop = {
                coinPrompt = null
                onShop()
            },
        )
    }
}

@Composable
private fun LevelCard(level: LevelItem, onClick: () -> Unit) {
    val alpha by animateFloatAsState(if (level.unlocked || level.unlockable) 1f else 0.55f, label = "levelAlpha")
    val lockedLabel = if (level.unlockable) {
        stringResource(R.string.level_unlock_cost, GameRules.LEVEL_UNLOCK_COST)
    } else {
        stringResource(R.string.level_locked)
    }
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { this.alpha = alpha }
            .then(if (!level.unlocked) Modifier.semantics { contentDescription = "${level.title}, $lockedLabel" } else Modifier),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = CircleShape,
                color = if (level.completed) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(48.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (level.unlocked) {
                        Text(level.number.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Rounded.Lock, contentDescription = null)
                    }
                }
            }
            Column(Modifier.weight(1f).padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(level.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.level_meta, difficultyLabel(level.difficulty), level.cols, level.rows, level.wordCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (level.unlockable && !level.unlocked) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Rounded.Toll, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(16.dp))
                        Text(lockedLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                    }
                }
                if (level.completed) {
                    Text(
                        stringResource(R.string.level_best, level.bestScore, formatElapsed(level.bestTimeSeconds ?: 0)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (level.completed) {
                Row {
                    repeat(3) { i ->
                        Icon(
                            if (i < level.stars) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}
