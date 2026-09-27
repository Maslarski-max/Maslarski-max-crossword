package com.maslarski.crossword.domain

import com.maslarski.crossword.domain.engine.GameRules
import com.maslarski.crossword.domain.engine.Streaks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class GameRulesTest {

    private val puzzle = miniPuzzle()

    @Test
    fun `faster clean solves score higher`() {
        val fast = GameRules.score(puzzle, elapsedSeconds = 30, revealedCells = 0, checksUsed = 0)
        val slow = GameRules.score(puzzle, elapsedSeconds = 3_000, revealedCells = 0, checksUsed = 0)
        val helped = GameRules.score(puzzle, elapsedSeconds = 30, revealedCells = 4, checksUsed = 2)
        assertTrue(fast > slow)
        assertTrue(fast > helped)
        assertEquals(GameRules.baseScore(puzzle.difficulty), slow)
    }

    @Test
    fun `score never drops below a tenth of base`() {
        val score = GameRules.score(puzzle, elapsedSeconds = 10_000, revealedCells = puzzle.openCellCount, checksUsed = 50)
        assertEquals(GameRules.baseScore(puzzle.difficulty) / 10, score)
    }

    @Test
    fun `stars reward unaided solves under par`() {
        assertEquals(3, GameRules.stars(puzzle, 10, 0))
        assertEquals(2, GameRules.stars(puzzle, 10_000, 0))
        assertEquals(1, GameRules.stars(puzzle, 10, puzzle.openCellCount))
    }

    @Test
    fun `streak counts consecutive days ending today or yesterday`() {
        val today = LocalDate.of(2026, 9, 27)
        val days = listOf(today.minusDays(1), today.minusDays(2), today.minusDays(4))
        assertEquals(2, Streaks.current(days, today))
        assertEquals(3, Streaks.current(days + today, today))
        assertEquals(0, Streaks.current(listOf(today.minusDays(3)), today))
    }
}
