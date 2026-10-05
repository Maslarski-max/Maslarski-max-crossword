package com.maslarski.crossword.ui.arena

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Toll
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maslarski.crossword.R
import com.maslarski.crossword.domain.arena.ArenaOutcome
import com.maslarski.crossword.domain.arena.ArenaResult
import com.maslarski.crossword.domain.arena.ArenaRules
import com.maslarski.crossword.domain.arena.ArenaWord
import com.maslarski.crossword.domain.arena.PlacementError
import com.maslarski.crossword.domain.arena.Side
import com.maslarski.crossword.domain.model.Difficulty
import com.maslarski.crossword.domain.engine.Hint
import com.maslarski.crossword.domain.model.Direction
import com.maslarski.crossword.ui.components.CoinChip
import com.maslarski.crossword.ui.components.CoinPrompt
import com.maslarski.crossword.ui.components.CoinPromptDialog
import com.maslarski.crossword.ui.components.Confetti
import com.maslarski.crossword.ui.components.ZoomableGridState
import com.maslarski.crossword.ui.theme.LocalArenaColors
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private data class DragTile(val slot: Int, val letter: Char, val position: Offset)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArenaScreen(
    onBack: () -> Unit,
    onRematch: (matchId: Long) -> Unit,
    onShop: () -> Unit,
    viewModel: ArenaViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()

    var coinPrompt by remember { mutableStateOf<CoinPrompt?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            if (message is ArenaMessage.NotEnoughCoins) {
                coinPrompt = CoinPrompt.NotEnoughCoins(message.cost)
                return@collect
            }
            val text = when (message) {
                is ArenaMessage.Invalid -> resources.getString(
                    when (message.error) {
                        PlacementError.CELL_TAKEN -> R.string.arena_message_cell_taken
                        else -> R.string.arena_message_not_one_word
                    },
                )
                is ArenaMessage.Missed -> resources.getQuantityString(R.plurals.arena_message_missed, message.count, message.count)
                is ArenaMessage.Scored -> resources.getQuantityString(R.plurals.arena_message_scored, message.points, message.points)
                is ArenaMessage.OpponentScored ->
                    resources.getQuantityString(R.plurals.arena_message_opponent_scored, message.points, message.points)
                ArenaMessage.OpponentMissed -> resources.getString(R.string.arena_message_opponent_missed)
                ArenaMessage.OpponentPassed -> resources.getString(R.string.arena_message_opponent_passed)
                ArenaMessage.NoHintMoves -> resources.getString(R.string.arena_message_no_hint)
                is ArenaMessage.NotEnoughCoins -> return@collect
            }
            snackbar.currentSnackbarData?.dismiss()
            scope.launch { snackbar.showSnackbar(text) }
        }
    }
    LaunchedEffect(viewModel) { viewModel.rematches.collect(onRematch) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.mode_arena_title), style = MaterialTheme.typography.titleMedium, maxLines = 1)
                        state.match?.let {
                            Text(
                                stringResource(R.string.arena_vs_opponent, opponentName(it.difficulty)),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) }
                },
                actions = { CoinChip(state.coins, Modifier.padding(horizontal = 8.dp), onClick = onShop) },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.notFound -> Column(
                    Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(stringResource(R.string.arena_match_not_found), style = MaterialTheme.typography.bodyLarge)
                    Button(onClick = onBack) { Text(stringResource(R.string.arena_back_to_lobby)) }
                }
                else -> ArenaBoard(state, viewModel)
            }
            val result = state.result
            if (result != null && state.showResult) {
                ArenaResultOverlay(
                    result = result,
                    onRematch = viewModel::rematch,
                    onLobby = onBack,
                    onViewBoard = viewModel::dismissResult,
                )
            }
        }
    }

    coinPrompt?.let { prompt ->
        CoinPromptDialog(
            prompt,
            state.coins,
            onUnlock = {},
            onDismiss = { coinPrompt = null },
            onShop = {
                coinPrompt = null
                onShop()
            },
        )
    }
}

