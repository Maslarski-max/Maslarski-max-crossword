package com.maslarski.crossword.domain

import com.maslarski.crossword.domain.review.ReviewPromptRecord
import com.maslarski.crossword.domain.review.ReviewSchedule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewScheduleTest {

    @Test
    fun firstPromptAfterTwoLevelsOrFirstArenaWin() {
        assertFalse(ReviewSchedule.isDue(levelsSolved = 1, arenaWins = 0, last = null))
        assertTrue(ReviewSchedule.isDue(levelsSolved = 2, arenaWins = 0, last = null))
        assertTrue(ReviewSchedule.isDue(levelsSolved = 0, arenaWins = 1, last = null))
    }

    @Test
    fun repeatsEveryFifteenLevelsAfterTheLastPrompt() {
        val dueAt = mutableListOf<Int>()
        var last: ReviewPromptRecord? = null
        for (levels in 1..50) {
            if (ReviewSchedule.isDue(levels, arenaWins = 0, last = last)) {
                dueAt += levels
                last = ReviewPromptRecord(levels, 0)
            }
        }
        assertTrue(dueAt == listOf(2, 17, 32, 47))
    }

    @Test
    fun repeatsAfterFiveMoreArenaWins() {
        val last = ReviewPromptRecord(levelsSolved = 2, arenaWins = 3)
        assertFalse(ReviewSchedule.isDue(levelsSolved = 16, arenaWins = 7, last = last))
        assertTrue(ReviewSchedule.isDue(levelsSolved = 16, arenaWins = 8, last = last))
        assertTrue(ReviewSchedule.isDue(levelsSolved = 17, arenaWins = 3, last = last))
    }
}
