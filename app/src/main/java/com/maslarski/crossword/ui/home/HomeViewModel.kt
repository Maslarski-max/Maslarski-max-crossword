package com.maslarski.crossword.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maslarski.crossword.domain.model.Difficulty
import com.maslarski.crossword.domain.model.GameSession
import com.maslarski.crossword.domain.model.PlayerStats
import com.maslarski.crossword.domain.model.Puzzle
import com.maslarski.crossword.domain.repository.ProgressRepository
import com.maslarski.crossword.domain.repository.PuzzleRepository
import com.maslarski.crossword.domain.repository.EntitlementRepository
import com.maslarski.crossword.domain.repository.WalletRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

data class PuzzleCard(
    val sessionId: String,
    val puzzleId: String,
    val title: String,
    val difficulty: Difficulty,
    val progress: Float,
    val completed: Boolean,
    val score: Int = 0,
)

data class HomeUiState(
    val loading: Boolean = true,
    val date: LocalDate? = null,
    val daily: PuzzleCard? = null,
    val continuePlaying: PuzzleCard? = null,
    val nextLevel: PuzzleCard? = null,
    val levelsSolved: Int = 0,
    val levelsTotal: Int = 0,
    val stats: PlayerStats = PlayerStats(0, 0, 0),
    val coins: Int = 0,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    puzzles: PuzzleRepository,
    progress: ProgressRepository,
    wallet: WalletRepository,
    private val entitlements: EntitlementRepository,
    clock: Clock,
) : ViewModel() {

    /** Current local date; re-checked at least every minute so the daily puzzle rolls over at midnight. */
    private val dates: Flow<LocalDate> = flow {
        while (true) {
            val now = LocalDateTime.now(clock)
            emit(now.toLocalDate())
            val untilMidnight = Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay()).toMillis()
            delay(untilMidnight.coerceIn(1, DATE_POLL_MS))
        }
    }.distinctUntilChanged()

    val state: StateFlow<HomeUiState> = dates.flatMapLatest { today -> homeState(puzzles, progress, wallet, today) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    private fun homeState(
        puzzles: PuzzleRepository,
        progress: ProgressRepository,
        wallet: WalletRepository,
        today: LocalDate,
    ): Flow<HomeUiState> = flow {
        val levels = puzzles.levels()
        progress.syncLevels(levels)
        val daily = puzzles.dailyPuzzle(today)
        val dailySession = daily?.let { GameSession.Daily(today, it.id) }
        val byId = (levels + listOfNotNull(daily)).associateBy { it.id }

        val boards = combine(
            progress.observeBoard(dailySession?.id ?: NO_SESSION),
            progress.observeLastInProgress(),
            ::Pair,
        )
        emitAll(
            combine(boards, progress.observeLevels(), progress.observeStats(today), wallet.observeCoins(), entitlements.observeUnlimited()) { (dailyBoard, last), levelProgress, stats, coins, unlimited ->
                val dailyBoard = dailyBoard?.takeIf { daily != null && it.matches(daily) }
                val dailyCard = if (daily != null && dailySession != null) {
                    PuzzleCard(
                        sessionId = dailySession.id,
                        puzzleId = daily.id,
                        title = daily.title,
                        difficulty = daily.difficulty,
                        progress = dailyBoard?.let { progressOf(daily, it.board.entries) } ?: 0f,
                        completed = dailyBoard?.completed == true,
                        score = dailyBoard?.score ?: 0,
                    )
                } else null
                val continueCard = last
                    ?.takeIf { it.sessionId != dailySession?.id }
                    ?.let { saved ->
                        val puzzle = byId[saved.puzzleId]?.takeIf { saved.matches(it) } ?: return@let null
                        PuzzleCard(saved.sessionId, puzzle.id, puzzle.title, puzzle.difficulty, progressOf(puzzle, saved.board.entries), false)
                    }
                val next = levelProgress
                    .sortedBy { it.order }
                    .firstOrNull { (it.unlocked || unlimited) && !it.completed }
                    ?.let { byId[it.puzzleId] }
                    ?.takeIf { it.id != continueCard?.puzzleId }
                    ?.let { PuzzleCard(GameSession.Level(it.id).id, it.id, it.title, it.difficulty, 0f, false) }
                HomeUiState(
                    loading = false,
                    date = today,
                    daily = dailyCard,
                    continuePlaying = continueCard,
                    nextLevel = next,
                    levelsSolved = levelProgress.count { it.completed },
                    levelsTotal = levels.size,
                    stats = stats,
                    coins = coins,
                )
            },
        )
    }

    private fun progressOf(puzzle: Puzzle, entries: String): Float {
        if (entries.length != puzzle.cellCount || puzzle.openCellCount == 0) return 0f
        return entries.count { it != Puzzle.EMPTY && it != Puzzle.BLOCK }.toFloat() / puzzle.openCellCount
    }

    private companion object {
        const val NO_SESSION = "none"
        const val DATE_POLL_MS = 60_000L
    }
}