@Composable
private fun ArenaBoard(state: ArenaUiState, viewModel: ArenaViewModel) {
    val layout = state.layout ?: return
    val match = state.match ?: return
    val gridState = remember(layout) { ZoomableGridState() }
    var drag by remember { mutableStateOf<DragTile?>(null) }
    var rootOrigin by remember { mutableStateOf(Offset.Zero) }
    val dropTarget = drag?.let { gridState.cellAtRoot(it.position) }

    val grid = @Composable { modifier: Modifier ->
        ArenaGrid(
            layout = layout,
            match = match,
            pending = state.pendingLetters,
            opponentTiles = state.opponentTiles,
            activeWord = state.activeWord,
            focusCell = state.focusCell,
            missCells = state.missCells,
            dropTarget = dropTarget,
            flash = state.flash,
            state = gridState,
            onCellTap = viewModel::onCellTap,
            description = stringResource(R.string.arena_grid_description, match.emptyCells),
            modifier = modifier,
        )
    }
    val controls = @Composable { tileSize: Dp ->
        LetterRack(
            rack = match.playerRack,
            used = state.pending.values.toSet(),
            selected = state.selectedSlot,
            dragging = drag?.slot,
            enabled = state.playerTurn,
            tileSize = tileSize,
            onTap = viewModel::onSlotTap,
            onDragStart = { slot, position -> drag = DragTile(slot, match.playerRack[slot], position) },
            onDrag = { delta -> drag = drag?.let { it.copy(position = it.position + delta) } },
            onDragEnd = {
                drag?.let { viewModel.onDrop(it.slot, gridState.cellAtRoot(it.position)) }
                drag = null
            },
            onDragCancel = { drag = null },
        )
        ArenaControls(
            hasPending = state.pending.isNotEmpty(),
            hintCost = if (state.unlimited) 0 else Hint.REVEAL_LETTER.cost,
            enabled = state.playerTurn,
            onShuffle = viewModel::onShuffle,
            onSubmit = viewModel::onSubmit,
            onPass = viewModel::onPass,
            onHint = viewModel::onHint,
        )
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { rootOrigin = it.positionInRoot() },
    ) {
        val wide = maxWidth >= 720.dp && maxWidth > maxHeight
        val tileSize = min(56.dp, (if (wide) maxWidth * 0.4f else maxWidth) / (ArenaRules.RACK_SIZE + 1.5f))
        if (wide) {
            Row(Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                grid(Modifier.weight(1.3f).fillMaxHeight())
                Column(
                    Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ScoreBanner(state)
                    ClueStrip(state.activeWord)
                    controls(tileSize)
                }
            }
        } else {
            Column(
                Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ScoreBanner(state)
                grid(Modifier.weight(1f).fillMaxWidth())
                ClueStrip(state.activeWord)
                controls(tileSize)
            }
        }

        drag?.let { tile ->
            val half = with(LocalDensity.current) { (tileSize * 0.6f).toPx() }
            LetterTile(
                letter = tile.letter,
                size = tileSize * 1.2f,
                modifier = Modifier
                    .offset {
                        val p = tile.position - rootOrigin - Offset(half, half * 1.6f)
                        IntOffset(p.x.roundToInt(), p.y.roundToInt())
                    }
                    .shadow(8.dp, RoundedCornerShape(10.dp)),
            )
        }
    }
}

