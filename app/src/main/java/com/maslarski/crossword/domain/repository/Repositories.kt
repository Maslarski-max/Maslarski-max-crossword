package com.maslarski.crossword.domain.repository

import com.maslarski.crossword.domain.arena.ArenaMatch
import com.maslarski.crossword.domain.arena.ArenaResult
import com.maslarski.crossword.domain.arena.ArenaState
import com.maslarski.crossword.domain.arena.ArenaStats
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
import com.maslarski.crossword.domain.model.UnlockResult
import com.maslarski.crossword.domain.profile.Achievement
import com.maslarski.crossword.domain.profile.LoginReward
import com.maslarski.crossword.domain.profile.PlayerProfile
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
    /** Registers newly shipped levels; the first level is always unlocked, later ones are bought. */
    suspend fun syncLevels(levels: List<Puzzle>)
    /**
     * Spends [com.maslarski.crossword.domain.engine.GameRules.LEVEL_UNLOCK_COST] coins and unlocks [puzzleId] in one transaction. Only the first locked
     * level of the shipped [levels] can be bought.
     */
    suspend fun unlockLevel(levels: List<Puzzle>, puzzleId: String): UnlockResult

    fun observeDaily(date: LocalDate): Flow<DailyStatus?>
    /** Days in [from]..[to] whose Daily Challenge was solved. */
    fun observeDailyCompletions(from: LocalDate, to: LocalDate): Flow<Set<LocalDate>>
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

/** Arena matches and career stats; separate from the classic [ProgressRepository] records. */
interface ArenaRepository {
    fun observeStats(): Flow<ArenaStats>
    /** The unfinished match, if any. At most one exists at a time. */
    fun observeActiveMatch(): Flow<ArenaMatch?>
    /** Stores a new match and returns its id; an unfinished previous match is recorded as forfeited. */
    suspend fun startMatch(state: ArenaState): Long
    suspend fun loadMatch(id: Long): ArenaMatch?
    suspend fun saveMatch(id: Long, state: ArenaState)

    /** Drops an unfinished match that can no longer be played, e.g. because its puzzle changed. */
    suspend fun discardMatch(id: Long)
    /** Records the result of a finished [state] and awards coins; returns null if it was already recorded. */
    suspend fun finishMatch(id: Long, state: ArenaState): ArenaResult?
}

/** My Stats, achievements and the daily login bonus. */
interface ProfileRepository {
    fun observeProfile(today: LocalDate): Flow<PlayerProfile>
    /** Unlocked achievements with their unlock time in epoch millis. */
    fun observeUnlocked(): Flow<Map<Achievement, Long>>
    /** Stores every achievement [profile] meets; returns the ones this call unlocked. */
    suspend fun unlockMet(profile: PlayerProfile): List<Achievement>
    /** Advances the login streak and pays its bonus in one transaction; null if today was already claimed. */
    suspend fun claimDailyLogin(today: LocalDate): LoginReward?
}

interface WalletRepository {
    fun observeCoins(): Flow<Int>
    /** Deducts [amount] only if the balance covers it. */
    suspend fun trySpend(amount: Int): Boolean
    suspend fun earn(amount: Int)
    /** Returns coins from a cancelled [trySpend] without counting them as earnings. */
    suspend fun refund(amount: Int)
}

interface SettingsRepository {
    val settings: Flow<Settings>
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setDynamicColor(enabled: Boolean)
    suspend fun setHaptics(enabled: Boolean)
    suspend fun setShowTimer(enabled: Boolean)
}
