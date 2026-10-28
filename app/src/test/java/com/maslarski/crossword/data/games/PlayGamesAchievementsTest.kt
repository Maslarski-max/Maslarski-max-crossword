package com.maslarski.crossword.data.games

import com.maslarski.crossword.R
import com.maslarski.crossword.domain.profile.Achievement
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayGamesAchievementsTest {

    @Test
    fun `empty unlocked set has no Play Games achievements`() {
        assertEquals(emptyList<Int>(), playGamesAchievementIds(emptySet()))
    }

    @Test
    fun `achievements without Play Games mappings are ignored`() {
        assertEquals(
            emptyList<Int>(),
            playGamesAchievementIds(setOf(Achievement.LEVELS_200, Achievement.PURE_GENIUS)),
        )
    }

    @Test
    fun `Level 100 maps to its Play Games resource`() {
        assertEquals(
            listOf(R.string.achievement_level_100),
            playGamesAchievementIds(setOf(Achievement.LEVELS_100, Achievement.LEVELS_200)),
        )
    }
}
