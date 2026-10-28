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
            playGamesAchievementIds(setOf(Achievement.PURE_GENIUS, Achievement.FIRST_SOLVE)),
        )
    }

    @Test
    fun `mapped achievements return their Play Games resources`() {
        assertEquals(
            listOf(
                R.string.achievement_level_100,
                R.string.play_games_achievement_halfway_champion,
                R.string.play_games_achievement_arena_legend,
            ),
            playGamesAchievementIds(
                setOf(
                    Achievement.LEVELS_100,
                    Achievement.HALFWAY_CHAMPION,
                    Achievement.ARENA_LEGEND,
                    Achievement.PURE_GENIUS,
                ),
            ),
        )
    }
}
