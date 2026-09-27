package com.maslarski.crossword.domain.arena

import com.maslarski.crossword.domain.model.Direction
import com.maslarski.crossword.domain.model.Puzzle

/** One answer slot of the arena grid. [clueCell] holds its clue and points at the first of [cells]. */
data class ArenaWord(
    val key: String,
    val direction: Direction,
    val clue: String,
    val answer: String,
    val clueCell: Int,
    val cells: List<Int>,
)

/** A non-letter cell. It hosts up to one clue per direction; with neither it is plain filler. */
data class ClueCell(val across: ArenaWord?, val down: ArenaWord?)

/**
 * Arrow-word view of a classic [Puzzle]: one extra row on top and column on the left, and every clue written
 * in the non-letter cell right before its answer, pointing right (across) or down. Cells are row-major indices.
 */
class ArenaLayout(val puzzle: Puzzle) {
    val rows: Int = puzzle.rows + 1
    val cols: Int = puzzle.cols + 1
    val cellCount: Int = rows * cols

    private val solution = CharArray(cellCount) { Puzzle.BLOCK }
    private val clues = arrayOfNulls<ClueCell>(cellCount)
    private val acrossAt = IntArray(cellCount) { -1 }
    private val downAt = IntArray(cellCount) { -1 }

    val words: List<ArenaWord>
    val letterCells: List<Int>

    init {
        for (i in 0 until puzzle.cellCount) {
            if (!puzzle.isBlock(i)) solution[fromPuzzle(i)] = puzzle.solutionAt(i)
        }
        words = puzzle.orderedWords.map { word ->
            val cells = word.cells.map(::fromPuzzle)
            val clueCell = if (word.direction == Direction.ACROSS) cells.first() - 1 else cells.first() - cols
            require(solution[clueCell] == Puzzle.BLOCK) { "${puzzle.id}: no room for the clue of ${word.key}" }
            ArenaWord(word.key, word.direction, word.clue, word.answer, clueCell, cells)
        }
        words.forEachIndexed { i, word ->
            val target = if (word.direction == Direction.ACROSS) acrossAt else downAt
            word.cells.forEach { target[it] = i }
        }
        words.groupBy { it.clueCell }.forEach { (cell, hosted) ->
            clues[cell] = ClueCell(
                across = hosted.firstOrNull { it.direction == Direction.ACROSS },
                down = hosted.firstOrNull { it.direction == Direction.DOWN },
            )
        }
        letterCells = (0 until cellCount).filter { solution[it] != Puzzle.BLOCK }
    }

    fun fromPuzzle(index: Int): Int = (puzzle.rowOf(index) + 1) * cols + puzzle.colOf(index) + 1
    fun index(row: Int, col: Int): Int = row * cols + col
    fun rowOf(index: Int): Int = index / cols
    fun colOf(index: Int): Int = index % cols

    fun isLetter(index: Int): Boolean = solution[index] != Puzzle.BLOCK
    fun solutionAt(index: Int): Char = solution[index]
    fun clueAt(index: Int): ClueCell? = clues[index]

    fun wordAt(index: Int, direction: Direction): ArenaWord? {
        val i = if (direction == Direction.ACROSS) acrossAt[index] else downAt[index]
        return if (i >= 0) words[i] else null
    }

    fun wordsAt(index: Int): List<ArenaWord> = listOfNotNull(wordAt(index, Direction.ACROSS), wordAt(index, Direction.DOWN))

    fun wordByKey(key: String): ArenaWord? = words.firstOrNull { it.key == key }
}
