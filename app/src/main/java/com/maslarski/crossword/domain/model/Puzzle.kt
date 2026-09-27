package com.maslarski.crossword.domain.model

import java.util.zip.CRC32

enum class Difficulty { EASY, MEDIUM, HARD }

enum class PuzzleType { LEVEL, DAILY }

enum class Direction {
    ACROSS, DOWN;

    fun toggled(): Direction = if (this == ACROSS) DOWN else ACROSS
}

/** A single answer slot in the grid. [cells] are row-major indices into the puzzle grid. */
data class Word(
    val number: Int,
    val direction: Direction,
    val row: Int,
    val col: Int,
    val answer: String,
    val clue: String,
    val cells: List<Int>,
) {
    val key: String get() = "$number:${direction.name}"
    val length: Int get() = answer.length
}

/**
 * Immutable, validated puzzle definition. Cells are addressed by row-major index (row * cols + col).
 * Blocks are stored as [BLOCK] in [solution].
 */
class Puzzle(
    val id: String,
    val title: String,
    val author: String,
    val difficulty: Difficulty,
    val type: PuzzleType,
    val order: Int,
    val rows: Int,
    val cols: Int,
    val solution: String,
    val words: List<Word>,
) {
    val cellCount: Int get() = rows * cols
    val openCellCount: Int = solution.count { it != BLOCK }

    /** Identifies this exact answer grid, so boards saved against an earlier revision can be detected. */
    val fingerprint: String = CRC32().run {
        update("${rows}x$cols:$solution".toByteArray())
        value.toString(16)
    }

    private val numbers = IntArray(cellCount)
    private val acrossIndex = IntArray(cellCount) { -1 }
    private val downIndex = IntArray(cellCount) { -1 }

    init {
        require(solution.length == cellCount) { "Solution length ${solution.length} != $rows x $cols" }
        words.forEachIndexed { i, word ->
            numbers[word.cells.first()] = word.number
            val target = if (word.direction == Direction.ACROSS) acrossIndex else downIndex
            word.cells.forEach { target[it] = i }
        }
    }

    val acrossWords: List<Word> = words.filter { it.direction == Direction.ACROSS }
    val downWords: List<Word> = words.filter { it.direction == Direction.DOWN }

    /** Words in clue-list order: all across clues, then all down clues. */
    val orderedWords: List<Word> = acrossWords + downWords

    fun index(row: Int, col: Int): Int = row * cols + col
    fun rowOf(index: Int): Int = index / cols
    fun colOf(index: Int): Int = index % cols

    fun isBlock(index: Int): Boolean = solution[index] == BLOCK
    fun solutionAt(index: Int): Char = solution[index]
    fun numberAt(index: Int): Int = numbers[index]

    fun wordAt(index: Int, direction: Direction): Word? {
        val i = if (direction == Direction.ACROSS) acrossIndex[index] else downIndex[index]
        return if (i >= 0) words[i] else null
    }

    fun wordByKey(key: String): Word? = words.firstOrNull { it.key == key }

    fun firstOpenCell(): Int = orderedWords.firstOrNull()?.cells?.first() ?: solution.indexOfFirst { it != BLOCK }

    companion object {
        const val BLOCK = '#'
        const val EMPTY = ' '
    }
}
