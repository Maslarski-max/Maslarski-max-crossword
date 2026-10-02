package com.maslarski.crossword.domain

import com.maslarski.crossword.domain.arena.ArenaAi
import com.maslarski.crossword.domain.arena.ArenaLayout
import com.maslarski.crossword.domain.arena.ArenaOutcome
import com.maslarski.crossword.domain.arena.ArenaRules
import com.maslarski.crossword.domain.arena.ArenaState
import com.maslarski.crossword.domain.arena.MoveKind
import com.maslarski.crossword.domain.arena.PlacementError
import com.maslarski.crossword.domain.arena.Side
import com.maslarski.crossword.domain.model.Difficulty
import com.maslarski.crossword.domain.model.Direction
import com.maslarski.crossword.domain.model.PuzzleType
import com.maslarski.crossword.domain.parser.PuzzleParser
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Mini puzzle as an arena grid (indices in brackets):
 * ```
 * [0] -   [1] v   [2] -   [3] v
 * [4] >   [5] C   [6] A   [7] T
 * [8] -   [9] A   [10] -  [11] O
 * [12] >  [13] B  [14] O  [15] W
 * ```
 */
class ArenaRulesTest {

    private val layout = ArenaLayout(miniPuzzle())

    private fun state(
        playerRack: String = "CAT",
        opponentRack: String = "BOW",
        bag: String = "AO",
        values: String = "0000011101010111",
    ): ArenaState {
        val owners = CharArray(layout.cellCount) { if (layout.isLetter(it)) ArenaState.EMPTY else ArenaState.NONE }
        return ArenaState(
            puzzleId = "mini",
            fingerprint = layout.puzzle.fingerprint,
            difficulty = Difficulty.MEDIUM,
            seed = 7,
            owners = String(owners),
            values = values,
            bag = bag,
            playerRack = playerRack,
            opponentRack = opponentRack,
        )
    }

    private fun letters(state: ArenaState): String = (state.bag + state.playerRack + state.opponentRack).toList().sorted().joinToString("")

    private fun emptyLetters(state: ArenaState): String =
        layout.letterCells.filter(state::isEmpty).map(layout::solutionAt).sorted().joinToString("")

    @Test
    fun `layout puts each clue before its answer`() {
        assertEquals(4, layout.rows)
        assertEquals(4, layout.cols)
        assertEquals(listOf(5, 6, 7), layout.wordByKey("1:ACROSS")!!.cells)
        assertEquals("Feline", layout.clueAt(4)!!.across!!.clue)
        assertEquals("Taxi", layout.clueAt(1)!!.down!!.clue)
        assertEquals("Pull a car", layout.clueAt(3)!!.down!!.clue)
        assertEquals("Archer's weapon", layout.clueAt(12)!!.across!!.clue)
        assertNull(layout.clueAt(10))
        assertFalse(layout.isLetter(10))
        assertEquals("1:DOWN", layout.wordAt(9, Direction.DOWN)!!.key)
    }

    @Test
    fun `every bundled puzzle converts to an arena layout`() {
        val parser = PuzzleParser()
        val files = File("src/main/assets/puzzles").walk().filter { it.extension == "json" }.toList()
        assertTrue(files.isNotEmpty())
        files.forEach { file ->
            val arena = ArenaLayout(parser.parse(file.readText(), PuzzleType.LEVEL))
            arena.words.forEach { word ->
                assertFalse(arena.isLetter(word.clueCell))
                val hosted = arena.clueAt(word.clueCell)!!
                assertEquals(word, if (word.direction == Direction.ACROSS) hosted.across else hosted.down)
            }
        }
    }

    @Test
    fun `new match deals every empty cell's letter exactly once`() {
        val puzzle = PuzzleParser().parse(File("src/main/assets/puzzles/levels/medium-01.json").readText(), PuzzleType.LEVEL)
        val arena = ArenaLayout(puzzle)
        val match = ArenaRules.newMatch(arena, Difficulty.MEDIUM, seed = 42)
        val empty = arena.letterCells.filter(match::isEmpty)
        assertEquals(empty.map(arena::solutionAt).sorted(), (match.bag + match.playerRack + match.opponentRack).toList().sorted())
        assertEquals(ArenaRules.RACK_SIZE, match.playerRack.length)
        assertEquals(ArenaRules.RACK_SIZE, match.opponentRack.length)
        assertTrue(match.values.contains('2') && match.values.contains('3'))
        assertTrue(arena.letterCells.count { match.owners[it] == ArenaState.GIVEN } in 1 until arena.letterCells.size)
        assertEquals(match, ArenaRules.newMatch(arena, Difficulty.MEDIUM, seed = 42))
    }

