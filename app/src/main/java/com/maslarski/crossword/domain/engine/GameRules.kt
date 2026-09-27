package com.maslarski.crossword.domain.engine

import com.maslarski.crossword.domain.model.Difficulty
import com.maslarski.crossword.domain.model.Puzzle
import com.maslarski.crossword.domain.model.PuzzleType
import kotlin.math.max
import kotlin.math.roundToInt

enum class Hint(val cost: Int) {
    REVEAL_LETTER(5),
    REVEAL_WORD(15),
    CHECK_ERRORS(3),
}

object GameRules {
    const val STARTING_COINS = 100
    const val CHECK_PENALTY = 20

    fun baseScore(difficulty: Difficulty): Int = when (difficulty) {
        Difficulty.EASY -> 300
        Difficulty.MEDIUM -> 600
        Difficulty.HARD -> 1000
    }

    fun parSeconds(difficulty: Difficulty): Long = when (difficulty) {
        Difficulty.EASY -> 240
        Difficulty.MEDIUM -> 480
        Difficulty.HARD -> 900
    }

    fun completionCoins(puzzle: Puzzle): Int = when {
        puzzle.type == PuzzleType.DAILY -> 30
        puzzle.difficulty == Difficulty.EASY -> 20
        puzzle.difficulty == Difficulty.MEDIUM -> 35
        else -> 50
    }

    /**
     * Base points plus up to 50% time bonus (finishing under par), minus a per-cell penalty for revealed
     * letters (revealing the whole grid cancels the base) and a flat penalty per error check.
     */
    fun score(puzzle: Puzzle, elapsedSeconds: Long, revealedCells: Int, checksUsed: Int): Int {
        val base = baseScore(puzzle.difficulty)
        val par = parSeconds(puzzle.difficulty)
        val timeBonus = if (elapsedSeconds < par) (base * 0.5 * (par - elapsedSeconds) / par).roundToInt() else 0
        val revealPenalty = (base.toDouble() * revealedCells / max(1, puzzle.openCellCount)).roundToInt()
        return max(base / 10, base + timeBonus - revealPenalty - checksUsed * CHECK_PENALTY)
    }

    fun stars(puzzle: Puzzle, elapsedSeconds: Long, revealedCells: Int): Int = when {
        revealedCells == 0 && elapsedSeconds <= parSeconds(puzzle.difficulty) -> 3
        revealedCells * 10 <= puzzle.openCellCount -> 2
        else -> 1
    }
}
