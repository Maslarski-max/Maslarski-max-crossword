package com.maslarski.crossword.domain.arena

import com.maslarski.crossword.domain.model.Difficulty
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

/** Why a set of rack placements cannot be submitted. */
enum class PlacementError { NOT_YOUR_TURN, NO_TILES, BAD_TILE, CELL_TAKEN, NOT_ONE_WORD }

/**
 * Turn-based arena rules. Each side holds a rack of tiles drawn from a bag containing exactly the letters of the
 * empty cells, so every tile belongs somewhere. A turn places tiles into a single answer slot: if all are correct
 * they score their cell values (plus the word length for every word they complete) and the rack refills; any wrong
 * tile voids the move. Either way, or on a pass, the turn switches. The match ends when the board is full or after
 * [SCORELESS_TURN_LIMIT] consecutive turns without points.
 */
object ArenaRules {
    const val RACK_SIZE = 7
    const val WIN_REWARD = 20
    const val DRAW_REWARD = 10
    const val SCORELESS_TURN_LIMIT = 4

    private const val GIVEN_RATIO = 0.3
    private const val DOUBLE_RATIO = 0.1
    private const val TRIPLE_RATIO = 0.05

    fun newMatch(layout: ArenaLayout, difficulty: Difficulty, seed: Long): ArenaState {
        val random = Random(seed)
        val letters = layout.letterCells.shuffled(random)
        val givenCount = (letters.size * GIVEN_RATIO).roundToInt().coerceAtMost(letters.size - 1)
        val given = letters.take(givenCount).toHashSet()
        val open = letters.drop(givenCount).shuffled(random)
        val triples = open.take(max(1, (open.size * TRIPLE_RATIO).roundToInt())).toHashSet()
        val doubles = open.drop(triples.size).take(max(1, (open.size * DOUBLE_RATIO).roundToInt())).toHashSet()

        val owners = CharArray(layout.cellCount) { cell ->
            when {
                !layout.isLetter(cell) -> ArenaState.NONE
                cell in given -> ArenaState.GIVEN
                else -> ArenaState.EMPTY
            }
        }
        val values = CharArray(layout.cellCount) { cell ->
            when {
                !layout.isLetter(cell) -> '0'
                cell in triples -> '3'
                cell in doubles -> '2'
                else -> '1'
            }
        }
        val bag = open.map(layout::solutionAt).shuffled(random).joinToString("")
        return ArenaState(
            puzzleId = layout.puzzle.id,
            fingerprint = layout.puzzle.fingerprint,
            difficulty = difficulty,
            seed = seed,
            owners = String(owners),
            values = String(values),
            bag = bag.drop(RACK_SIZE * 2),
            playerRack = bag.take(RACK_SIZE),
            opponentRack = bag.drop(RACK_SIZE).take(RACK_SIZE),
        )
    }

    /** Draws from the bag until both racks hold [RACK_SIZE] tiles, e.g. for matches saved with a smaller rack. */
    fun topUpRacks(state: ArenaState): ArenaState {
        if (state.finished) return state
        val playerDraw = state.bag.take((RACK_SIZE - state.playerRack.length).coerceAtLeast(0))
        val rest = state.bag.drop(playerDraw.length)
        val opponentDraw = rest.take((RACK_SIZE - state.opponentRack.length).coerceAtLeast(0))
        if (playerDraw.isEmpty() && opponentDraw.isEmpty()) return state
        return state.copy(
            bag = rest.drop(opponentDraw.length),
            playerRack = state.playerRack + playerDraw,
            opponentRack = state.opponentRack + opponentDraw,
        )
    }

    /** The single answer slot that contains all [cells], if any. */
    fun wordFor(layout: ArenaLayout, cells: Collection<Int>): ArenaWord? {
        val first = cells.firstOrNull() ?: return null
        return layout.wordsAt(first).firstOrNull { word -> cells.all { it in word.cells } }
    }

    /** [placements] maps board cell to rack slot. Returns null when the move may be submitted. */
    fun check(layout: ArenaLayout, state: ArenaState, side: Side, placements: Map<Int, Int>): PlacementError? {
        if (state.finished || state.turn != side) return PlacementError.NOT_YOUR_TURN
        if (placements.isEmpty()) return PlacementError.NO_TILES
        val rack = state.rack(side)
        if (placements.values.toSet().size != placements.size || placements.values.any { it !in rack.indices }) {
            return PlacementError.BAD_TILE
        }
        if (placements.keys.any { it !in 0 until layout.cellCount || !state.isEmpty(it) }) return PlacementError.CELL_TAKEN
        if (wordFor(layout, placements.keys) == null) return PlacementError.NOT_ONE_WORD
        return null
    }

