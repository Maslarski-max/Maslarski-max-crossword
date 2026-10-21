package com.maslarski.crossword.ui.profile

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MilitaryTech
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Toll
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.maslarski.crossword.R
import com.maslarski.crossword.domain.profile.Achievement

val Achievement.icon: ImageVector
    get() = when (this) {
        Achievement.FIRST_SOLVE -> Icons.Rounded.TaskAlt
        Achievement.PUZZLER -> Icons.Rounded.AutoStories
        Achievement.WORD_MASTER -> Icons.Rounded.MilitaryTech
        Achievement.DAILY_DEVOTEE -> Icons.Rounded.CalendarMonth
        Achievement.SPEED_SOLVER -> Icons.Rounded.Bolt
        Achievement.ARENA_CHALLENGER -> Icons.Rounded.SportsEsports
        Achievement.ARENA_KING -> Icons.Rounded.EmojiEvents
        Achievement.LOYAL_PLAYER -> Icons.Rounded.LocalFireDepartment
        Achievement.COIN_COLLECTOR -> Icons.Rounded.Toll
        Achievement.LEVELS_100,
        Achievement.LEVELS_200,
        Achievement.LEVELS_300,
        Achievement.LEVELS_400,
        Achievement.LEVELS_500,
        Achievement.LEVELS_600,
        Achievement.LEVELS_700 -> Icons.Rounded.EmojiEvents
        Achievement.PURE_GENIUS -> Icons.Rounded.Bolt
        Achievement.FLAWLESS_DECAMERON -> Icons.Rounded.TaskAlt
    }

@get:StringRes
val Achievement.titleRes: Int
    get() = when (this) {
        Achievement.FIRST_SOLVE -> R.string.achievement_first_solve
        Achievement.PUZZLER -> R.string.achievement_puzzler
        Achievement.WORD_MASTER -> R.string.achievement_word_master
        Achievement.DAILY_DEVOTEE -> R.string.achievement_daily_devotee
        Achievement.SPEED_SOLVER -> R.string.achievement_speed_solver
        Achievement.ARENA_CHALLENGER -> R.string.achievement_arena_challenger
        Achievement.ARENA_KING -> R.string.achievement_arena_king
        Achievement.LOYAL_PLAYER -> R.string.achievement_loyal_player
        Achievement.COIN_COLLECTOR -> R.string.achievement_coin_collector
        Achievement.LEVELS_100,
        Achievement.LEVELS_200,
        Achievement.LEVELS_300,
        Achievement.LEVELS_400,
        Achievement.LEVELS_500,
        Achievement.LEVELS_600,
        Achievement.LEVELS_700 -> R.string.achievement_level_milestone
        Achievement.PURE_GENIUS -> R.string.achievement_pure_genius
        Achievement.FLAWLESS_DECAMERON -> R.string.achievement_flawless_decameron
    }

@get:StringRes
val Achievement.descriptionRes: Int
    get() = when (this) {
        Achievement.FIRST_SOLVE -> R.string.achievement_first_solve_desc
        Achievement.PUZZLER -> R.string.achievement_puzzler_desc
        Achievement.WORD_MASTER -> R.string.achievement_word_master_desc
        Achievement.DAILY_DEVOTEE -> R.string.achievement_daily_devotee_desc
        Achievement.SPEED_SOLVER -> R.string.achievement_speed_solver_desc
        Achievement.ARENA_CHALLENGER -> R.string.achievement_arena_challenger_desc
        Achievement.ARENA_KING -> R.string.achievement_arena_king_desc
        Achievement.LOYAL_PLAYER -> R.string.achievement_loyal_player_desc
        Achievement.COIN_COLLECTOR -> R.string.achievement_coin_collector_desc
        Achievement.LEVELS_100,
        Achievement.LEVELS_200,
        Achievement.LEVELS_300,
        Achievement.LEVELS_400,
        Achievement.LEVELS_500,
        Achievement.LEVELS_600,
        Achievement.LEVELS_700 -> R.string.achievement_level_milestone_desc
        Achievement.PURE_GENIUS -> R.string.achievement_pure_genius_desc
        Achievement.FLAWLESS_DECAMERON -> R.string.achievement_flawless_decameron_desc
    }

@Composable
fun Achievement.titleText(): String =
    if (isLevelMilestone) stringResource(titleRes, target.toInt()) else stringResource(titleRes)

@Composable
fun Achievement.descriptionText(): String =
    if (isLevelMilestone) stringResource(descriptionRes, target.toInt()) else stringResource(descriptionRes)
