package com.maslarski.crossword.domain.arena

import com.maslarski.crossword.domain.model.Difficulty
import com.maslarski.crossword.domain.repository.ArenaRepository
import com.maslarski.crossword.domain.repository.PuzzleRepository
import javax.inject.Inject
import kotlin.random.Random

/** Deals a new arena match on a random bundled level of the chosen difficulty and stores it. */
class StartArenaMatch @Inject constructor(
    private val puzzles: PuzzleRepository,
    private val arena: ArenaRepository,
) {
    /** Returns the new match, or null when no puzzle is available. */
    suspend operator fun invoke(difficulty: Difficulty, avoidPuzzleId: String? = null, random: Random = Random.Default): ArenaMatch? {
        val levels = puzzles.levels()
        val pool = levels.filter { it.difficulty == difficulty }.ifEmpty { levels }
        val puzzle = (pool.filter { it.id != avoidPuzzleId }.ifEmpty { pool }).randomOrNull(random) ?: return null
        val state = ArenaRules.newMatch(ArenaLayout(puzzle), difficulty, random.nextLong())
        return ArenaMatch(arena.startMatch(state), state)
    }
}
