package com.maslarski.crossword.domain.model

/**
 * Player's board. [entries] has one char per cell: a letter, [Puzzle.EMPTY] or [Puzzle.BLOCK].
 * [revealed] cells were filled by a hint and are locked; [incorrect] cells were flagged by "Check errors".
 */
data class BoardState(
    val entries: String,
    val revealed: Set<Int>,
    val incorrect: Set<Int>,
    val selected: Int,
    val direction: Direction,
) {
    fun entryAt(index: Int): Char = entries[index]
    fun isFilled(index: Int): Boolean = entries[index] != Puzzle.EMPTY && entries[index] != Puzzle.BLOCK
}
