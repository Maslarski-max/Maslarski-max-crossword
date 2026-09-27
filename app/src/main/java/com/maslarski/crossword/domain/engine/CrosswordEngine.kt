package com.maslarski.crossword.domain.engine

import com.maslarski.crossword.domain.model.BoardState
import com.maslarski.crossword.domain.model.Direction
import com.maslarski.crossword.domain.model.Puzzle
import com.maslarski.crossword.domain.model.Word

/** Pure crossword board rules. Every operation returns a new [BoardState]; nothing here touches I/O. */
object CrosswordEngine {

    fun newBoard(puzzle: Puzzle): BoardState {
        val entries = buildString(puzzle.cellCount) {
            for (i in 0 until puzzle.cellCount) append(if (puzzle.isBlock(i)) Puzzle.BLOCK else Puzzle.EMPTY)
        }
        val start = puzzle.firstOpenCell()
        val direction = if (puzzle.wordAt(start, Direction.ACROSS) != null) Direction.ACROSS else Direction.DOWN
        return BoardState(entries, emptySet(), emptySet(), start, direction)
    }

    fun activeWord(puzzle: Puzzle, state: BoardState): Word? =
        puzzle.wordAt(state.selected, state.direction) ?: puzzle.wordAt(state.selected, state.direction.toggled())

    /** Tapping the selected cell again flips direction (when a crossing word exists). */
    fun select(puzzle: Puzzle, state: BoardState, index: Int): BoardState {
        if (index !in 0 until puzzle.cellCount || puzzle.isBlock(index)) return state
        if (index == state.selected) return toggleDirection(puzzle, state)
        val direction = if (puzzle.wordAt(index, state.direction) != null) state.direction else state.direction.toggled()
        return state.copy(selected = index, direction = direction)
    }

    fun toggleDirection(puzzle: Puzzle, state: BoardState): BoardState {
        val other = state.direction.toggled()
        return if (puzzle.wordAt(state.selected, other) != null) state.copy(direction = other) else state
    }

    fun selectWord(puzzle: Puzzle, state: BoardState, word: Word): BoardState {
        val target = word.cells.firstOrNull { !state.isFilled(it) } ?: word.cells.first()
        return state.copy(selected = target, direction = word.direction)
    }

    fun input(puzzle: Puzzle, state: BoardState, letter: Char): BoardState {
        val ch = letter.uppercaseChar()
        if (ch !in 'A'..'Z') return state
        val index = state.selected
        var next = state
        if (index !in state.revealed) {
            val entries = StringBuilder(state.entries).apply { setCharAt(index, ch) }.toString()
            next = state.copy(entries = entries, incorrect = state.incorrect - index)
        }
        return advance(puzzle, next)
    }

    fun delete(puzzle: Puzzle, state: BoardState): BoardState {
        val index = state.selected
        if (state.isFilled(index) && index !in state.revealed) return clear(state, index)
        val word = activeWord(puzzle, state) ?: return state
        val pos = word.cells.indexOf(index)
        if (pos <= 0) return state
        val previous = word.cells[pos - 1]
        val moved = state.copy(selected = previous, direction = word.direction)
        return if (previous in state.revealed) moved else clear(moved, previous)
    }

    /** Arrow-key movement. Moving across the current direction first flips direction, like most apps. */
    fun move(puzzle: Puzzle, state: BoardState, dRow: Int, dCol: Int): BoardState {
        val wanted = if (dRow != 0) Direction.DOWN else Direction.ACROSS
        if (wanted != state.direction && puzzle.wordAt(state.selected, wanted) != null) {
            return state.copy(direction = wanted)
        }
        var row = puzzle.rowOf(state.selected) + dRow
        var col = puzzle.colOf(state.selected) + dCol
        while (row in 0 until puzzle.rows && col in 0 until puzzle.cols) {
            val index = puzzle.index(row, col)
            if (!puzzle.isBlock(index)) return select(puzzle, state, index)
            row += dRow
            col += dCol
        }
        return state
    }

    fun nextWord(puzzle: Puzzle, state: BoardState, step: Int = 1): BoardState {
        val words = puzzle.orderedWords
        if (words.isEmpty()) return state
        val current = activeWord(puzzle, state)
        val start = words.indexOf(current).coerceAtLeast(0)
        val target = words[Math.floorMod(start + step, words.size)]
        return selectWord(puzzle, state, target)
    }

    fun revealLetter(puzzle: Puzzle, state: BoardState): BoardState = reveal(puzzle, state, listOf(state.selected))

    fun revealWord(puzzle: Puzzle, state: BoardState): BoardState =
        activeWord(puzzle, state)?.let { reveal(puzzle, state, it.cells) } ?: state

    /** Flags filled cells whose letter doesn't match the solution. */
    fun checkErrors(puzzle: Puzzle, state: BoardState): BoardState {
        val wrong = (0 until puzzle.cellCount).filterTo(mutableSetOf()) {
            state.isFilled(it) && state.entries[it] != puzzle.solutionAt(it)
        }
        return state.copy(incorrect = wrong)
    }

    fun isSolved(puzzle: Puzzle, state: BoardState): Boolean = state.entries == puzzle.solution

    fun isComplete(state: BoardState): Boolean = state.entries.none { it == Puzzle.EMPTY }

    fun isWordFilled(state: BoardState, word: Word): Boolean = word.cells.all { state.isFilled(it) }

    fun filledCount(state: BoardState): Int = state.entries.count { it != Puzzle.EMPTY && it != Puzzle.BLOCK }

    /** Cells [reveal] would change; used to avoid charging for a no-op hint. */
    fun revealableCells(puzzle: Puzzle, state: BoardState, cells: List<Int>): List<Int> =
        cells.filter { !puzzle.isBlock(it) && it !in state.revealed && state.entries[it] != puzzle.solutionAt(it) }

    private fun reveal(puzzle: Puzzle, state: BoardState, cells: List<Int>): BoardState {
        val targets = revealableCells(puzzle, state, cells)
        if (targets.isEmpty()) return state
        val entries = StringBuilder(state.entries)
        targets.forEach { entries.setCharAt(it, puzzle.solutionAt(it)) }
        return state.copy(
            entries = entries.toString(),
            revealed = state.revealed + targets,
            incorrect = state.incorrect - targets.toSet(),
        )
    }

    private fun clear(state: BoardState, index: Int): BoardState {
        val entries = StringBuilder(state.entries).apply { setCharAt(index, Puzzle.EMPTY) }.toString()
        return state.copy(entries = entries, incorrect = state.incorrect - index)
    }

    /** After typing: next empty cell in the word, else the next word with gaps, else stay put. */
    private fun advance(puzzle: Puzzle, state: BoardState): BoardState {
        val word = activeWord(puzzle, state) ?: return state
        val pos = word.cells.indexOf(state.selected)
        val after = word.cells.drop(pos + 1).firstOrNull { !state.isFilled(it) }
            ?: word.cells.take(pos).firstOrNull { !state.isFilled(it) }
        if (after != null) return state.copy(selected = after, direction = word.direction)
        if (isComplete(state)) {
            val nextCell = word.cells.getOrNull(pos + 1) ?: return state
            return state.copy(selected = nextCell, direction = word.direction)
        }
        val words = puzzle.orderedWords
        val start = words.indexOf(word)
        for (step in 1..words.size) {
            val candidate = words[(start + step) % words.size]
            if (!isWordFilled(state, candidate)) return selectWord(puzzle, state, candidate)
        }
        return state
    }
}
