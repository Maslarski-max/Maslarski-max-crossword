package com.maslarski.crossword.ui.game

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.maslarski.crossword.data.telemetry.Telemetry
import com.maslarski.crossword.di.ApplicationScope
import com.maslarski.crossword.domain.engine.CrosswordEngine
import com.maslarski.crossword.domain.engine.Hint
import com.maslarski.crossword.domain.model.BoardState
import com.maslarski.crossword.domain.model.CompletionResult
import com.maslarski.crossword.domain.model.GameSession
import com.maslarski.crossword.domain.model.Puzzle
import com.maslarski.crossword.domain.model.Settings
import com.maslarski.crossword.domain.model.Word
import com.maslarski.crossword.domain.repository.ProgressRepository
import com.maslarski.crossword.domain.repository.PuzzleRepository
import com.maslarski.crossword.domain.repository.SettingsRepository
import com.maslarski.crossword.domain.repository.WalletRepository
import com.maslarski.crossword.ui.components.InputEvent
import com.maslarski.crossword.ui.navigation.GameRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

data class GameUiState(
    val loading: Boolean = true,
    val notFound: Boolean = false,
    val puzzle: Puzzle? = null,
    val board: BoardState? = null,
    val elapsedSeconds: Long = 0,
    val checksUsed: Int = 0,
    val coins: Int = 0,
    val settings: Settings = Settings(),
    val solved: Boolean = false,
    /** Set when the puzzle was solved in this visit; drives the celebration dialog. */
    val completion: CompletionResult? = null,
    val showCompletion: Boolean = false,
    val lastInput: InputEvent? = null,
) {
    val activeWord: Word?
        get() = if (puzzle != null && board != null) CrosswordEngine.activeWord(puzzle, board) else null

    val progress: Float
        get() = if (puzzle != null && board != null && puzzle.openCellCount > 0) {
            CrosswordEngine.filledCount(board).toFloat() / puzzle.openCellCount
        } else 0f
}

sealed interface GameMessage {
    data class NotEnoughCoins(val cost: Int) : GameMessage
    data class ErrorsFound(val count: Int) : GameMessage
    data object NoErrors : GameMessage
    data object NothingToReveal : GameMessage
    data object NothingToCheck : GameMessage
}

