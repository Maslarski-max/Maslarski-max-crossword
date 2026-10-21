package com.maslarski.crossword.domain.profile

import com.maslarski.crossword.domain.arena.ArenaStats

/** Everything My Stats shows, gathered from the Classic, Daily, Arena, wallet and login records. */
data class PlayerProfile(
    val puzzlesSolved: Int = 0,
    val levelsSolved: Int = 0,
    val hintFreeLevels: Int = 0,
    val dailySolved: Int = 0,
    val dailyStreak: Int = 0,
    val fastestSolveSeconds: Long? = null,
    val coinsEarned: Int = 0,
    val arena: ArenaStats = ArenaStats(),
    val loginStreak: Int = 0,
    val bestLoginStreak: Int = 0,
)

/** Unlockable badges. [target] is compared against [progress]; ids are stored in Room, so never rename them. */
enum class Achievement(val id: String, val target: Long) {
    FIRST_SOLVE("first_solve", 1),
    PUZZLER("puzzler", 10),
    WORD_MASTER("word_master", 50),
    DAILY_DEVOTEE("daily_devotee", 7),
    SPEED_SOLVER("speed_solver", Achievements.SPEED_SOLVER_SECONDS),
    ARENA_CHALLENGER("arena_challenger", 1),
    ARENA_KING("arena_king", 10),
    LOYAL_PLAYER("loyal_player", 7),
    COIN_COLLECTOR("coin_collector", 500),
    LEVELS_100("levels_100", Achievements.LEVEL_MILESTONE_STEP * 1L),
    LEVELS_200("levels_200", Achievements.LEVEL_MILESTONE_STEP * 2L),
    LEVELS_300("levels_300", Achievements.LEVEL_MILESTONE_STEP * 3L),
    LEVELS_400("levels_400", Achievements.LEVEL_MILESTONE_STEP * 4L),
    LEVELS_500("levels_500", Achievements.LEVEL_MILESTONE_STEP * 5L),
    LEVELS_600("levels_600", Achievements.LEVEL_MILESTONE_STEP * 6L),
    LEVELS_700("levels_700", Achievements.LEVEL_MILESTONE_STEP * 7L),
    PURE_GENIUS("pure_genius", 1),
    FLAWLESS_DECAMERON("flawless_decameron", 10),
    ;

    val isLevelMilestone: Boolean get() = id.startsWith("levels_")

    /** Progress towards [target], capped at it; Speed Solver counts down to [target] seconds instead. */
    fun progress(profile: PlayerProfile): Long = when (this) {
        FIRST_SOLVE, PUZZLER, WORD_MASTER -> profile.puzzlesSolved.toLong()
        LEVELS_100, LEVELS_200, LEVELS_300, LEVELS_400, LEVELS_500, LEVELS_600, LEVELS_700 -> profile.levelsSolved.toLong()
        PURE_GENIUS, FLAWLESS_DECAMERON -> profile.hintFreeLevels.toLong()
        DAILY_DEVOTEE -> profile.dailySolved.toLong()
        SPEED_SOLVER -> if (profile.fastestSolveSeconds?.let { it <= target } == true) target else 0
        ARENA_CHALLENGER, ARENA_KING -> profile.arena.wins.toLong()
        LOYAL_PLAYER -> profile.bestLoginStreak.toLong()
        COIN_COLLECTOR -> profile.coinsEarned.toLong()
    }.coerceIn(0, target)

    fun isMet(profile: PlayerProfile): Boolean = progress(profile) >= target

    companion object {
        fun byId(id: String): Achievement? = entries.firstOrNull { it.id == id }
    }
}

object Achievements {
    const val SPEED_SOLVER_SECONDS = 120L
    const val LEVEL_MILESTONE_STEP = 100
    const val LEVEL_MILESTONES = 7

    fun met(profile: PlayerProfile): Set<Achievement> = Achievement.entries.filterTo(LinkedHashSet()) { it.isMet(profile) }
}
