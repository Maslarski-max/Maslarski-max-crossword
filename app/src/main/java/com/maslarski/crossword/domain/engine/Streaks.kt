package com.maslarski.crossword.domain.engine

import java.time.LocalDate

object Streaks {
    /** Consecutive solved days ending today, or yesterday if today's puzzle isn't solved yet. */
    fun current(solvedDates: Collection<LocalDate>, today: LocalDate): Int {
        val solved = solvedDates.toSet()
        var day = if (today in solved) today else today.minusDays(1)
        var streak = 0
        while (day in solved) {
            streak++
            day = day.minusDays(1)
        }
        return streak
    }
}
