package com.maslarski.crossword.domain

import com.maslarski.crossword.domain.daily.DailyPuzzleGenerator
import com.maslarski.crossword.domain.model.Difficulty
import com.maslarski.crossword.domain.model.PuzzleType
import com.maslarski.crossword.domain.parser.PuzzleParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

class DailyPuzzleGeneratorTest {

    private val bank = DailyPuzzleGenerator.parseBank(File("src/main/assets/puzzles/wordbank.txt").readText())
    private val generator = DailyPuzzleGenerator(bank)
    private val start = LocalDate.of(2026, 10, 1)

    @Test
    fun `bundled word bank is large and clean`() {
        assertTrue("bank has ${bank.size} entries", bank.size >= 200)
        assertTrue(bank.all { e -> e.answer.all { it in 'A'..'Z' } && e.clue.isNotBlank() })
    }

    @Test
    fun `same date always builds the same puzzle`() {
        val a = generator.generate(start)!!
        val b = DailyPuzzleGenerator(bank).generate(start)!!
        assertEquals(a.fingerprint, b.fingerprint)
        assertEquals(a.words.map { it.clue }, b.words.map { it.clue })
    }

    @Test
    fun `every day of two months gets its own valid puzzle`() {
        val puzzles = (0L until 61L).map { start.plusDays(it) }.map { date -> date to assertNotNullPuzzle(date) }
        puzzles.forEach { (date, puzzle) ->
            assertEquals(DailyPuzzleGenerator.idFor(date), puzzle.id)
            assertEquals(PuzzleType.DAILY, puzzle.type)
            assertTrue("${puzzle.id} has ${puzzle.words.size} words", puzzle.words.size >= 6)
            assertTrue(puzzle.rows <= PuzzleParser.MAX_SIZE && puzzle.cols <= PuzzleParser.MAX_SIZE)
            assertTrue(puzzle.words.all { w -> bank.any { it.answer == w.answer && it.clue == w.clue } })
        }
        assertEquals(puzzles.size, puzzles.map { it.second.fingerprint }.toSet().size)
    }

    private fun assertNotNullPuzzle(date: LocalDate) = generator.generate(date).also { assertNotNull("no puzzle for $date", it) }!!

    @Test
    fun `difficulty follows the weekday`() {
        assertEquals(Difficulty.EASY, generator.generate(LocalDate.of(2026, 10, 5))!!.difficulty)
        assertEquals(Difficulty.MEDIUM, generator.generate(LocalDate.of(2026, 10, 7))!!.difficulty)
        assertEquals(Difficulty.HARD, generator.generate(LocalDate.of(2026, 10, 10))!!.difficulty)
    }

    @Test
    fun `generated ids round trip to their date`() {
        assertEquals(start, DailyPuzzleGenerator.dateOf(DailyPuzzleGenerator.idFor(start)))
        assertNull(DailyPuzzleGenerator.dateOf("daily-01"))
        assertNull(DailyPuzzleGenerator.dateOf("easy-01"))
    }

    @Test
    fun `tiny bank produces nothing`() {
        assertNull(DailyPuzzleGenerator(bank.take(5)).generate(start))
    }
}
