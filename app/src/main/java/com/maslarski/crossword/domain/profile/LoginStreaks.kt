package com.maslarski.crossword.domain.profile

import java.time.LocalDate

/** Consecutive calendar days the app was opened. [lastDay] is the day the latest reward was claimed. */
data class LoginStreak(val lastDay: LocalDate, val streak: Int, val bestStreak: Int, val totalDays: Int)

/** A daily login bonus that was just paid out. [cycleDay] is 1..7 within the weekly reward track. */
data class LoginReward(val streak: Int, val cycleDay: Int, val coins: Int)

object LoginStreaks {
    /** Coins for day 1..7 of a streak; the track repeats every week while the streak continues. */
    val REWARDS = listOf(5, 10, 15, 20, 25, 30, 50)

    fun cycleDay(streak: Int): Int = Math.floorMod(streak - 1, REWARDS.size) + 1

    fun reward(streak: Int): Int = REWARDS[cycleDay(streak) - 1]

    /**
     * The streak after opening the app on [today], or null if today's bonus was already claimed. Opening the day
     * after [LoginStreak.lastDay] extends the streak; a missed day restarts it at day 1. A clock set back before the
     * last claim pays nothing, so changing the date can't farm rewards.
     */
    fun next(previous: LoginStreak?, today: LocalDate): LoginStreak? {
        if (previous == null) return LoginStreak(today, streak = 1, bestStreak = 1, totalDays = 1)
        if (!today.isAfter(previous.lastDay)) return null
        val streak = if (previous.lastDay.plusDays(1) == today) previous.streak + 1 else 1
        return LoginStreak(today, streak, maxOf(previous.bestStreak, streak), previous.totalDays + 1)
    }
}
