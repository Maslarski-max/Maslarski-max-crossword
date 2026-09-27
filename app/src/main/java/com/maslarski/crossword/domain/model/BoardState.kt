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

    /** True when this board's shape and indices are valid for [puzzle]. */
    fun fits(puzzle: Puzzle): Boolean =
        entries.length == puzzle.cellCount &&
            selected in 0 until puzzle.cellCount &&
            !puzzle.isBlock(selected) &&
            entries.indices.all { (entries[it] == Puzzle.BLOCK) == puzzle.isBlock(it) } &&
            (revealed + incorrect).all { it in 0 until puzzle.cellCount }
}
