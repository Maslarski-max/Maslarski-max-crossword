package com.maslarski.crossword.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.maslarski.crossword.data.local.CrosswordDatabase
import com.maslarski.crossword.data.local.WalletEntity
import com.maslarski.crossword.data.repository.CoinLedger
import com.maslarski.crossword.data.repository.RoomArenaRepository
import com.maslarski.crossword.data.repository.RoomProfileRepository
import com.maslarski.crossword.data.repository.RoomProgressRepository
import com.maslarski.crossword.data.repository.RoomWalletRepository
import com.maslarski.crossword.domain.engine.CrosswordEngine
import com.maslarski.crossword.domain.model.GameSession
import com.maslarski.crossword.domain.model.Puzzle
import com.maslarski.crossword.domain.model.PuzzleType
import com.maslarski.crossword.domain.parser.PuzzleParser
import com.maslarski.crossword.domain.profile.Achievement
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.LocalDate

/** Login bonus, daily calendar and achievements against the real Room schema. */
@RunWith(AndroidJUnit4::class)
class RetentionDatabaseTest {

    private lateinit var db: CrosswordDatabase
    private val ledger = CoinLedger()
    private lateinit var wallet: RoomWalletRepository
    private lateinit var progress: RoomProgressRepository
    private lateinit var profile: RoomProfileRepository
    private val day = LocalDate.of(2026, 10, 2)

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), CrosswordDatabase::class.java).build()
        db.walletDao().insertIgnore(WalletEntity(coins = 0, lifetimeEarned = 0))
        wallet = RoomWalletRepository(db.walletDao(), ledger)
        progress = RoomProgressRepository(db, Clock.systemUTC(), ledger)
        profile = RoomProfileRepository(db, RoomArenaRepository(db, Clock.systemUTC(), ledger), Clock.systemUTC(), ledger)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun coins() = wallet.observeCoins().first()

    @Test
    fun loginBonusIsPaidOncePerDayEvenWhenClaimedConcurrently() = runBlocking {
        val rewards = (1..5).map { async { profile.claimDailyLogin(day) } }.awaitAll().filterNotNull()
        assertEquals(1, rewards.size)
        assertEquals(5, rewards.single().coins)
        assertEquals(5, coins())
        assertNull(profile.claimDailyLogin(day))
        assertEquals(5, profile.observeProfile(day).first().coinsEarned)
    }

    @Test
    fun loginStreakClimbsAndRestartsAfterAMissedDay() = runBlocking {
        assertEquals(listOf(5, 10, 15), (0L..2L).map { profile.claimDailyLogin(day.plusDays(it))!!.coins })
        assertEquals(3, profile.observeProfile(day.plusDays(2)).first().loginStreak)
        assertEquals(0, profile.observeProfile(day.plusDays(4)).first().loginStreak)

        val restarted = profile.claimDailyLogin(day.plusDays(4))!!
        assertEquals(1, restarted.streak)
        assertEquals(5, restarted.coins)
        assertEquals(35, coins())
        assertEquals(3, profile.observeProfile(day.plusDays(4)).first().bestLoginStreak)
    }

    @Test
    fun dailyCompletionsAreReportedPerMonth() = runBlocking {
        val puzzle = mini("daily-x")
        listOf(LocalDate.of(2026, 9, 30), LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 2)).forEach { date ->
            progress.recordCompletion(GameSession.Daily(date, puzzle.id), puzzle, CrosswordEngine.newBoard(puzzle), 40, 0, null)
        }
        val october = progress.observeDailyCompletions(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)).first()
        assertEquals(setOf(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 2)), october)
        val stats = profile.observeProfile(day).first()
        assertEquals(3, stats.dailySolved)
        assertEquals(3, stats.dailyStreak)
        assertEquals(3, stats.puzzlesSolved)
        assertEquals(40L, stats.fastestSolveSeconds)
    }

    @Test
    fun achievementsUnlockOnceAndPersist() = runBlocking {
        val puzzle = mini("l1")
        progress.recordCompletion(GameSession.Level(puzzle.id), puzzle, CrosswordEngine.newBoard(puzzle), 30, 0, null)
        val stats = profile.observeProfile(day).first()

        assertEquals(listOf(Achievement.FIRST_SOLVE, Achievement.SPEED_SOLVER), profile.unlockMet(stats))
        assertTrue(profile.unlockMet(stats).isEmpty())
        assertEquals(setOf(Achievement.FIRST_SOLVE, Achievement.SPEED_SOLVER), profile.observeUnlocked().first().keys)
    }

    private fun mini(id: String): Puzzle = PuzzleParser().parse(
        """
        {
          "id": "$id",
          "title": "Mini",
          "difficulty": "EASY",
          "order": 1,
          "grid": ["CAT", "A#O", "BOW"],
          "clues": {
            "across": { "1": "Feline", "3": "Archer's weapon" },
            "down": { "1": "Taxi", "2": "Pull a car" }
          }
        }
        """,
        PuzzleType.LEVEL,
    )
}
