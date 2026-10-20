package com.maslarski.crossword.domain.review

/** Player totals when the In-App Review flow was last requested. */
data class ReviewPromptRecord(val levelsSolved: Int, val arenaWins: Int)

/**
 * When to ask for a Play review: first after 2 Classic levels or the first Arena win, then again after
 * every 15 more levels or 5 more Arena wins. Play never reports whether the player rated or dismissed
 * the sheet, so every request starts the next interval; Play's own quota can still hide the sheet.
 */
object ReviewSchedule {
    const val FIRST_LEVELS = 2
    const val FIRST_ARENA_WINS = 1
    const val LEVEL_INTERVAL = 15
    const val ARENA_WIN_INTERVAL = 5

    fun isDue(levelsSolved: Int, arenaWins: Int, last: ReviewPromptRecord?): Boolean =
        if (last == null) {
            levelsSolved >= FIRST_LEVELS || arenaWins >= FIRST_ARENA_WINS
        } else {
            levelsSolved >= last.levelsSolved + LEVEL_INTERVAL || arenaWins >= last.arenaWins + ARENA_WIN_INTERVAL
        }
}
