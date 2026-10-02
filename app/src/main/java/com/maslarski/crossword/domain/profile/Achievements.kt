package com.maslarski.crossword.domain.profile

import com.maslarski.crossword.domain.arena.ArenaStats

/** Everything My Stats shows, gathered from the Classic, Daily, Arena, wallet and login records. */
data class PlayerProfile(
    val puzzlesSolved: Int = 0,
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
    ;

    /** Progress towards [target], capped at it; Speed Solver counts down to [target] seconds instead. */
    fun progress(profile: PlayerProfile): Long = when (this) {
        FIRST_SOLVE, PUZZLER, WORD_MASTER -> profile.puzzlesSolved.toLong()
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

    fun met(profile: PlayerProfile): Set<Achievement> = Achievement.entries.filterTo(LinkedHashSet()) { it.isMet(profile) }
}
