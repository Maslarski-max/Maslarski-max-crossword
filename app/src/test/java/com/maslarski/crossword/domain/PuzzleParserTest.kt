package com.maslarski.crossword.domain

import com.maslarski.crossword.domain.model.Difficulty
import com.maslarski.crossword.domain.model.Direction
import com.maslarski.crossword.domain.model.PuzzleType
import com.maslarski.crossword.domain.parser.PuzzleFormatException
import com.maslarski.crossword.domain.parser.PuzzleParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PuzzleParserTest {

    private val parser = PuzzleParser()

    @Test
    fun `parses grid, numbering and clues`() {
        val puzzle = miniPuzzle()
        assertEquals("mini", puzzle.id)
        assertEquals(Difficulty.EASY, puzzle.difficulty)
        assertEquals(3, puzzle.rows)
        assertEquals(3, puzzle.cols)
        assertEquals(8, puzzle.openCellCount)
        assertTrue(puzzle.isBlock(4))

        assertEquals(listOf("1:ACROSS", "3:ACROSS"), puzzle.acrossWords.map { it.key })
        assertEquals(listOf("1:DOWN", "2:DOWN"), puzzle.downWords.map { it.key })
        assertEquals(listOf("CAT", "BOW", "CAB", "TOW"), puzzle.orderedWords.map { it.answer })
        assertEquals(listOf(2, 5, 8), puzzle.wordByKey("2:DOWN")!!.cells)
        assertEquals("Archer's weapon", puzzle.wordByKey("3:ACROSS")!!.clue)

        assertEquals(1, puzzle.numberAt(0))
        assertEquals(2, puzzle.numberAt(2))
        assertEquals(3, puzzle.numberAt(6))
        assertEquals(0, puzzle.numberAt(1))
    }

    @Test
    fun `maps cells to crossing words`() {
        val puzzle = miniPuzzle()
        assertEquals("CAT", puzzle.wordAt(0, Direction.ACROSS)!!.answer)
        assertEquals("CAB", puzzle.wordAt(0, Direction.DOWN)!!.answer)
        assertNull(puzzle.wordAt(3, Direction.ACROSS))
        assertEquals("CAB", puzzle.wordAt(3, Direction.DOWN)!!.answer)
    }

    @Test
    fun `lowercase letters and difficulty are accepted`() {
        val puzzle = parser.parse(MINI_JSON.replace("\"CAT\"", "\"cat\"").replace("EASY", "easy"), PuzzleType.DAILY)
        assertEquals("CAT", puzzle.acrossWords.first().answer)
        assertEquals(PuzzleType.DAILY, puzzle.type)
    }

    @Test
    fun `missing clue is rejected`() = assertInvalid(MINI_JSON.replace(""""2": "Pull a car"""", """"9": "Nothing""""))

    @Test
    fun `clue without a slot is rejected`() = assertInvalid(MINI_JSON.replace(""""3": "Archer's weapon"""", """"3": "Archer's weapon", "7": "Extra""""))

    @Test
    fun `non rectangular grid is rejected`() = assertInvalid(MINI_JSON.replace("\"BOW\"", "\"BO\""))

    @Test
    fun `invalid characters are rejected`() = assertInvalid(MINI_JSON.replace("\"A#O\"", "\"A.O\""))

    @Test
    fun `unknown difficulty is rejected`() = assertInvalid(MINI_JSON.replace("EASY", "IMPOSSIBLE"))

    @Test
    fun `malformed json is rejected`() = assertInvalid("{ not json")

    @Test
    fun `isolated open cell is rejected`() = assertInvalid(
        """{"id":"x","title":"x","difficulty":"EASY","grid":["AB#","###","#C#"],"clues":{"across":{"1":"x"}}}""",
    )

    private fun assertInvalid(json: String) {
        assertThrows(PuzzleFormatException::class.java) { parser.parse(json, PuzzleType.LEVEL) }
    }
}