    @Test
    fun `racks saved with fewer tiles are topped up from the bag`() {
        val puzzle = PuzzleParser().parse(File("src/main/assets/puzzles/levels/medium-01.json").readText(), PuzzleType.LEVEL)
        val match = ArenaRules.newMatch(ArenaLayout(puzzle), Difficulty.MEDIUM, seed = 42)
        val legacy = match.copy(
            bag = match.playerRack.drop(5) + match.opponentRack.drop(5) + match.bag,
            playerRack = match.playerRack.take(5),
            opponentRack = match.opponentRack.take(5),
        )
        val topped = ArenaRules.topUpRacks(legacy)
        assertEquals(ArenaRules.RACK_SIZE, topped.playerRack.length)
        assertEquals(ArenaRules.RACK_SIZE, topped.opponentRack.length)
        assertEquals(letters(legacy), letters(topped))
        assertEquals(match, ArenaRules.topUpRacks(match))
    }

    @Test
    fun `placements must be one word on empty cells from the rack`() {
        val s = state()
        assertEquals(PlacementError.NO_TILES, ArenaRules.check(layout, s, Side.PLAYER, emptyMap()))
        assertEquals(PlacementError.NOT_YOUR_TURN, ArenaRules.check(layout, s, Side.OPPONENT, mapOf(13 to 0)))
        assertEquals(PlacementError.NOT_ONE_WORD, ArenaRules.check(layout, s, Side.PLAYER, mapOf(5 to 0, 15 to 1)))
        assertEquals(PlacementError.BAD_TILE, ArenaRules.check(layout, s, Side.PLAYER, mapOf(5 to 0, 6 to 0)))
        assertEquals(PlacementError.CELL_TAKEN, ArenaRules.check(layout, s, Side.PLAYER, mapOf(10 to 0)))
        assertNull(ArenaRules.check(layout, s, Side.PLAYER, mapOf(5 to 0, 9 to 1)))
    }

    @Test
    fun `correct word scores cell values plus completion bonus and refills rack`() {
        val s = state(values = "0000031101010111")
        val next = ArenaRules.play(layout, s, Side.PLAYER, mapOf(5 to 0, 6 to 1, 7 to 2))
        assertEquals(3 + 1 + 1 + 3, next.playerScore)
        assertEquals(listOf("1:ACROSS"), next.lastMove!!.completedWords)
        assertEquals("AO", next.playerRack)
        assertEquals("", next.bag)
        assertEquals(Side.OPPONENT, next.turn)
        assertEquals(0, next.scorelessTurns)
        assertEquals(letters(next).length, emptyLetters(next).length)
        assertEquals(letters(next), emptyLetters(next))
    }

    @Test
    fun `a wrong tile voids the whole move and passes the turn`() {
        val s = state()
        val next = ArenaRules.play(layout, s, Side.PLAYER, mapOf(5 to 0, 6 to 2))
        assertEquals(MoveKind.MISS, next.lastMove!!.kind)
        assertEquals(listOf(6), next.lastMove!!.cells)
        assertEquals(s.owners, next.owners)
        assertEquals(s.playerRack, next.playerRack)
        assertEquals(0, next.playerScore)
        assertEquals(Side.OPPONENT, next.turn)
        assertEquals(1, next.scorelessTurns)
    }

    @Test
    fun `match ends after consecutive scoreless turns`() {
        var s = state()
        repeat(ArenaRules.SCORELESS_TURN_LIMIT) {
            assertFalse(s.finished)
            s = ArenaRules.pass(s, s.turn)
        }
        assertTrue(s.finished)
        assertEquals(ArenaOutcome.DRAW, s.outcome)
    }

