package com.maslarski.crossword.data.repository

import androidx.room.withTransaction
import com.maslarski.crossword.data.local.BoardProgressEntity
import com.maslarski.crossword.data.local.CrosswordDatabase
import com.maslarski.crossword.data.local.DailyPuzzleEntity
import com.maslarski.crossword.data.local.HighScoreEntity
import com.maslarski.crossword.data.local.LevelProgressEntity
import com.maslarski.crossword.data.local.toDomain
import com.maslarski.crossword.data.local.toMask
import com.maslarski.crossword.data.local.toSavedBoard
import com.maslarski.crossword.domain.engine.GameRules
import com.maslarski.crossword.domain.engine.LevelUnlocks
import com.maslarski.crossword.domain.engine.Streaks
import com.maslarski.crossword.domain.model.BoardState
import com.maslarski.crossword.domain.model.CompletionResult
import com.maslarski.crossword.domain.model.DailyStatus
import com.maslarski.crossword.domain.model.GameSession
import com.maslarski.crossword.domain.model.LevelProgress
import com.maslarski.crossword.domain.model.PlayerStats
import com.maslarski.crossword.domain.model.Puzzle
import com.maslarski.crossword.domain.model.SavedBoard
import com.maslarski.crossword.domain.model.UnlockResult
import com.maslarski.crossword.domain.repository.ProgressRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.LocalDate

