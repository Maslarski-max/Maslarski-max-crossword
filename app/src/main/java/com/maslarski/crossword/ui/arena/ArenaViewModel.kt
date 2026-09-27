package com.maslarski.crossword.ui.arena

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.maslarski.crossword.data.telemetry.Telemetry
import com.maslarski.crossword.di.ApplicationScope
import com.maslarski.crossword.domain.arena.ArenaAi
import com.maslarski.crossword.domain.arena.ArenaLayout
import com.maslarski.crossword.domain.arena.ArenaMove
import com.maslarski.crossword.domain.arena.ArenaResult
import com.maslarski.crossword.domain.arena.ArenaRules
import com.maslarski.crossword.domain.arena.ArenaState
import com.maslarski.crossword.domain.arena.ArenaWord
import com.maslarski.crossword.domain.arena.MoveKind
import com.maslarski.crossword.domain.arena.PlacementError
import com.maslarski.crossword.domain.arena.Side
import com.maslarski.crossword.domain.arena.StartArenaMatch
import com.maslarski.crossword.domain.model.Direction
import com.maslarski.crossword.domain.repository.ArenaRepository
import com.maslarski.crossword.domain.repository.PuzzleRepository
import com.maslarski.crossword.domain.repository.WalletRepository
import com.maslarski.crossword.ui.navigation.ArenaRoute
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
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ArenaUiState(
    val loading: Boolean = true,
    val notFound: Boolean = false,
    val layout: ArenaLayout? = null,
    val match: ArenaState? = null,
    /** Tiles placed this turn: board cell to rack slot. */
    val pending: Map<Int, Int> = emptyMap(),
    val selectedSlot: Int? = null,
    val activeWord: ArenaWord? = null,
    val hintCells: Set<Int> = emptySet(),
    val missCells: Set<Int> = emptySet(),
    val opponentThinking: Boolean = false,
    /** Opponent tiles being laid down one by one before its move resolves. */
    val opponentTiles: Map<Int, Char> = emptyMap(),
    val flash: ScoreFlash? = null,
    val coins: Int = 0,
    val result: ArenaResult? = null,
    val showResult: Boolean = false,
) {
    val playerTurn: Boolean get() = match != null && !match.finished && match.turn == Side.PLAYER && !opponentThinking

    val pendingLetters: Map<Int, Char>
        get() = match?.let { m -> pending.mapValues { (_, slot) -> m.playerRack[slot] } }.orEmpty()
}

sealed interface ArenaMessage {
    data class Invalid(val error: PlacementError) : ArenaMessage
    data class Missed(val count: Int) : ArenaMessage
    data class Scored(val points: Int) : ArenaMessage
    data class OpponentScored(val points: Int) : ArenaMessage
    data object OpponentMissed : ArenaMessage
    data object OpponentPassed : ArenaMessage
    data object NoHintMoves : ArenaMessage
    data object NoHintsLeft : ArenaMessage
}

