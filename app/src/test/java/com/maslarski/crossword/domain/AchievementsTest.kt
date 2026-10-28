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
    fun `level achievements have exactly the configured thresholds`() {
        val levelAchievements = Achievement.entries.filter {
            it.isLevelMilestone || it == Achievement.HALFWAY_CHAMPION || it == Achievement.ARENA_LEGEND
        }
        assertEquals(
            mapOf(
                Achievement.LEVELS_100 to 100L,
                Achievement.HALFWAY_CHAMPION to 350L,
                Achievement.ARENA_LEGEND to 720L,
            ),
            levelAchievements.associateWith { it.target },
        )
        assertFalse(Achievement.LEVELS_100.isMet(PlayerProfile(levelsSolved = 99)))
        assertEquals(emptySet<Achievement>(), Achievements.met(PlayerProfile(levelsSolved = 99)))
        assertEquals(setOf(Achievement.LEVELS_100), Achievements.met(PlayerProfile(levelsSolved = 100)))
    }

    @Test
    fun `Play Games level achievements use their exact completion thresholds`() {
        assertFalse(Achievement.HALFWAY_CHAMPION.isMet(PlayerProfile(levelsSolved = 349)))
        assertTrue(Achievement.HALFWAY_CHAMPION.isMet(PlayerProfile(levelsSolved = 350)))
        assertFalse(Achievement.ARENA_LEGEND.isMet(PlayerProfile(levelsSolved = 719)))
        assertTrue(Achievement.ARENA_LEGEND.isMet(PlayerProfile(levelsSolved = 720)))
    }

    @Test
    fun `hint free achievements use completed classic levels`() {
        assertFalse(Achievement.PURE_GENIUS.isMet(PlayerProfile(hintFreeLevels = 0)))
        assertTrue(Achievement.PURE_GENIUS.isMet(PlayerProfile(hintFreeLevels = 1)))
        assertFalse(Achievement.FLAWLESS_DECAMERON.isMet(PlayerProfile(hintFreeLevels = 9)))
        assertTrue(Achievement.FLAWLESS_DECAMERON.isMet(PlayerProfile(hintFreeLevels = 10)))
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
