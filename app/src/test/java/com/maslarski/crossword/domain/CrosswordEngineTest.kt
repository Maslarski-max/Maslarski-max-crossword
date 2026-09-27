package com.maslarski.crossword.domain

import com.maslarski.crossword.domain.engine.CrosswordEngine
import com.maslarski.crossword.domain.model.BoardState
import com.maslarski.crossword.domain.model.Direction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrosswordEngineTest {

    private val puzzle = miniPuzzle()
    private val engine = CrosswordEngine

    private fun BoardState.type(text: String) = text.fold(this) { s, c -> engine.input(puzzle, s, c) }

    @Test
    fun `new board starts on 1 across with blocks preserved`() {
        val board = engine.newBoard(puzzle)
        assertEquals(0, board.selected)
        assertEquals(Direction.ACROSS, board.direction)
        assertEquals("    #    ", board.entries)
    }

    @Test
    fun `typing fills the word then jumps to the next unfilled word`() {
        val board = engine.newBoard(puzzle).type("CAT")
        assertEquals("CAT", board.entries.substring(0, 3))
        // 1 Across is full, so the cursor moves to the first gap of 3 Across.
        assertEquals(6, board.selected)
        assertEquals(Direction.ACROSS, board.direction)
    }

    @Test
    fun `typing ignores non letters and uppercases`() {
        val board = engine.newBoard(puzzle)
        assertEquals(board, engine.input(puzzle, board, '7'))
        assertEquals('C', engine.input(puzzle, board, 'c').entryAt(0))
    }

    @Test
    fun `tapping the selected cell toggles direction`() {
        val board = engine.newBoard(puzzle)
        assertEquals(Direction.DOWN, engine.select(puzzle, board, 0).direction)
        // Cell 3 only belongs to a down word, so selecting it switches direction.
        assertEquals(Direction.DOWN, engine.select(puzzle, board, 3).direction)
        assertEquals(board, engine.select(puzzle, board, 4))
    }

    @Test
    fun `delete clears current cell or steps back`() {
        val typed = engine.newBoard(puzzle).type("CA")
        assertEquals(2, typed.selected)
        val back = engine.delete(puzzle, typed)
        assertEquals(1, back.selected)
        assertEquals(' ', back.entryAt(1))
        val cleared = engine.delete(puzzle, back.copy(selected = 0))
        assertEquals(' ', cleared.entryAt(0))
    }

    @Test
    fun `reveal letter locks the cell`() {
        val revealed = engine.revealLetter(puzzle, engine.newBoard(puzzle))
        assertEquals('C', revealed.entryAt(0))
        assertTrue(0 in revealed.revealed)
        val retyped = engine.input(puzzle, revealed.copy(selected = 0), 'X')
        assertEquals('C', retyped.entryAt(0))
        val deleted = engine.delete(puzzle, revealed.copy(selected = 0))
        assertEquals('C', deleted.entryAt(0))
    }

    @Test
    fun `reveal word fills only wrong or empty cells`() {
        val board = engine.newBoard(puzzle).type("C")
        val revealed = engine.revealWord(puzzle, board.copy(selected = 0))
        assertEquals("CAT", revealed.entries.substring(0, 3))
        assertEquals(setOf(1, 2), revealed.revealed)
        assertEquals(revealed, engine.revealWord(puzzle, revealed))
    }

    @Test
    fun `check errors flags wrong letters and typing clears the flag`() {
        val board = engine.newBoard(puzzle).type("CUT")
        val checked = engine.checkErrors(puzzle, board)
        assertEquals(setOf(1), checked.incorrect)
        val fixed = engine.input(puzzle, checked.copy(selected = 1), 'A')
        assertTrue(fixed.incorrect.isEmpty())
    }

    @Test
    fun `solving every word completes the puzzle`() {
        var board = engine.newBoard(puzzle).type("CAT")
        assertFalse(engine.isSolved(puzzle, board))
        board = board.type("BOW")
        board = engine.selectWord(puzzle, board, puzzle.wordByKey("1:DOWN")!!).type("A")
        board = engine.selectWord(puzzle, board, puzzle.wordByKey("2:DOWN")!!).type("O")
        assertTrue(engine.isComplete(board))
        assertTrue(engine.isSolved(puzzle, board))
        assertEquals(8, engine.filledCount(board))
    }

    @Test
    fun `next and previous word wrap around clue order`() {
        val board = engine.newBoard(puzzle)
        val keys = generateSequence(board) { engine.nextWord(puzzle, it) }.take(5).map { engine.activeWord(puzzle, it)!!.key }.toList()
        assertEquals(listOf("1:ACROSS", "3:ACROSS", "1:DOWN", "2:DOWN", "1:ACROSS"), keys)
        assertEquals("2:DOWN", engine.activeWord(puzzle, engine.nextWord(puzzle, board, -1))!!.key)
    }

    @Test
    fun `arrow keys switch direction first then skip blocks`() {
        val board = engine.newBoard(puzzle).copy(selected = 3, direction = Direction.DOWN)
        val right = engine.move(puzzle, board, 0, 1)
        assertEquals(5, right.selected)
        val down = engine.move(puzzle, engine.newBoard(puzzle), 1, 0)
        assertEquals(Direction.DOWN, down.direction)
        assertEquals(0, down.selected)
    }
}
