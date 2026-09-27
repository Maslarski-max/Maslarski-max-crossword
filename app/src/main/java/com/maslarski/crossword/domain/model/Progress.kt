package com.maslarski.crossword.domain.model

import java.time.LocalDate

/** Identifies one playthrough: a level has a single board, the daily puzzle gets a fresh board every day. */
sealed interface GameSession {
    val id: String
    val puzzleId: String

    data class Level(override val puzzleId: String) : GameSession {
        override val id: String get() = "level:$puzzleId"
    }

    data class Daily(val date: LocalDate, override val puzzleId: String) : GameSession {
        override val id: String get() = "daily:$date"
    }

    companion object {
        fun from(sessionId: String, puzzleId: String): GameSession {
            val (kind, value) = sessionId.split(":", limit = 2)
            return when (kind) {
                "level" -> Level(puzzleId)
                "daily" -> Daily(LocalDate.parse(value), puzzleId)
                else -> throw IllegalArgumentException("Unknown session $sessionId")
            }
        }
    }
}

data class SavedBoard(
    val sessionId: String,
    val puzzleId: String,
    /**
     * [Puzzle.fingerprint] the board was saved against; blank for boards saved before it was tracked. Such legacy
     * boards are kept while in progress, but a legacy completion only counts if it still solves the current answers.
     */
    val solutionFingerprint: String,
    val board: BoardState,
    val elapsedSeconds: Long,
    val checksUsed: Int,
    val completed: Boolean,
    val score: Int,
    val stars: Int,
    val updatedAt: Long,
) {
    /** True when this board was saved for [puzzle]'s current answers and layout. */
    fun matches(puzzle: Puzzle): Boolean =
        puzzleId == puzzle.id &&
            (solutionFingerprint == puzzle.fingerprint || solutionFingerprint.isEmpty() && (!completed || board.entries == puzzle.solution)) &&
            board.fits(puzzle)
}

data class LevelProgress(
    val puzzleId: String,
    val order: Int,
    val unlocked: Boolean,
    val completed: Boolean,
    val bestScore: Int,
    val bestTimeSeconds: Long?,
    val stars: Int,
)

enum class UnlockResult {
    UNLOCKED,
    ALREADY_UNLOCKED,
    NOT_ENOUGH_COINS,
    /** An earlier level is still locked, or the level is unknown. */
    NOT_NEXT,
}

data class DailyStatus(
    val date: LocalDate,
    val puzzleId: String,
    val completed: Boolean,
    val score: Int,
)

data class PlayerStats(
    val puzzlesSolved: Int,
    val bestScore: Int,
    val dailyStreak: Int,
)

data class CompletionResult(
    val score: Int,
    val stars: Int,
    val elapsedSeconds: Long,
    val coinsEarned: Int,
    val isNewBest: Boolean,
    val nextPuzzleId: String?,
)

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class Settings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val hintEconomyEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val showTimer: Boolean = true,
)
