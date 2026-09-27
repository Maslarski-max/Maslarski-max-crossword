package com.maslarski.crossword.domain.repository

import com.maslarski.crossword.domain.model.BoardState
import com.maslarski.crossword.domain.model.CompletionResult
import com.maslarski.crossword.domain.model.DailyStatus
import com.maslarski.crossword.domain.model.GameSession
import com.maslarski.crossword.domain.model.LevelProgress
import com.maslarski.crossword.domain.model.PlayerStats
import com.maslarski.crossword.domain.model.Puzzle
import com.maslarski.crossword.domain.model.SavedBoard
import com.maslarski.crossword.domain.model.Settings
import com.maslarski.crossword.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface PuzzleRepository {
    suspend fun levels(): List<Puzzle>
    suspend fun puzzle(id: String): Puzzle?
    suspend fun dailyPuzzle(date: LocalDate): Puzzle?
}

interface ProgressRepository {
    fun observeBoard(sessionId: String): Flow<SavedBoard?>
    suspend fun loadBoard(sessionId: String): SavedBoard?
    suspend fun saveBoard(session: GameSession, puzzle: Puzzle, board: BoardState, elapsedSeconds: Long, checksUsed: Int)
    suspend fun resetBoard(sessionId: String)
    fun observeLastInProgress(): Flow<SavedBoard?>

    fun observeLevels(): Flow<List<LevelProgress>>
    /** Registers newly shipped levels and recomputes which ones are unlocked. */
    suspend fun syncLevels(levels: List<Puzzle>)

    fun observeDaily(date: LocalDate): Flow<DailyStatus?>
    fun observeStats(today: LocalDate): Flow<PlayerStats>

    /** Atomically marks the board solved, updates level/daily records, high scores and awards coins. */
    suspend fun recordCompletion(
        session: GameSession,
        puzzle: Puzzle,
        board: BoardState,
        elapsedSeconds: Long,
        checksUsed: Int,
        nextPuzzleId: String?,
    ): CompletionResult
}

interface WalletRepository {
    fun observeCoins(): Flow<Int>
    /** Deducts [amount] only if the balance covers it. */
    suspend fun trySpend(amount: Int): Boolean
    suspend fun earn(amount: Int)
}

interface SettingsRepository {
    val settings: Flow<Settings>
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setDynamicColor(enabled: Boolean)
    suspend fun setHintEconomy(enabled: Boolean)
    suspend fun setHaptics(enabled: Boolean)
    suspend fun setShowTimer(enabled: Boolean)
}
