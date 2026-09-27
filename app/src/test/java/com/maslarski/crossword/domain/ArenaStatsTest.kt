package com.maslarski.crossword.domain

import com.maslarski.crossword.domain.arena.ArenaOutcome
import com.maslarski.crossword.domain.arena.ArenaResultRecord
import com.maslarski.crossword.domain.arena.ArenaRules
import com.maslarski.crossword.domain.arena.ArenaStats
import com.maslarski.crossword.domain.model.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Test

class ArenaStatsTest {

    private fun r(outcome: ArenaOutcome, difficulty: Difficulty = Difficulty.EASY, score: Int = 10) =
        ArenaResultRecord(difficulty, outcome, score)

    @Test
    fun `no matches leaves only easy unlocked`() {
        assertEquals(ArenaStats(), ArenaStats.from(emptyList()))
    }

    @Test
    fun `counts results, streaks and forfeits as losses`() {
        val stats = ArenaStats.from(
            listOf(
                r(ArenaOutcome.WON, score = 30),
                r(ArenaOutcome.WON),
                r(ArenaOutcome.FORFEIT),
                r(ArenaOutcome.WON),
                r(ArenaOutcome.WON),
                r(ArenaOutcome.WON, score = 44),
                r(ArenaOutcome.DRAW),
                r(ArenaOutcome.LOST),
            ),
        )
        assertEquals(8, stats.played)
        assertEquals(5, stats.wins)
        assertEquals(2, stats.losses)
        assertEquals(1, stats.draws)
        assertEquals(44, stats.bestScore)
        assertEquals(2, stats.currentStreak)
        assertEquals(3, stats.bestStreak)
    }

    @Test
    fun `beating a tier unlocks the next`() {
        assertEquals(setOf(Difficulty.EASY), ArenaStats.from(listOf(r(ArenaOutcome.LOST))).unlocked)
        assertEquals(setOf(Difficulty.EASY, Difficulty.MEDIUM), ArenaStats.from(listOf(r(ArenaOutcome.WON))).unlocked)
        assertEquals(Difficulty.entries.toSet(), ArenaStats.from(listOf(r(ArenaOutcome.WON, Difficulty.MEDIUM))).unlocked)
    }

    @Test
    fun `rewards scale with difficulty and only for wins and draws`() {
        assertEquals(50, ArenaRules.rewardCoins(Difficulty.HARD, ArenaOutcome.WON))
        assertEquals(10, ArenaRules.rewardCoins(Difficulty.EASY, ArenaOutcome.DRAW))
        assertEquals(0, ArenaRules.rewardCoins(Difficulty.EASY, ArenaOutcome.LOST))
    }
}