    /** Applies a move that passed [check]. */
    fun play(layout: ArenaLayout, state: ArenaState, side: Side, placements: Map<Int, Int>): ArenaState {
        require(check(layout, state, side, placements) == null) { "Invalid placement" }
        val rack = state.rack(side)
        val cells = placements.keys.sorted()
        val wrong = cells.filter { rack[placements.getValue(it)] != layout.solutionAt(it) }
        if (wrong.isNotEmpty()) {
            return advance(state, ArenaMove(side, MoveKind.MISS, cells = wrong), scored = false)
        }

        val owners = state.owners.toCharArray()
        cells.forEach { owners[it] = ArenaState.ownerCode(side) }
        val completed = layout.words.filter { word ->
            word.cells.any { it in placements } && word.cells.all { owners[it] != ArenaState.EMPTY }
        }
        val points = cells.map(state::valueAt)
        val move = ArenaMove(
            side = side,
            kind = MoveKind.PLAY,
            cells = cells,
            points = points,
            completedWords = completed.map { it.key },
            wordBonus = completed.sumOf { it.cells.size },
        )
        val used = placements.values.toHashSet()
        val kept = rack.filterIndexed { i, _ -> i !in used }
        val drawn = state.bag.take(RACK_SIZE - kept.length)
        val newRack = kept + drawn
        val next = state.copy(
            owners = String(owners),
            bag = state.bag.drop(drawn.length),
            playerRack = if (side == Side.PLAYER) newRack else state.playerRack,
            opponentRack = if (side == Side.OPPONENT) newRack else state.opponentRack,
            playerScore = state.playerScore + if (side == Side.PLAYER) move.total else 0,
            opponentScore = state.opponentScore + if (side == Side.OPPONENT) move.total else 0,
        )
        return advance(next, move, scored = true)
    }

    fun pass(state: ArenaState, side: Side): ArenaState {
        require(!state.finished && state.turn == side) { "Not ${side.name}'s turn" }
        return advance(state, ArenaMove(side, MoveKind.PASS), scored = false)
    }

    private fun advance(state: ArenaState, move: ArenaMove, scored: Boolean): ArenaState {
        val scoreless = if (scored) 0 else state.scorelessTurns + 1
        return state.copy(
            turn = move.side.other(),
            turnNumber = state.turnNumber + 1,
            scorelessTurns = scoreless,
            lastMove = move,
            finished = state.emptyCells == 0 || scoreless >= SCORELESS_TURN_LIMIT,
        )
    }

    /** Greedily assigns tiles of [rack] to the empty cells of [word] they solve; maps cell to rack slot. */
    fun fillable(word: ArenaWord, layout: ArenaLayout, state: ArenaState, rack: String): Map<Int, Int> {
        val used = BooleanArray(rack.length)
        val result = LinkedHashMap<Int, Int>()
        for (cell in word.cells) {
            if (!state.isEmpty(cell)) continue
            val slot = rack.indices.firstOrNull { !used[it] && rack[it] == layout.solutionAt(cell) } ?: continue
            used[slot] = true
            result[cell] = slot
        }
        return result
    }

    /** Points a fully correct [fill] of [word] would earn, including the completion bonus. */
    fun potential(word: ArenaWord, state: ArenaState, fill: Map<Int, Int>): Int {
        val completes = word.cells.all { !state.isEmpty(it) || it in fill }
        return fill.keys.sumOf(state::valueAt) + if (completes) word.cells.size else 0
    }

    /**
     * Empty cell a Reveal Letter hint fills: [selected] if it is still open, else the first open cell of [word],
     * else the first open cell on the board. Cells in [skip] (tiles placed but not yet submitted) are never chosen.
     */
    fun revealTarget(layout: ArenaLayout, state: ArenaState, selected: Int?, word: ArenaWord?, skip: Set<Int>): Int? {
        fun open(cell: Int) = layout.isLetter(cell) && state.isEmpty(cell) && cell !in skip
        return selected?.takeIf(::open)
            ?: word?.cells?.firstOrNull(::open)
            ?: layout.letterCells.firstOrNull(::open)
    }

    /**
     * Writes the solution letter into empty [cell] as a given letter, without scoring or ending the turn. The tile is
     * taken from the bag if possible, otherwise from a rack, which then draws a replacement, so the bag and racks keep
     * spelling exactly the open cells.
     */
    fun revealLetter(layout: ArenaLayout, state: ArenaState, cell: Int): ArenaState {
        require(layout.isLetter(cell) && state.isEmpty(cell)) { "Cell $cell is not open" }
        val letter = layout.solutionAt(cell)
        val owners = state.owners.toCharArray().also { it[cell] = ArenaState.GIVEN }
        var bag = state.bag
        var playerRack = state.playerRack
        var opponentRack = state.opponentRack
        fun String.without(index: Int) = removeRange(index, index + 1)
        fun refill(rack: String): String = (rack + bag.take(RACK_SIZE - rack.length)).also { bag = bag.drop(it.length - rack.length) }
        when {
            letter in bag -> bag = bag.without(bag.indexOf(letter))
            letter in playerRack -> playerRack = refill(playerRack.without(playerRack.indexOf(letter)))
            else -> opponentRack = refill(opponentRack.without(opponentRack.indexOf(letter)))
        }
        val next = state.copy(owners = String(owners), bag = bag, playerRack = playerRack, opponentRack = opponentRack)
        return if (next.emptyCells == 0) next.copy(finished = true) else next
    }

    fun rewardCoins(outcome: ArenaOutcome): Int = when (outcome) {
        ArenaOutcome.WON -> WIN_REWARD
        ArenaOutcome.DRAW -> DRAW_REWARD
        ArenaOutcome.LOST, ArenaOutcome.FORFEIT -> 0
    }
}
