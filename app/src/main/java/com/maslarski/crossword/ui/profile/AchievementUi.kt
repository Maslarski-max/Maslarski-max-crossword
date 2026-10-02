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
import androidx.compose.ui.graphics.vector.ImageVector
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
    }
