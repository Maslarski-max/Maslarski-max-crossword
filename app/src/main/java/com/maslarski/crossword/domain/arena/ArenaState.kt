package com.maslarski.crossword.domain.arena

import com.maslarski.crossword.domain.model.Difficulty
import kotlinx.serialization.Serializable

enum class Side {
    PLAYER, OPPONENT;

    fun other(): Side = if (this == PLAYER) OPPONENT else PLAYER
}

enum class MoveKind { PLAY, MISS, PASS }

enum class ArenaOutcome { WON, LOST, DRAW, FORFEIT }

/** The last completed turn. For [MoveKind.PLAY] [points] holds the value earned by each of [cells]. */
@Serializable
data class ArenaMove(
    val side: Side,
    val kind: MoveKind,
    val cells: List<Int> = emptyList(),
    val points: List<Int> = emptyList(),
    val completedWords: List<String> = emptyList(),
    val wordBonus: Int = 0,
) {
    val total: Int get() = points.sum() + wordBonus
}

/**
 * Complete, serialisable state of one arena match. Board strings are aligned with [ArenaLayout] cells.
 * Letters never need storing: only correct tiles stay on the board, so a filled cell shows its solution.
 */
@Serializable
data class ArenaState(
    val puzzleId: String,
    val fingerprint: String,
    val difficulty: Difficulty,
    val seed: Long,
    /** Per cell: [NONE], [EMPTY], [GIVEN], [PLAYER] or [OPPONENT]. */
    val owners: String,
    /** Per cell point value: '0' for non-letter cells, '1' normally, '2'/'3' on bonus cells. */
    val values: String,
    /** Undrawn tiles, drawn from the front. Bag + both racks always spell exactly the empty cells' letters. */
    val bag: String,
    val playerRack: String,
    val opponentRack: String,
    val playerScore: Int = 0,
    val opponentScore: Int = 0,
    val turn: Side = Side.PLAYER,
    val turnNumber: Int = 0,
    val scorelessTurns: Int = 0,
    val hintsLeft: Int = ArenaRules.HINTS_PER_MATCH,
    val lastMove: ArenaMove? = null,
    val finished: Boolean = false,
) {
    fun rack(side: Side): String = if (side == Side.PLAYER) playerRack else opponentRack
    fun score(side: Side): Int = if (side == Side.PLAYER) playerScore else opponentScore
    fun isEmpty(cell: Int): Boolean = owners[cell] == EMPTY
    fun isFilled(cell: Int): Boolean = owners[cell] == GIVEN || owners[cell] == PLAYER || owners[cell] == OPPONENT
    fun valueAt(cell: Int): Int = values[cell].digitToInt()

    val emptyCells: Int get() = owners.count { it == EMPTY }

    /** Result from the player's point of view once [finished]. */
    val outcome: ArenaOutcome?
        get() = when {
            !finished -> null
            playerScore > opponentScore -> ArenaOutcome.WON
            playerScore < opponentScore -> ArenaOutcome.LOST
            else -> ArenaOutcome.DRAW
        }

    companion object {
        const val NONE = '#'
        const val EMPTY = '.'
        const val GIVEN = 'G'
        const val PLAYER = 'P'
        const val OPPONENT = 'O'

        fun ownerCode(side: Side): Char = if (side == Side.PLAYER) PLAYER else OPPONENT
    }
}