class RoomProgressRepository(
    private val db: CrosswordDatabase,
    private val clock: Clock,
) : ProgressRepository {

    private val boards = db.boardProgressDao()
    private val levels = db.levelProgressDao()
    private val daily = db.dailyPuzzleDao()
    private val highScores = db.highScoreDao()
    private val wallet = db.walletDao()

    /**
     * Serialises board reads and writes across all game screens (FIFO), so a snapshot queued by a closing
     * screen always lands before a reopened screen loads or saves the same session.
     */
    private val boardLock = Mutex()

    override fun observeBoard(sessionId: String): Flow<SavedBoard?> =
        boards.observe(sessionId).map { it?.toSavedBoard() }

    override suspend fun loadBoard(sessionId: String): SavedBoard? = boardLock.withLock { boards.get(sessionId)?.toSavedBoard() }

    override suspend fun saveBoard(session: GameSession, puzzle: Puzzle, board: BoardState, elapsedSeconds: Long, checksUsed: Int) = boardLock.withLock {
        val existing = boards.get(session.id)?.toSavedBoard()
        if (existing?.completed == true && existing.matches(puzzle)) return@withLock
        boards.upsert(board.toEntity(session, puzzle, elapsedSeconds, checksUsed, completed = false, score = 0, stars = 0))
    }

    override suspend fun resetBoard(sessionId: String) = boardLock.withLock { boards.delete(sessionId) }

    override fun observeLastInProgress(): Flow<SavedBoard?> =
        boards.observeLastInProgressLevel().map { it?.toSavedBoard() }

    override fun observeLevels(): Flow<List<LevelProgress>> =
        levels.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun syncLevels(levels: List<Puzzle>): Unit = db.withTransaction {
        this.levels.insertIgnore(
            levels.map {
                LevelProgressEntity(it.id, it.order, unlocked = false, completed = false, bestScore = 0, bestTimeSeconds = null, stars = 0, completedAt = null)
            },
        )
        levels.forEach { this.levels.updateOrder(it.id, it.order) }
        levels.firstOrNull()?.let { this.levels.unlock(it.id) }
        Unit
    }

    override suspend fun unlockLevel(levels: List<Puzzle>, puzzleId: String): UnlockResult = db.withTransaction {
        val records = this.levels.getAll().associateBy { it.puzzleId }
        val record = records[puzzleId] ?: return@withTransaction UnlockResult.NOT_NEXT
        if (record.unlocked) return@withTransaction UnlockResult.ALREADY_UNLOCKED
        val unlocked = records.values.filter { it.unlocked }.mapTo(HashSet()) { it.puzzleId }
        if (LevelUnlocks.nextUnlockable(levels.map { it.id }, unlocked) != puzzleId) return@withTransaction UnlockResult.NOT_NEXT
        if (wallet.spend(GameRules.LEVEL_UNLOCK_COST) == 0) return@withTransaction UnlockResult.NOT_ENOUGH_COINS
        this.levels.unlock(puzzleId)
        UnlockResult.UNLOCKED
    }

    override fun observeDaily(date: LocalDate): Flow<DailyStatus?> =
        daily.observe(date.toString()).map { e ->
            e?.let { DailyStatus(LocalDate.parse(it.date), it.puzzleId, it.completed, it.score) }
        }

    override fun observeDailyCompletions(from: LocalDate, to: LocalDate): Flow<Set<LocalDate>> =
        daily.observeCompletedBetween(from.toString(), to.toString()).map { dates -> dates.mapTo(HashSet(), LocalDate::parse) }

    override fun observeStats(today: LocalDate): Flow<PlayerStats> = combine(
        highScores.observeSolvedCount(),
        highScores.observeBest(),
        daily.observeCompletedDates(),
    ) { solved, best, dates ->
        PlayerStats(solved, best, Streaks.current(dates.map(LocalDate::parse), today))
    }

    override suspend fun recordCompletion(
        session: GameSession,
        puzzle: Puzzle,
        board: BoardState,
        elapsedSeconds: Long,
        checksUsed: Int,
        nextPuzzleId: String?,
    ): CompletionResult = boardLock.withLock { recordCompletionLocked(session, puzzle, board, elapsedSeconds, checksUsed, nextPuzzleId) }

    private suspend fun recordCompletionLocked(
        session: GameSession,
        puzzle: Puzzle,
        board: BoardState,
        elapsedSeconds: Long,
        checksUsed: Int,
        nextPuzzleId: String?,
    ): CompletionResult = db.withTransaction {
        val now = clock.millis()
        val score = GameRules.score(puzzle, elapsedSeconds, board.revealed.size, checksUsed)
        val stars = GameRules.stars(puzzle, elapsedSeconds, board.revealed.size)
        val previousBest = highScores.bestFor(puzzle.id) ?: 0

        val alreadyRewarded = when (session) {
            is GameSession.Level -> levels.get(puzzle.id)?.completed == true
            is GameSession.Daily -> daily.get(session.date.toString())?.completed == true
        }

        boards.upsert(board.toEntity(session, puzzle, elapsedSeconds, checksUsed, completed = true, score = score, stars = stars))
        highScores.insert(HighScoreEntity(puzzleId = puzzle.id, sessionId = session.id, score = score, elapsedSeconds = elapsedSeconds, stars = stars, achievedAt = now))

        when (session) {
            is GameSession.Level -> {
                val current = levels.get(puzzle.id)
                levels.upsert(
                    LevelProgressEntity(
                        puzzleId = puzzle.id,
                        levelOrder = puzzle.order,
                        unlocked = true,
                        completed = true,
                        bestScore = maxOf(score, current?.bestScore ?: 0),
                        bestTimeSeconds = minOf(elapsedSeconds, current?.bestTimeSeconds ?: Long.MAX_VALUE),
                        stars = maxOf(stars, current?.stars ?: 0),
                        completedAt = current?.completedAt ?: now,
                    ),
                )
            }
            is GameSession.Daily -> daily.upsert(
                DailyPuzzleEntity(session.date.toString(), puzzle.id, completed = true, score = score, completedAt = now),
            )
        }

        val coins = if (alreadyRewarded) 0 else GameRules.completionCoins(puzzle)
        if (coins > 0) wallet.earn(coins)

        CompletionResult(
            score = score,
            stars = stars,
            elapsedSeconds = elapsedSeconds,
            coinsEarned = coins,
            isNewBest = score > previousBest,
            nextPuzzleId = nextPuzzleId,
        )
    }

    private fun BoardState.toEntity(
        session: GameSession,
        puzzle: Puzzle,
        elapsedSeconds: Long,
        checksUsed: Int,
        completed: Boolean,
        score: Int,
        stars: Int,
    ) = BoardProgressEntity(
        sessionId = session.id,
        puzzleId = session.puzzleId,
        solutionFingerprint = puzzle.fingerprint,
        entries = entries,
        revealedMask = revealed.toMask(entries.length),
        incorrectMask = incorrect.toMask(entries.length),
        selectedIndex = selected,
        direction = direction.name,
        elapsedSeconds = elapsedSeconds,
        checksUsed = checksUsed,
        completed = completed,
        score = score,
        stars = stars,
        updatedAt = clock.millis(),
    )
}