@Composable
private fun ScoreBanner(state: ArenaUiState) {
    val match = state.match ?: return
    val colors = LocalArenaColors.current
    val description = stringResource(R.string.arena_score_description, match.playerScore, match.opponentScore)
    val status = when {
        match.finished -> stringResource(R.string.arena_match_over)
        state.playerTurn -> stringResource(R.string.arena_your_turn)
        else -> stringResource(R.string.arena_opponent_turn)
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(2.dp, if (state.playerTurn) colors.player else MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.clearAndSetSemantics { contentDescription = "$description. $status" },
        ) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(stringResource(R.string.arena_you), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                ScorePill(match.playerScore, colors.player, active = state.playerTurn)
                Text(stringResource(R.string.arena_vs), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ScorePill(match.opponentScore, colors.opponent, active = !match.finished && match.turn == Side.OPPONENT)
                Text(stringResource(R.string.arena_opponent), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
        AnimatedContent(status, label = "status") { text ->
            Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ScorePill(score: Int, color: Color, active: Boolean) {
    val bounce = remember { Animatable(1f) }
    LaunchedEffect(score) {
        bounce.snapTo(1.3f)
        bounce.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium))
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .graphicsLayer {
                scaleX = bounce.value
                scaleY = bounce.value
            }
            .background(if (active) color else color.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 2.dp),
    ) {
        AnimatedContent(
            targetState = score,
            transitionSpec = { (slideInVertically { -it } + fadeIn()) togetherWith (slideOutVertically { it } + fadeOut()) },
            label = "score",
        ) { value ->
            Text(
                value.toString(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (active) MaterialTheme.colorScheme.surface else color,
            )
        }
    }
}

@Composable
private fun ClueStrip(word: ArenaWord?) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (word != null) {
                Icon(
                    if (word.direction == Direction.ACROSS) Icons.AutoMirrored.Rounded.ArrowForward else Icons.Rounded.ArrowDownward,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(8.dp))
            }
            Text(
                if (word != null) stringResource(R.string.arena_clue_length, word.clue, word.cells.size) else stringResource(R.string.arena_clue_prompt),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LetterRack(
    rack: String,
    used: Set<Int>,
    selected: Int?,
    dragging: Int?,
    enabled: Boolean,
    tileSize: Dp,
    onTap: (Int) -> Unit,
    onDragStart: (slot: Int, rootPosition: Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 6.dp)) {
        repeat(ArenaRules.RACK_SIZE) { slot ->
            val letter = rack.getOrNull(slot)
            if (letter == null || slot in used) {
                Box(
                    Modifier
                        .size(tileSize)
                        .border(2.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp)),
                )
                return@repeat
            }
            var origin by remember { mutableStateOf(Offset.Zero) }
            val lift by animateDpAsState(if (slot == selected) (-8).dp else 0.dp, label = "lift")
            val label = if (slot == selected) stringResource(R.string.arena_tile_selected, letter.toString()) else stringResource(R.string.arena_tile, letter.toString())
            LetterTile(
                letter = letter,
                size = tileSize,
                highlighted = slot == selected,
                dimmed = !enabled,
                modifier = Modifier
                    .offset { IntOffset(0, lift.roundToPx()) }
                    .graphicsLayer { alpha = if (slot == dragging) 0.25f else 1f }
                    .onGloballyPositioned { origin = it.positionInRoot() }
                    .semantics { contentDescription = label }
                    .clickable(enabled = enabled) { onTap(slot) }
                    .pointerInput(slot, letter, enabled) {
                        if (!enabled) return@pointerInput
                        detectDragGestures(
                            onDragStart = { local -> onDragStart(slot, origin + local) },
                            onDrag = { change, amount ->
                                change.consume()
                                onDrag(amount)
                            },
                            onDragEnd = onDragEnd,
                            onDragCancel = onDragCancel,
                        )
                    },
            )
        }
    }
}

@Composable
private fun LetterTile(letter: Char, size: Dp, modifier: Modifier = Modifier, highlighted: Boolean = false, dimmed: Boolean = false) {
    val colors = LocalArenaColors.current
    val shape = RoundedCornerShape(10.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .graphicsLayer { alpha = if (dimmed) 0.55f else 1f }
            .background(colors.tileEdge, shape)
            .padding(bottom = 3.dp)
            .background(colors.tile, shape)
            .then(if (highlighted) Modifier.border(2.dp, colors.player, shape) else Modifier),
    ) {
        Text(
            letter.toString(),
            color = colors.onTile,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.5f).sp,
        )
    }
}

@Composable
private fun ArenaControls(
    hasPending: Boolean,
    hintCost: Int,
    enabled: Boolean,
    onShuffle: () -> Unit,
    onSubmit: () -> Unit,
    onPass: () -> Unit,
    onHint: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth().padding(vertical = 4.dp),
    ) {
        FilledTonalIconButton(onClick = onShuffle, enabled = enabled, modifier = Modifier.size(52.dp)) {
            Icon(Icons.Rounded.Shuffle, stringResource(R.string.arena_shuffle))
        }
        AnimatedContent(hasPending, label = "submitOrPass", modifier = Modifier.weight(1f)) { pending ->
            if (pending) {
                Button(onClick = onSubmit, enabled = enabled, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text(stringResource(R.string.arena_submit), style = MaterialTheme.typography.titleMedium)
                }
            } else {
                OutlinedButton(onClick = onPass, enabled = enabled, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text(stringResource(R.string.arena_pass), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        BadgedBox(
            badge = {
                Badge(containerColor = MaterialTheme.colorScheme.tertiary, contentColor = MaterialTheme.colorScheme.onTertiary) {
                    Icon(Icons.Rounded.Toll, contentDescription = null, modifier = Modifier.size(12.dp))
                    Text(hintCost.toString())
                }
            },
            modifier = Modifier.padding(end = 12.dp),
        ) {
            FilledTonalIconButton(onClick = onHint, enabled = enabled, modifier = Modifier.size(52.dp)) {
                Icon(Icons.Rounded.Lightbulb, stringResource(R.string.arena_hint, hintCost))
            }
        }
    }
}

@Composable
private fun ArenaResultOverlay(result: ArenaResult, onRematch: () -> Unit, onLobby: () -> Unit, onViewBoard: () -> Unit) {
    val card = remember { Animatable(0f) }
    LaunchedEffect(Unit) { card.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow)) }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
    ) {
        if (result.outcome == ArenaOutcome.WON) {
            Confetti(
                colors = listOf(
                    MaterialTheme.colorScheme.primary,
                    MaterialTheme.colorScheme.tertiary,
                    MaterialTheme.colorScheme.secondary,
                    LocalArenaColors.current.opponent,
                    MaterialTheme.colorScheme.inversePrimary,
                ),
                modifier = Modifier.fillMaxSize(),
            )
        }
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
                Text(
                    stringResource(
                        when (result.outcome) {
                            ArenaOutcome.WON -> R.string.arena_result_won
                            ArenaOutcome.LOST -> R.string.arena_result_lost
                            ArenaOutcome.DRAW -> R.string.arena_result_draw
                            ArenaOutcome.FORFEIT -> R.string.arena_result_forfeit
                        },
                    ),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(R.string.arena_result_score, result.playerScore, result.opponentScore),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (result.coinsEarned > 0) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.tertiaryContainer) {
                        Text(
                            pluralStringResource(R.plurals.completion_coins, result.coinsEarned, result.coinsEarned),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                    }
                }
                Button(onClick = onRematch, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.arena_rematch)) }
                OutlinedButton(onClick = onLobby, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.arena_back_to_lobby)) }
                TextButton(onClick = onViewBoard) { Text(stringResource(R.string.completion_view_board)) }
            }
        }
    }
}

@Composable
fun opponentName(difficulty: Difficulty): String = stringResource(
    when (difficulty) {
        Difficulty.EASY -> R.string.arena_opponent_easy
        Difficulty.MEDIUM -> R.string.arena_opponent_medium
        Difficulty.HARD -> R.string.arena_opponent_hard
    },
)