@HiltViewModel
class ArenaViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val puzzles: PuzzleRepository,
    private val arena: ArenaRepository,
    private val startMatch: StartArenaMatch,
    wallet: WalletRepository,
    private val telemetry: Telemetry,
    /** Match writes run here so the last move is saved even if the screen closes right after it. */
    @ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {

    private val matchId = savedStateHandle.toRoute<ArenaRoute>().matchId

    private val _state = MutableStateFlow(ArenaUiState())
    val state: StateFlow<ArenaUiState> = _state.asStateFlow()

    private val _messages = Channel<ArenaMessage>(Channel.BUFFERED)
    val messages: Flow<ArenaMessage> = _messages.receiveAsFlow()

    private val _rematches = Channel<Long>(Channel.BUFFERED)
    /** Ids of newly started matches to navigate to. */
    val rematches: Flow<Long> = _rematches.receiveAsFlow()

    private var opponentJob: Job? = null
    private var missJob: Job? = null
    private var flashSequence = 0

    init {
        viewModelScope.launch { wallet.observeCoins().collect { coins -> _state.update { it.copy(coins = coins) } } }
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val match = arena.loadMatch(matchId)
        val puzzle = match?.let { puzzles.puzzle(it.state.puzzleId) }
        if (match == null || puzzle == null || puzzle.fingerprint != match.state.fingerprint) {
            _state.update { it.copy(loading = false, notFound = true) }
            return
        }
        val layout = ArenaLayout(puzzle)
        val s = match.state
        _state.update {
            it.copy(
                loading = false,
                layout = layout,
                match = s,
                activeWord = layout.words.firstOrNull { w -> w.cells.any(s::isEmpty) },
                result = s.outcome?.let { outcome -> ArenaResult(outcome, s.playerScore, s.opponentScore, 0) },
            )
        }
        when {
            s.finished -> finish(s)
            s.turn == Side.OPPONENT -> runOpponent(animate = false)
        }
    }

    fun onSlotTap(slot: Int) {
        val s = _state.value
        if (!s.playerTurn || slot in s.pending.values) return
        _state.update { it.copy(selectedSlot = if (it.selectedSlot == slot) null else slot) }
    }

    fun onCellTap(cell: Int) {
        val s = _state.value
        val layout = s.layout ?: return
        val match = s.match ?: return
        val clue = layout.clueAt(cell)
        if (clue != null) {
            val word = if (clue.across != null && clue.down != null && s.activeWord == clue.across) clue.down else clue.across ?: clue.down
            _state.update { it.copy(activeWord = word) }
            return
        }
        if (!layout.isLetter(cell)) return
        when {
            cell in s.pending -> _state.update { it.copy(pending = it.pending - cell) }
            s.playerTurn && s.selectedSlot != null && match.isEmpty(cell) -> place(s.selectedSlot, cell)
            else -> {
                val words = layout.wordsAt(cell)
                val word = if (s.activeWord in words && words.size > 1) words.first { it != s.activeWord } else words.firstOrNull()
                _state.update { it.copy(activeWord = word) }
            }
        }
    }

    /** A rack tile was dropped at [cell] (null when released outside the grid). */
    fun onDrop(slot: Int, cell: Int?) {
        val s = _state.value
        val layout = s.layout ?: return
        val match = s.match ?: return
        if (!s.playerTurn || cell == null || !layout.isLetter(cell) || !match.isEmpty(cell)) return
        place(slot, cell)
    }

    private fun place(slot: Int, cell: Int) {
        _state.update { s ->
            val layout = s.layout ?: return@update s
            val pending = s.pending.filterValues { it != slot } + (cell to slot)
            val word = ArenaRules.wordFor(layout, pending.keys)
                ?: s.activeWord?.takeIf { cell in it.cells }
                ?: layout.wordAt(cell, s.activeWord?.direction ?: Direction.ACROSS)
                ?: layout.wordsAt(cell).firstOrNull()
            s.copy(pending = pending, selectedSlot = null, activeWord = word)
        }
    }

    fun onRecall() {
        _state.update { it.copy(pending = emptyMap(), selectedSlot = null) }
    }

    fun onShuffle() {
        val s = _state.value
        val match = s.match ?: return
        if (!s.playerTurn) return
        val shuffled = match.copy(playerRack = match.playerRack.toList().shuffled().joinToString(""))
        _state.update { it.copy(match = shuffled, pending = emptyMap(), selectedSlot = null) }
        persist(shuffled)
    }

    fun onSubmit() {
        val s = _state.value
        val layout = s.layout ?: return
        val match = s.match ?: return
        if (!s.playerTurn) return
        ArenaRules.check(layout, match, Side.PLAYER, s.pending)?.let { error ->
            _messages.trySend(ArenaMessage.Invalid(error))
            return
        }
        val next = ArenaRules.play(layout, match, Side.PLAYER, s.pending)
        val move = next.lastMove ?: return
        applyMove(next, move)
        _messages.trySend(if (move.kind == MoveKind.PLAY) ArenaMessage.Scored(move.total) else ArenaMessage.Missed(move.cells.size))
        afterTurn(next)
    }

    fun onPass() {
        val s = _state.value
        val match = s.match ?: return
        if (!s.playerTurn) return
        val next = ArenaRules.pass(match, Side.PLAYER)
        applyMove(next, next.lastMove ?: return)
        afterTurn(next)
    }

    fun onHint() {
        val s = _state.value
        val layout = s.layout ?: return
        val match = s.match ?: return
        if (!s.playerTurn) return
        if (match.hintsLeft <= 0) {
            _messages.trySend(ArenaMessage.NoHintsLeft)
            return
        }
        val cells = ArenaRules.hintCells(layout, match)
        if (cells.isEmpty()) {
            _messages.trySend(ArenaMessage.NoHintMoves)
            return
        }
        val next = ArenaRules.useHint(match)
        _state.update { it.copy(match = next, hintCells = cells, activeWord = ArenaRules.wordFor(layout, cells) ?: it.activeWord) }
        persist(next)
    }

    fun dismissResult() {
        _state.update { it.copy(showResult = false) }
    }

    fun rematch() {
        val match = _state.value.match ?: return
        viewModelScope.launch {
            val started = startMatch(match.difficulty, avoidPuzzleId = match.puzzleId) ?: return@launch
            telemetry.arenaMatchStarted(started.state)
            _rematches.send(started.id)
        }
    }

    private fun applyMove(next: ArenaState, move: ArenaMove) {
        val flash = if (move.kind == MoveKind.PLAY) ScoreFlash(move.side, move.cells, move.points, ++flashSequence) else null
        val miss = if (move.kind == MoveKind.MISS) move.cells.toSet() else emptySet()
        _state.update {
            it.copy(
                match = next,
                pending = if (move.side == Side.PLAYER) emptyMap() else it.pending,
                selectedSlot = null,
                hintCells = if (move.side == Side.PLAYER) emptySet() else it.hintCells.filterTo(HashSet(), next::isEmpty),
                flash = flash ?: it.flash,
                missCells = miss,
                opponentTiles = emptyMap(),
            )
        }
        missJob?.cancel()
        if (miss.isNotEmpty()) {
            missJob = viewModelScope.launch {
                delay(MISS_FLASH_MS)
                _state.update { it.copy(missCells = emptySet()) }
            }
        }
    }

    private fun afterTurn(next: ArenaState) {
        persist(next)
        when {
            next.finished -> finish(next)
            next.turn == Side.OPPONENT -> runOpponent()
        }
    }

    /**
     * Plays the opponent's turn. A turn found on a reopened match is resolved without the thinking and tile
     * animations, so closing the screen mid-turn can't postpone it again.
     */
    private fun runOpponent(animate: Boolean = true) {
        if (opponentJob?.isActive == true) return
        opponentJob = viewModelScope.launch {
            val layout = _state.value.layout ?: return@launch
            val match = _state.value.match ?: return@launch
            val placements = ArenaAi.decide(layout, match)
            if (animate) {
                _state.update { it.copy(opponentThinking = true) }
                delay(THINK_MS)
                placements.entries.sortedBy { it.key }.forEach { (cell, slot) ->
                    _state.update { it.copy(opponentTiles = it.opponentTiles + (cell to match.opponentRack[slot])) }
                    delay(TILE_MS)
                }
            }
            val next = if (placements.isEmpty()) ArenaRules.pass(match, Side.OPPONENT) else ArenaRules.play(layout, match, Side.OPPONENT, placements)
            val move = next.lastMove ?: return@launch
            applyMove(next, move)
            _state.update { it.copy(opponentThinking = false) }
            _messages.send(
                when (move.kind) {
                    MoveKind.PLAY -> ArenaMessage.OpponentScored(move.total)
                    MoveKind.MISS -> ArenaMessage.OpponentMissed
                    MoveKind.PASS -> ArenaMessage.OpponentPassed
                },
            )
            afterTurn(next)
        }
    }

    private fun persist(next: ArenaState) {
        appScope.launch { arena.saveMatch(matchId, next) }
    }

    private fun finish(next: ArenaState) {
        appScope.launch {
            val recorded = arena.finishMatch(matchId, next)
            if (recorded != null) telemetry.arenaMatchFinished(next, recorded)
            val outcome = next.outcome ?: return@launch
            val result = recorded ?: _state.value.result ?: ArenaResult(outcome, next.playerScore, next.opponentScore, 0)
            _state.update { it.copy(result = result, showResult = true) }
        }
    }

    private companion object {
        const val THINK_MS = 1_200L
        const val TILE_MS = 350L
        const val MISS_FLASH_MS = 1_200L
    }
}
