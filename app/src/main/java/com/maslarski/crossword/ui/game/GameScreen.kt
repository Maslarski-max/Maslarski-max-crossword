package com.maslarski.crossword.ui.game

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ListAlt
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.maslarski.crossword.R
import com.maslarski.crossword.domain.model.Difficulty
import com.maslarski.crossword.domain.model.GameSession
import com.maslarski.crossword.ui.components.ClueBar
import com.maslarski.crossword.ui.components.CoinChip
import com.maslarski.crossword.ui.components.CoinPrompt
import com.maslarski.crossword.ui.components.CoinPromptDialog
import com.maslarski.crossword.ui.components.CrosswordGrid
import com.maslarski.crossword.ui.components.LetterKeyboard
import com.maslarski.crossword.ui.components.clueSection
import com.maslarski.crossword.ui.components.formatElapsed
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(
    selectedWordResult: StateFlow<String?>,
    onSelectedWordConsumed: () -> Unit,
    onBack: () -> Unit,
    onOpenClues: (sessionId: String, puzzleId: String) -> Unit,
    onPlayNext: (sessionId: String, puzzleId: String) -> Unit,
    viewModel: GameViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val selectedWord by selectedWordResult.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    var menuOpen by remember { mutableStateOf(false) }
    var confirmRestart by rememberSaveable { mutableStateOf(false) }

    LifecycleResumeEffect(viewModel) {
        viewModel.onForeground()
        onPauseOrDispose { viewModel.onBackground() }
    }

    LaunchedEffect(selectedWord, state.puzzle) {
        val key = selectedWord ?: return@LaunchedEffect
        if (state.puzzle == null) return@LaunchedEffect
        viewModel.onSelectWordKey(key)
        onSelectedWordConsumed()
    }

    var coinPrompt by remember { mutableStateOf<CoinPrompt?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            val text = when (message) {
                is GameMessage.NotEnoughCoins -> {
                    coinPrompt = CoinPrompt.NotEnoughCoins(message.cost)
                    return@collect
                }
                is GameMessage.OfferUnlock -> {
                    coinPrompt = CoinPrompt.UnlockLevel(message.puzzleId, message.title, message.cost)
                    return@collect
                }
                is GameMessage.OpenLevel -> {
                    onPlayNext(GameSession.Level(message.puzzleId).id, message.puzzleId)
                    return@collect
                }
                is GameMessage.ErrorsFound -> resources.getQuantityString(R.plurals.message_errors_found, message.count, message.count)
                GameMessage.NoErrors -> resources.getString(R.string.message_no_errors)
                GameMessage.NothingToReveal -> resources.getString(R.string.message_nothing_to_reveal)
                GameMessage.NothingToCheck -> resources.getString(R.string.message_nothing_to_check)
            }
            snackbar.currentSnackbarData?.dismiss()
            scope.launch { snackbar.showSnackbar(text) }
        }
    }

    val playNext: (String) -> Unit = viewModel::onPlayNext

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(state.puzzle) { if (state.puzzle != null) runCatching { focusRequester.requestFocus() } }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(state.puzzle?.title.orEmpty(), style = MaterialTheme.typography.titleMedium, maxLines = 1)
                        state.puzzle?.let {
                            Text(
                                difficultyLabel(it.difficulty),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) }
                },
                actions = {
                    if (state.settings.showTimer && state.puzzle != null) {
                        Text(
                            formatElapsed(state.elapsedSeconds),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                    }
                    CoinChip(state.coins, Modifier.padding(horizontal = 4.dp))
                    IconButton(onClick = { onOpenClues(viewModel.session.id, viewModel.session.puzzleId) }) {
                        Icon(Icons.AutoMirrored.Rounded.ListAlt, stringResource(R.string.clues_title))
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.more_options)) }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.restart_puzzle)) },
                                leadingIcon = { Icon(Icons.Rounded.Replay, null) },
                                onClick = {
                                    menuOpen = false
                                    confirmRestart = true
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        val puzzle = state.puzzle
        val board = state.board
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .focusRequester(focusRequester)
                .focusable()
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    val char = event.utf16CodePoint.toChar()
                    when {
                        char.isLetter() && char.code < 128 -> viewModel.onLetter(char.uppercaseChar())
                        event.key == Key.Backspace || event.key == Key.Delete -> viewModel.onDelete()
                        event.key == Key.Spacebar -> viewModel.onToggleDirection()
                        event.key == Key.Tab -> if (event.isShiftPressed) viewModel.onPreviousWord() else viewModel.onNextWord()
                        event.key == Key.DirectionLeft -> viewModel.onMove(0, -1)
                        event.key == Key.DirectionRight -> viewModel.onMove(0, 1)
                        event.key == Key.DirectionUp -> viewModel.onMove(-1, 0)
                        event.key == Key.DirectionDown -> viewModel.onMove(1, 0)
                        else -> return@onKeyEvent false
                    }
                    true
                },
        ) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.notFound || puzzle == null || board == null -> Column(
                    Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(stringResource(R.string.puzzle_not_found), style = MaterialTheme.typography.titleMedium)
                    Button(onClick = onBack) { Text(stringResource(R.string.back)) }
                }
                else -> BoxWithConstraints(Modifier.fillMaxSize()) {
                    val wide = maxWidth >= 720.dp && maxWidth > maxHeight
                    val keyHeight = if (maxHeight < 560.dp) 40.dp else 50.dp
                    val gridDescription = stringResource(
                        R.string.grid_description,
                        puzzle.rows,
                        puzzle.cols,
                        (state.progress * 100).toInt(),
                    )
                    val playArea: @Composable (Modifier) -> Unit = { modifier ->
                        Column(modifier.padding(horizontal = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            val animatedProgress by animateFloatAsState(state.progress, label = "progress")
                            LinearProgressIndicator(
                                progress = { animatedProgress },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            )
                            CrosswordGrid(
                                puzzle = puzzle,
                                board = board,
                                activeWord = state.activeWord,
                                lastInput = state.lastInput,
                                solved = state.solved,
                                onCellTap = viewModel::onCellTap,
                                description = gridDescription,
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                            )
                            if (state.solved) {
                                SolvedPanel(
                                    onReplay = { confirmRestart = true },
                                    onNext = state.completion?.nextPuzzleId?.let { id -> { playNext(id) } },
                                    onBack = onBack,
                                )
                            } else {
                                ClueBar(
                                    word = state.activeWord,
                                    onPrevious = viewModel::onPreviousWord,
                                    onNext = viewModel::onNextWord,
                                    onToggleDirection = viewModel::onToggleDirection,
                                    modifier = Modifier.padding(top = 8.dp).widthIn(max = 720.dp),
                                )
                                HintBar(
                                    onHint = viewModel::onHint,
                                    modifier = Modifier.padding(vertical = 8.dp).widthIn(max = 600.dp),
                                )
                                LetterKeyboard(
                                    onLetter = viewModel::onLetter,
                                    onDelete = viewModel::onDelete,
                                    onToggleDirection = viewModel::onToggleDirection,
                                    hapticsEnabled = state.settings.hapticsEnabled,
                                    keyHeight = keyHeight,
                                )
                            }
                        }
                    }
                    if (wide) {
                        Row(Modifier.fillMaxSize()) {
                            playArea(Modifier.weight(1.4f).fillMaxHeight())
                            VerticalDivider()
                            val acrossTitle = stringResource(R.string.across)
                            val downTitle = stringResource(R.string.down)
                            LazyColumn(Modifier.weight(1f).fillMaxHeight(), contentPadding = PaddingValues(bottom = 16.dp)) {
                                val activeKey = state.activeWord?.key
                                clueSection(acrossTitle, puzzle.acrossWords, board, activeKey, viewModel::onSelectWord)
                                clueSection(downTitle, puzzle.downWords, board, activeKey, viewModel::onSelectWord)
                            }
                        }
                    } else {
                        playArea(Modifier.fillMaxSize())
                    }
                }
            }

            val completion = state.completion
            AnimatedVisibility(state.showCompletion && completion != null, enter = fadeIn(), exit = fadeOut()) {
                if (completion != null) {
                    CompletionOverlay(
                        result = completion,
                        onNext = completion.nextPuzzleId?.let { id -> { playNext(id) } },
                        onDone = onBack,
                        onViewBoard = viewModel::dismissCompletion,
                    )
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
                viewModel.unlockLevel(puzzleId)
            },
            onDismiss = { coinPrompt = null },
        )
    }

    if (confirmRestart) {
        AlertDialog(
            onDismissRequest = { confirmRestart = false },
            title = { Text(stringResource(R.string.restart_puzzle)) },
            text = { Text(stringResource(R.string.restart_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRestart = false
                    viewModel.restart()
                }) { Text(stringResource(R.string.restart)) }
            },
            dismissButton = { TextButton(onClick = { confirmRestart = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun SolvedPanel(onReplay: () -> Unit, onNext: (() -> Unit)?, onBack: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().widthIn(max = 600.dp).padding(vertical = 12.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.solved_banner), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onReplay) { Text(stringResource(R.string.restart)) }
                if (onNext != null) Button(onClick = onNext) { Text(stringResource(R.string.completion_next)) }
                else Button(onClick = onBack) { Text(stringResource(R.string.completion_done)) }
            }
        }
    }
}

@Composable
fun difficultyLabel(difficulty: Difficulty): String = stringResource(
    when (difficulty) {
        Difficulty.EASY -> R.string.difficulty_easy
        Difficulty.MEDIUM -> R.string.difficulty_medium
        Difficulty.HARD -> R.string.difficulty_hard
    },
)