@HiltViewModel
class GameViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val puzzles: PuzzleRepository,
    private val progress: ProgressRepository,
    private val wallet: WalletRepository,
    settingsRepository: SettingsRepository,
    private val telemetry: Telemetry,
    /** Board writes run here so a save queued just before the screen closes is not cancelled with it. */
    @ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<GameRoute>()
    val session: GameSession = GameSession.from(route.sessionId, route.puzzleId)

    private val _state = MutableStateFlow(GameUiState())
    val state: StateFlow<GameUiState> = _state.asStateFlow()

    private val _messages = Channel<GameMessage>(Channel.BUFFERED)
    val messages: Flow<GameMessage> = _messages.receiveAsFlow()

    /** Serialises hint purchases so each one is validated against the board left by the previous one. */
    private val hintLock = Mutex()
    private var timerJob: Job? = null
    private var foreground = false
    private var inputSequence = 0L

    init {
        viewModelScope.launch { wallet.observeCoins().collect { coins -> _state.update { it.copy(coins = coins) } } }
        viewModelScope.launch { settingsRepository.settings.collect { s -> _state.update { it.copy(settings = s) } } }
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val puzzle = puzzles.puzzle(route.puzzleId)
        if (puzzle == null) {
            _state.update { it.copy(loading = false, notFound = true) }
            return
        }
        val saved = progress.loadBoard(session.id)?.takeIf { it.matches(puzzle) }
        _state.update {
            it.copy(
                loading = false,
                puzzle = puzzle,
                board = saved?.board ?: CrosswordEngine.newBoard(puzzle),
                elapsedSeconds = saved?.elapsedSeconds ?: 0,
                checksUsed = saved?.checksUsed ?: 0,
                solved = saved?.completed == true,
            )
        }
        if (saved == null) telemetry.puzzleStarted(puzzle)
        if (foreground) startTimer()
    }

    fun onForeground() {
        foreground = true
        startTimer()
    }

    fun onBackground() {
        foreground = false
        timerJob?.cancel()
        persist()
    }

    private fun startTimer() {
        val s = _state.value
        if (timerJob?.isActive == true || s.puzzle == null || s.solved) return
        timerJob = viewModelScope.launch {
            while (isActive) {
                delay(1_000)
                _state.update { it.copy(elapsedSeconds = it.elapsedSeconds + 1) }
                if (_state.value.elapsedSeconds % 15 == 0L) persist()
            }
        }
    }

    fun onCellTap(index: Int) = mutate { p, b -> CrosswordEngine.select(p, b, index) }
    fun onLetter(letter: Char) = mutate(isInput = true) { p, b -> CrosswordEngine.input(p, b, letter) }
    fun onDelete() = mutate { p, b -> CrosswordEngine.delete(p, b) }
    fun onToggleDirection() = mutate { p, b -> CrosswordEngine.toggleDirection(p, b) }
    fun onMove(dRow: Int, dCol: Int) = mutate { p, b -> CrosswordEngine.move(p, b, dRow, dCol) }
    fun onNextWord() = mutate { p, b -> CrosswordEngine.nextWord(p, b, 1) }
    fun onPreviousWord() = mutate { p, b -> CrosswordEngine.nextWord(p, b, -1) }
    fun onSelectWord(word: Word) = mutate { p, b -> CrosswordEngine.selectWord(p, b, word) }
    fun onSelectWordKey(key: String) = mutate { p, b -> p.wordByKey(key)?.let { CrosswordEngine.selectWord(p, b, it) } ?: b }

    fun onHint(hint: Hint) {
        appScope.launch {
            hintLock.withLock {
                val s = _state.value
                val puzzle = s.puzzle ?: return@launch
                val board = s.board ?: return@launch
                if (s.solved) return@launch
                unavailable(hint, puzzle, board)?.let {
                    _messages.send(it)
                    return@launch
                }
                val charged = s.settings.hintEconomyEnabled
                if (charged && !wallet.trySpend(hint.cost)) {
                    _messages.send(GameMessage.NotEnoughCoins(hint.cost))
                    return@launch
                }
                // The board may have changed while the wallet write was suspended.
                val now = _state.value
                val stale = if (now.solved || now.puzzle == null || now.board == null) {
                    GameMessage.NothingToReveal
                } else {
                    unavailable(hint, now.puzzle, now.board)
                }
                if (stale != null) {
                    if (charged) wallet.refund(hint.cost)
                    _messages.send(stale)
                    return@launch
                }
                telemetry.hintUsed(puzzle, hint)
                if (hint == Hint.CHECK_ERRORS) {
                    _state.update { it.copy(checksUsed = it.checksUsed + 1) }
                    mutate(forcePersist = true) { p, b -> CrosswordEngine.checkErrors(p, b) }
                    val errors = _state.value.board?.incorrect?.size ?: 0
                    _messages.send(if (errors > 0) GameMessage.ErrorsFound(errors) else GameMessage.NoErrors)
                } else {
                    mutate(isInput = true) { p, b -> reveal(hint, p, b) }
                }
            }
        }
    }

    private fun unavailable(hint: Hint, puzzle: Puzzle, board: BoardState): GameMessage? = when (hint) {
        Hint.CHECK_ERRORS -> GameMessage.NothingToCheck.takeIf { CrosswordEngine.filledCount(board) == 0 }
        Hint.REVEAL_LETTER, Hint.REVEAL_WORD -> GameMessage.NothingToReveal.takeIf { reveal(hint, puzzle, board) == board }
    }

    private fun reveal(hint: Hint, puzzle: Puzzle, board: BoardState): BoardState =
        if (hint == Hint.REVEAL_WORD) CrosswordEngine.revealWord(puzzle, board) else CrosswordEngine.revealLetter(puzzle, board)

    fun dismissCompletion() = _state.update { it.copy(showCompletion = false) }

    /** Clears the board to replay a puzzle. Level completion and high scores are kept. */
    fun restart() {
        val puzzle = _state.value.puzzle ?: return
        timerJob?.cancel()
        _state.update {
            it.copy(
                board = CrosswordEngine.newBoard(puzzle),
                elapsedSeconds = 0,
                checksUsed = 0,
                solved = false,
                completion = null,
                showCompletion = false,
                lastInput = null,
            )
        }
        appScope.launch {
            progress.resetBoard(session.id)
            telemetry.puzzleStarted(puzzle)
            if (foreground) startTimer()
        }
    }

    private inline fun mutate(
        isInput: Boolean = false,
        forcePersist: Boolean = false,
        transform: (Puzzle, BoardState) -> BoardState,
    ) {
        val s = _state.value
        val puzzle = s.puzzle ?: return
        val board = s.board ?: return
        if (s.solved) return
        val next = transform(puzzle, board)
        if (next == board && !forcePersist) return
        _state.update {
            it.copy(
                board = next,
                lastInput = if (isInput && next.entries != board.entries) {
                    InputEvent(board.selected, ++inputSequence)
                } else it.lastInput,
            )
        }
        if (CrosswordEngine.isSolved(puzzle, next)) complete(puzzle, next) else persist()
    }

    /** Writes the current board to Room. Called on every board change, so progress survives process death. */
    private fun persist() {
        val s = _state.value
        val board = s.board ?: return
        if (s.solved) return
        val puzzle = s.puzzle ?: return
        appScope.launch {
            progress.saveBoard(session, puzzle, board, s.elapsedSeconds, s.checksUsed)
        }
    }

    private fun complete(puzzle: Puzzle, board: BoardState) {
        timerJob?.cancel()
        _state.update { it.copy(solved = true) }
        val s = _state.value
        appScope.launch {
            val next = nextPuzzleId(puzzle)
            val result = progress.recordCompletion(session, puzzle, board, s.elapsedSeconds, s.checksUsed, next)
            telemetry.puzzleCompleted(puzzle, result)
            _state.update { it.copy(completion = result, showCompletion = true) }
        }
    }

    private suspend fun nextPuzzleId(puzzle: Puzzle): String? {
        if (session !is GameSession.Level) return null
        val levels = puzzles.levels()
        val index = levels.indexOfFirst { it.id == puzzle.id }
        return levels.getOrNull(index + 1)?.id
    }

    override fun onCleared() {
        timerJob?.cancel()
    }
}
