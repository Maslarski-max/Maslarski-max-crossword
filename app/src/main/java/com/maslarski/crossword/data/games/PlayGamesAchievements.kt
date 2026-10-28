package com.maslarski.crossword.data.games

import com.maslarski.crossword.R
import com.maslarski.crossword.domain.profile.Achievement

/** Local achievements that also exist in Play Games, mapped to their Play Games ID string resource. */
internal val PLAY_GAMES_ACHIEVEMENTS: Map<Achievement, Int> =
    mapOf(
        Achievement.LEVELS_100 to R.string.achievement_level_100,
        Achievement.HALFWAY_CHAMPION to R.string.play_games_achievement_halfway_champion,
        Achievement.ARENA_LEGEND to R.string.play_games_achievement_arena_legend,
    )

internal fun playGamesAchievementIds(unlocked: Set<Achievement>): List<Int> =
    PLAY_GAMES_ACHIEVEMENTS.filterKeys { it in unlocked }.values.toList()
