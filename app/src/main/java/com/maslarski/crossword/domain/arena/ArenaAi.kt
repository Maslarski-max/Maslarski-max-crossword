package com.maslarski.crossword.domain.arena

import com.maslarski.crossword.domain.model.Difficulty
import kotlin.random.Random

/**
 * Simulated opponent. It "knows" an answer with a probability that grows as the word fills up, then plays the tiles
 * it holds for one known word. Harder opponents know more answers, prefer higher-scoring words and blunder less.
 * Decisions are seeded by the match seed and turn number, so a resumed match replays the same move.
 */
object ArenaAi {

    private class Skill(val knowledge: Double, val greed: Double, val mistakeRate: Double)

    private fun skill(difficulty: Difficulty) = when (difficulty) {
        Difficulty.EASY -> Skill(knowledge = 0.3, greed = 0.2, mistakeRate = 0.12)
        Difficulty.MEDIUM -> Skill(knowledge = 0.5, greed = 0.6, mistakeRate = 0.07)
        Difficulty.HARD -> Skill(knowledge = 0.72, greed = 1.0, mistakeRate = 0.03)
    }

    /** Placements (cell to rack slot) for the opponent's turn; empty means pass. */
    fun decide(layout: ArenaLayout, state: ArenaState): Map<Int, Int> {
        val rack = state.opponentRack
        if (state.finished || state.turn != Side.OPPONENT || rack.isEmpty()) return emptyMap()
        val random = Random(state.seed * 31 + state.turnNumber)
        val skill = skill(state.difficulty)

        if (random.nextDouble() < skill.mistakeRate) {
            blunder(layout, state, rack, random)?.let { return it }
        }

        val options = layout.words.mapNotNull { word ->
            val filled = word.cells.count { !state.isEmpty(it) }
            if (filled == word.cells.size) return@mapNotNull null
            val chance = skill.knowledge + (1 - skill.knowledge) * 0.8 * filled / word.cells.size
            if (random.nextDouble() >= chance) return@mapNotNull null
            val fill = ArenaRules.fillable(word, layout, state, rack)
            if (fill.isEmpty()) null else fill to ArenaRules.potential(word, state, fill)
        }.sortedByDescending { it.second }
        if (options.isEmpty()) return emptyMap()

        val pick = if (random.nextDouble() < skill.greed) 0 else random.nextInt(options.size)
        return options[pick].first
    }

    /** A single wrong tile in some empty cell, or null if every tile happens to fit every empty cell. */
    private fun blunder(layout: ArenaLayout, state: ArenaState, rack: String, random: Random): Map<Int, Int>? {
        val empties = layout.letterCells.filter(state::isEmpty).shuffled(random)
        for (cell in empties) {
            val slot = rack.indices.shuffled(random).firstOrNull { rack[it] != layout.solutionAt(cell) } ?: continue
            return mapOf(cell to slot)
        }
        return null
    }
}