    @Test
    fun `filling the board ends the match`() {
        var s = state(playerRack = "CATAB", opponentRack = "OWO", bag = "")
        s = ArenaRules.play(layout, s, Side.PLAYER, mapOf(5 to 0, 9 to 3, 13 to 4))
        s = ArenaRules.play(layout, s, Side.OPPONENT, mapOf(14 to 0, 15 to 1))
        s = ArenaRules.play(layout, s, Side.PLAYER, mapOf(6 to 0, 7 to 1))
        assertFalse(s.finished)
        s = ArenaRules.play(layout, s, Side.OPPONENT, mapOf(11 to 0))
        assertTrue(s.finished)
        assertEquals(0, s.emptyCells)
        // Player: C,A,B + CAB, then A,T + CAT = 11. Opponent: O,W + BOW, then O + TOW = 9.
        assertEquals(11, s.playerScore)
        assertEquals(9, s.opponentScore)
        assertEquals(ArenaOutcome.WON, s.outcome)
    }

    @Test
    fun `hint marks the slot the rack fills best`() {
        assertEquals(setOf(5, 6, 7), ArenaRules.hintCells(layout, state(playerRack = "CATXY")))
        assertTrue(ArenaRules.hintCells(layout, state(playerRack = "XYZ")).isEmpty())
    }

    @Test
    fun `state survives a JSON round trip`() {
        val s = ArenaRules.play(layout, state(), Side.PLAYER, mapOf(5 to 0))
        val json = Json.encodeToString(ArenaState.serializer(), s)
        assertEquals(s, Json.decodeFromString(ArenaState.serializer(), json))
    }

    @Test
    fun `AI moves are always legal and matches always finish`() {
        val parser = PuzzleParser()
        val puzzles = File("src/main/assets/puzzles/levels").listFiles { f -> f.extension == "json" }!!
            .map { parser.parse(it.readText(), PuzzleType.LEVEL) }
        for (puzzle in puzzles) {
            val arena = ArenaLayout(puzzle)
            for (difficulty in Difficulty.entries) {
                var s = ArenaRules.newMatch(arena, difficulty, seed = puzzle.id.hashCode().toLong() + difficulty.ordinal)
                var turns = 0
                while (!s.finished) {
                    s = if (s.turn == Side.OPPONENT) {
                        val move = ArenaAi.decide(arena, s)
                        if (move.isEmpty()) ArenaRules.pass(s, Side.OPPONENT) else {
                            assertNull(ArenaRules.check(arena, s, Side.OPPONENT, move))
                            ArenaRules.play(arena, s, Side.OPPONENT, move)
                        }
                    } else {
                        // A perfect player: best hint slot, correctly filled.
                        val word = arena.words.maxByOrNull { ArenaRules.fillable(it, arena, s, s.playerRack).size }!!
                        val fill = ArenaRules.fillable(word, arena, s, s.playerRack)
                        if (fill.isEmpty()) ArenaRules.pass(s, Side.PLAYER) else ArenaRules.play(arena, s, Side.PLAYER, fill)
                    }
                    val empty = arena.letterCells.filter(s::isEmpty).map(arena::solutionAt).sorted()
                    assertEquals(empty, (s.bag + s.playerRack + s.opponentRack).toList().sorted())
                    assertTrue("${puzzle.id} $difficulty did not finish", ++turns < 500)
                }
            }
        }
    }

    @Test
    fun `hard opponent outscores easy opponent against a passive player`() {
        val puzzle = PuzzleParser().parse(File("src/main/assets/puzzles/levels/hard-01.json").readText(), PuzzleType.LEVEL)
        val arena = ArenaLayout(puzzle)
        fun opponentScore(difficulty: Difficulty): Int = (1L..10L).sumOf { seed ->
            var s = ArenaRules.newMatch(arena, difficulty, seed)
            repeat(12) {
                if (s.finished) return@repeat
                s = ArenaRules.pass(s, Side.PLAYER)
                if (s.finished) return@repeat
                val move = ArenaAi.decide(arena, s)
                s = if (move.isEmpty()) ArenaRules.pass(s, Side.OPPONENT) else ArenaRules.play(arena, s, Side.OPPONENT, move)
                s = s.copy(scorelessTurns = 0)
            }
            s.opponentScore
        }
        assertTrue(opponentScore(Difficulty.HARD) > opponentScore(Difficulty.EASY))
    }
}
