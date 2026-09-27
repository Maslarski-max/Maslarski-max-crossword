package com.maslarski.crossword.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface BoardProgressDao {
    @Query("SELECT * FROM board_progress WHERE sessionId = :sessionId")
    fun observe(sessionId: String): Flow<BoardProgressEntity?>

    @Query("SELECT * FROM board_progress WHERE sessionId = :sessionId")
    suspend fun get(sessionId: String): BoardProgressEntity?

    @Upsert
    suspend fun upsert(entity: BoardProgressEntity)

    @Query("DELETE FROM board_progress WHERE sessionId = :sessionId")
    suspend fun delete(sessionId: String)

    @Query("SELECT * FROM board_progress WHERE completed = 0 AND sessionId LIKE 'level:%' ORDER BY updatedAt DESC LIMIT 1")
    fun observeLastInProgressLevel(): Flow<BoardProgressEntity?>
}

@Dao
interface LevelProgressDao {
    @Query("SELECT * FROM level_progress ORDER BY levelOrder")
    fun observeAll(): Flow<List<LevelProgressEntity>>

    @Query("SELECT * FROM level_progress ORDER BY levelOrder")
    suspend fun getAll(): List<LevelProgressEntity>

    @Query("SELECT * FROM level_progress WHERE puzzleId = :puzzleId")
    suspend fun get(puzzleId: String): LevelProgressEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(entities: List<LevelProgressEntity>)

    @Upsert
    suspend fun upsert(entity: LevelProgressEntity)

    @Query("UPDATE level_progress SET levelOrder = :order WHERE puzzleId = :puzzleId")
    suspend fun updateOrder(puzzleId: String, order: Int)

    @Query("UPDATE level_progress SET unlocked = 1 WHERE puzzleId = :puzzleId")
    suspend fun unlock(puzzleId: String)

    @Query("SELECT COUNT(*) FROM level_progress WHERE completed = 1")
    fun observeCompletedCount(): Flow<Int>
}

@Dao
interface DailyPuzzleDao {
    @Query("SELECT * FROM daily_puzzles WHERE date = :date")
    fun observe(date: String): Flow<DailyPuzzleEntity?>

    @Query("SELECT * FROM daily_puzzles WHERE date = :date")
    suspend fun get(date: String): DailyPuzzleEntity?

    @Upsert
    suspend fun upsert(entity: DailyPuzzleEntity)

    @Query("SELECT date FROM daily_puzzles WHERE completed = 1 ORDER BY date DESC")
    fun observeCompletedDates(): Flow<List<String>>
}

@Dao
interface HighScoreDao {
    @Insert
    suspend fun insert(entity: HighScoreEntity)

    @Query("SELECT MAX(score) FROM high_scores WHERE puzzleId = :puzzleId")
    suspend fun bestFor(puzzleId: String): Int?

    @Query("SELECT COALESCE(MAX(score), 0) FROM high_scores")
    fun observeBest(): Flow<Int>

    @Query("SELECT COUNT(DISTINCT sessionId) FROM high_scores")
    fun observeSolvedCount(): Flow<Int>
}

@Dao
interface WalletDao {
    @Query("SELECT coins FROM wallet WHERE id = 0")
    fun observeCoins(): Flow<Int?>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(entity: WalletEntity)

    @Query("UPDATE wallet SET coins = coins - :amount WHERE id = 0 AND coins >= :amount")
    suspend fun spend(amount: Int): Int

    @Query("UPDATE wallet SET coins = coins + :amount, lifetimeEarned = lifetimeEarned + :amount WHERE id = 0")
    suspend fun earn(amount: Int): Int

    @Query("UPDATE wallet SET coins = coins + :amount WHERE id = 0")
    suspend fun refund(amount: Int): Int
}

@Dao
interface ArenaMatchDao {
    @Insert
    suspend fun insert(entity: ArenaMatchEntity): Long

    @Query("SELECT * FROM arena_matches WHERE id = :id")
    suspend fun get(id: Long): ArenaMatchEntity?

    @Query("SELECT * FROM arena_matches WHERE outcome IS NULL ORDER BY updatedAt DESC LIMIT 1")
    fun observeActive(): Flow<ArenaMatchEntity?>

    @Query("UPDATE arena_matches SET state = :state, playerScore = :playerScore, opponentScore = :opponentScore, updatedAt = :now WHERE id = :id AND outcome IS NULL")
    suspend fun updateState(id: Long, state: String, playerScore: Int, opponentScore: Int, now: Long): Int

    @Query(
        "UPDATE arena_matches SET state = :state, playerScore = :playerScore, opponentScore = :opponentScore, " +
            "outcome = :outcome, updatedAt = :now, finishedAt = :now WHERE id = :id AND outcome IS NULL",
    )
    suspend fun finish(id: Long, state: String, playerScore: Int, opponentScore: Int, outcome: String, now: Long): Int

    @Query("UPDATE arena_matches SET outcome = :outcome, updatedAt = :now, finishedAt = :now WHERE outcome IS NULL")
    suspend fun closeActive(outcome: String, now: Long): Int

    @Query("SELECT difficulty, outcome, playerScore FROM arena_matches WHERE outcome IS NOT NULL ORDER BY finishedAt DESC, id DESC")
    fun observeResults(): Flow<List<ArenaResultRow>>
}
