package com.maslarski.crossword.domain

import com.maslarski.crossword.domain.profile.LoginStreak
import com.maslarski.crossword.domain.profile.LoginStreaks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class LoginStreaksTest {

    private val day = LocalDate.of(2026, 10, 2)

    @Test
    fun `first open starts a one day streak`() {
        assertEquals(LoginStreak(day, 1, 1, 1), LoginStreaks.next(null, day))
    }

    @Test
    fun `same day pays nothing`() {
        assertNull(LoginStreaks.next(LoginStreak(day, 3, 3, 3), day))
    }

    @Test
    fun `clock set back pays nothing`() {
        assertNull(LoginStreaks.next(LoginStreak(day, 3, 3, 3), day.minusDays(2)))
    }

    @Test
    fun `next day extends the streak`() {
        assertEquals(LoginStreak(day.plusDays(1), 4, 4, 4), LoginStreaks.next(LoginStreak(day, 3, 3, 3), day.plusDays(1)))
    }

    @Test
    fun `missed day restarts at day one and keeps the best`() {
        assertEquals(LoginStreak(day.plusDays(2), 1, 5, 6), LoginStreaks.next(LoginStreak(day, 5, 5, 5), day.plusDays(2)))
    }

    @Test
    fun `rewards climb to day seven then repeat`() {
        assertEquals(listOf(5, 10, 15, 20, 25, 30, 50, 5, 10), (1..9).map(LoginStreaks::reward))
        assertEquals(listOf(1, 7, 1, 7), listOf(1, 7, 8, 14).map(LoginStreaks::cycleDay))
    }
}
