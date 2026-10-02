package com.maslarski.crossword.ui.daily

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maslarski.crossword.domain.model.GameSession
import com.maslarski.crossword.domain.model.Puzzle
import com.maslarski.crossword.domain.profile.localDates
import com.maslarski.crossword.domain.repository.ProgressRepository
import com.maslarski.crossword.domain.repository.PuzzleRepository
import com.maslarski.crossword.domain.repository.WalletRepository
import com.maslarski.crossword.ui.home.PuzzleCard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

data class DailyUiState(
    val loading: Boolean = true,
    val today: LocalDate? = null,
    val challenge: PuzzleCard? = null,
    val month: YearMonth? = null,
    val completed: Set<LocalDate> = emptySet(),
    val streak: Int = 0,
    val coins: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DailyViewModel @Inject constructor(
    private val puzzles: PuzzleRepository,
    private val progress: ProgressRepository,
    private val wallet: WalletRepository,
    clock: Clock,
) : ViewModel() {

    /** Months back from the current one; 0 is the current month. */
    private val monthOffset = MutableStateFlow(0L)

    val state: StateFlow<DailyUiState> = localDates(clock)
        .flatMapLatest { today -> monthOffset.flatMapLatest { offset -> dailyState(today, offset) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DailyUiState())

    fun showPreviousMonth() = monthOffset.update { (it + 1).coerceAtMost(MAX_MONTHS_BACK) }

    fun showNextMonth() = monthOffset.update { (it - 1).coerceAtLeast(0) }

    private fun dailyState(today: LocalDate, offset: Long): Flow<DailyUiState> = flow {
        val puzzle = puzzles.dailyPuzzle(today)
        val session = puzzle?.let { GameSession.Daily(today, it.id) }
        val month = YearMonth.from(today).minusMonths(offset)
        emitAll(
            combine(
                progress.observeBoard(session?.id ?: NO_SESSION),
                progress.observeDailyCompletions(month.atDay(1), month.atEndOfMonth()),
                progress.observeStats(today),
                wallet.observeCoins(),
            ) { board, completed, stats, coins ->
                val saved = board?.takeIf { puzzle != null && it.matches(puzzle) }
                DailyUiState(
                    loading = false,
                    today = today,
                    challenge = if (puzzle != null && session != null) {
                        PuzzleCard(
                            sessionId = session.id,
                            puzzleId = puzzle.id,
                            title = puzzle.title,
                            difficulty = puzzle.difficulty,
                            progress = saved?.let { progressOf(puzzle, it.board.entries) } ?: 0f,
                            completed = saved?.completed == true,
                            score = saved?.score ?: 0,
                        )
                    } else null,
                    month = month,
                    completed = completed,
                    streak = stats.dailyStreak,
                    coins = coins,
                    canGoBack = offset < MAX_MONTHS_BACK,
                    canGoForward = offset > 0,
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
        const val MAX_MONTHS_BACK = 12L
    }
}
