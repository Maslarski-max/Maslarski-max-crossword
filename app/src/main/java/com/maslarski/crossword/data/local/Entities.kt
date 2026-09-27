package com.maslarski.crossword.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Board snapshot, written on every keystroke. Masks are strings of '0'/'1' aligned with [entries]. */
@Entity(tableName = "board_progress", indices = [Index("updatedAt")])
data class BoardProgressEntity(
    @PrimaryKey val sessionId: String,
    val puzzleId: String,
    @ColumnInfo(defaultValue = "") val solutionFingerprint: String,
    val entries: String,
    val revealedMask: String,
    val incorrectMask: String,
    val selectedIndex: Int,
    val direction: String,
    val elapsedSeconds: Long,
    val checksUsed: Int,
    val completed: Boolean,
    val score: Int,
    val stars: Int,
    val updatedAt: Long,
)

@Entity(tableName = "level_progress")
data class LevelProgressEntity(
    @PrimaryKey val puzzleId: String,
    val levelOrder: Int,
    val unlocked: Boolean,
    val completed: Boolean,
    val bestScore: Int,
    val bestTimeSeconds: Long?,
    val stars: Int,
    val completedAt: Long?,
)

@Entity(tableName = "daily_puzzles")
data class DailyPuzzleEntity(
    /** ISO date, e.g. 2026-09-27. */
    @PrimaryKey val date: String,
    val puzzleId: String,
    val completed: Boolean,
    val score: Int,
    val completedAt: Long?,
)

@Entity(tableName = "high_scores", indices = [Index("puzzleId"), Index("score")])
data class HighScoreEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val puzzleId: String,
    val sessionId: String,
    val score: Int,
    val elapsedSeconds: Long,
    val stars: Int,
    val achievedAt: Long,
)

@Entity(tableName = "wallet")
data class WalletEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val coins: Int,
    val lifetimeEarned: Int,
) {
    companion object {
        const val SINGLETON_ID = 0
    }
}

/** One arena match. [state] is the JSON-encoded ArenaState; [outcome] stays null while the match is in progress. */
@Entity(tableName = "arena_matches", indices = [Index("outcome"), Index("finishedAt")])
data class ArenaMatchEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val puzzleId: String,
    val difficulty: String,
    val state: String,
    val playerScore: Int,
    val opponentScore: Int,
    val outcome: String?,
    val startedAt: Long,
    val updatedAt: Long,
    val finishedAt: Long?,
)

data class ArenaResultRow(val difficulty: String, val outcome: String, val playerScore: Int)
