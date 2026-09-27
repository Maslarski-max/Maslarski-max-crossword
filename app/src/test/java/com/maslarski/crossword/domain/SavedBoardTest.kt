package com.maslarski.crossword.domain

import com.maslarski.crossword.domain.engine.CrosswordEngine
import com.maslarski.crossword.domain.model.PuzzleType
import com.maslarski.crossword.domain.model.SavedBoard
import com.maslarski.crossword.domain.parser.PuzzleParser
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedBoardTest {

    private val puzzle = miniPuzzle()

    /** Same block pattern and clue numbers as [miniPuzzle], but 1 Across is CAR instead of CAT. */
    private val revised = PuzzleParser().parse(MINI_JSON.replace("\"CAT\", \"A#O\"", "\"CAR\", \"A#O\""), PuzzleType.LEVEL)

    private fun saved(fingerprint: String) = SavedBoard(
        sessionId = "level:mini",
        puzzleId = puzzle.id,
        solutionFingerprint = fingerprint,
        board = CrosswordEngine.newBoard(puzzle),
        elapsedSeconds = 0,
        checksUsed = 0,
        completed = true,
        score = 0,
        stars = 0,
        updatedAt = 0,
    )

    @Test
    fun `fingerprint changes when answers change`() {
        assertNotEquals(puzzle.fingerprint, revised.fingerprint)
    }

    @Test
    fun `board saved against current answers matches`() {
        assertTrue(saved(puzzle.fingerprint).matches(puzzle))
    }

    @Test
    fun `board saved against an earlier revision does not match`() {
        assertFalse(saved(puzzle.fingerprint).matches(revised))
    }

    @Test
    fun `board without fingerprint falls back to layout check`() {
        assertTrue(saved("").matches(revised))
    }
}
