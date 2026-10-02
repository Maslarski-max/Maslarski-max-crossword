package com.maslarski.crossword.domain

import com.maslarski.crossword.domain.arena.ArenaStats
import com.maslarski.crossword.domain.profile.Achievement
import com.maslarski.crossword.domain.profile.Achievements
import com.maslarski.crossword.domain.profile.PlayerProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AchievementsTest {

    @Test
    fun `nothing is met by a new player`() {
        assertTrue(Achievements.met(PlayerProfile()).isEmpty())
    }

    @Test
    fun `word master needs fifty solves`() {
        assertFalse(Achievement.WORD_MASTER.isMet(PlayerProfile(puzzlesSolved = 49)))
        assertTrue(Achievement.WORD_MASTER.isMet(PlayerProfile(puzzlesSolved = 50)))
        assertEquals(
            setOf(Achievement.FIRST_SOLVE, Achievement.PUZZLER, Achievement.WORD_MASTER),
            Achievements.met(PlayerProfile(puzzlesSolved = 50)),
        )
    }

    @Test
    fun `arena king needs ten wins`() {
        assertFalse(Achievement.ARENA_KING.isMet(PlayerProfile(arena = ArenaStats(wins = 9))))
        assertTrue(Achievement.ARENA_KING.isMet(PlayerProfile(arena = ArenaStats(wins = 10))))
        assertEquals(9L, Achievement.ARENA_KING.progress(PlayerProfile(arena = ArenaStats(wins = 9))))
    }

    @Test
    fun `progress is capped at the target`() {
        assertEquals(10L, Achievement.PUZZLER.progress(PlayerProfile(puzzlesSolved = 80)))
    }

    @Test
    fun `speed solver needs a solve within two minutes`() {
        assertFalse(Achievement.SPEED_SOLVER.isMet(PlayerProfile(fastestSolveSeconds = null)))
        assertFalse(Achievement.SPEED_SOLVER.isMet(PlayerProfile(fastestSolveSeconds = 121)))
        assertTrue(Achievement.SPEED_SOLVER.isMet(PlayerProfile(fastestSolveSeconds = 120)))
    }

    @Test
    fun `loyal player uses the best login streak and coin collector lifetime earnings`() {
        assertTrue(Achievement.LOYAL_PLAYER.isMet(PlayerProfile(loginStreak = 1, bestLoginStreak = 7)))
        assertTrue(Achievement.COIN_COLLECTOR.isMet(PlayerProfile(coinsEarned = 500)))
        assertFalse(Achievement.COIN_COLLECTOR.isMet(PlayerProfile(coinsEarned = 499)))
    }

    @Test
    fun `ids are unique and resolvable`() {
        assertEquals(Achievement.entries.size, Achievement.entries.map { it.id }.toSet().size)
        Achievement.entries.forEach { assertEquals(it, Achievement.byId(it.id)) }
    }
}
