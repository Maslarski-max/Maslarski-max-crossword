package com.maslarski.crossword.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.maslarski.crossword.data.local.CrosswordDatabase
import com.maslarski.crossword.data.local.WalletEntity
import com.maslarski.crossword.data.repository.RoomArenaRepository
import com.maslarski.crossword.data.repository.RoomProgressRepository
import com.maslarski.crossword.data.repository.RoomWalletRepository
import com.maslarski.crossword.domain.arena.ArenaOutcome
import com.maslarski.crossword.domain.arena.ArenaRules
import com.maslarski.crossword.domain.arena.ArenaState
import com.maslarski.crossword.domain.engine.CrosswordEngine
import com.maslarski.crossword.domain.engine.GameRules
import com.maslarski.crossword.domain.model.Difficulty
import com.maslarski.crossword.domain.model.GameSession
import com.maslarski.crossword.domain.model.Puzzle
import com.maslarski.crossword.domain.model.PuzzleType
import com.maslarski.crossword.domain.model.UnlockResult
import com.maslarski.crossword.domain.parser.PuzzleParser
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock

/** Checks every coin earn/spend path against the real Room schema. */
@RunWith(AndroidJUnit4::class)
class CoinEconomyDatabaseTest {

    private lateinit var db: CrosswordDatabase
    private lateinit var wallet: RoomWalletRepository
    private lateinit var progress: RoomProgressRepository
    private lateinit var arena: RoomArenaRepository
    private val levels = (1..3).map(::level)

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), CrosswordDatabase::class.java).build()
        db.walletDao().insertIgnore(WalletEntity(coins = GameRules.STARTING_COINS, lifetimeEarned = 0))
        wallet = RoomWalletRepository(db.walletDao())
        progress = RoomProgressRepository(db, Clock.systemUTC())
        arena = RoomArenaRepository(db, Clock.systemUTC())
        progress.syncLevels(levels)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun coins() = wallet.observeCoins().first()
    private suspend fun unlocked() = progress.observeLevels().first().filter { it.unlocked }.map { it.puzzleId }

    @Test
    fun onlyTheFirstLevelStartsUnlocked() = runBlocking {
        assertEquals(listOf("l1"), unlocked())
        assertEquals(GameRules.STARTING_COINS, coins())
    }

    @Test
    fun unlockingSpendsFiftyCoinsAndPersistsTheLevel() = runBlocking {
        assertEquals(UnlockResult.UNLOCKED, progress.unlockLevel(levels, "l2"))
        assertEquals(GameRules.STARTING_COINS - GameRules.LEVEL_UNLOCK_COST, coins())
        assertEquals(listOf("l1", "l2"), unlocked())

        assertEquals(UnlockResult.ALREADY_UNLOCKED, progress.unlockLevel(levels, "l2"))
        assertEquals(GameRules.STARTING_COINS - GameRules.LEVEL_UNLOCK_COST, coins())
    }

    @Test
    fun levelsUnlockInOrder() = runBlocking {
        assertEquals(UnlockResult.NOT_NEXT, progress.unlockLevel(levels, "l3"))
        assertEquals(UnlockResult.NOT_NEXT, progress.unlockLevel(levels, "missing"))
        assertEquals(GameRules.STARTING_COINS, coins())
        assertEquals(listOf("l1"), unlocked())
    }

    @Test
    fun unlockWithoutEnoughCoinsChangesNothing() = runBlocking {
        assertTrue(wallet.trySpend(GameRules.STARTING_COINS - 49))
        assertEquals(UnlockResult.NOT_ENOUGH_COINS, progress.unlockLevel(levels, "l2"))
        assertEquals(49, coins())
        assertEquals(listOf("l1"), unlocked())
    }

    @Test
    fun concurrentUnlockTapsChargeOnce() = runBlocking {
        val results = List(5) { async { progress.unlockLevel(levels, "l2") } }.awaitAll()
        assertEquals(1, results.count { it == UnlockResult.UNLOCKED })
        assertEquals(GameRules.STARTING_COINS - GameRules.LEVEL_UNLOCK_COST, coins())
    }

    @Test
    fun solvingALevelPaysButNoLongerUnlocksTheNextOne() = runBlocking {
        val puzzle = levels[0]
        val result = progress.recordCompletion(GameSession.Level(puzzle.id), puzzle, CrosswordEngine.newBoard(puzzle), 30, 0, "l2")
        assertEquals(GameRules.completionCoins(puzzle), result.coinsEarned)
        assertEquals(GameRules.STARTING_COINS + result.coinsEarned, coins())
        assertEquals(listOf("l1"), unlocked())
        assertEquals("l2", result.nextPuzzleId)
    }

    @Test
    fun hintSpendIsAtomicAndRefundable() = runBlocking {
        val spends = List(15) { async { wallet.trySpend(GameRules.HINT_COST) } }.awaitAll()
        assertEquals(GameRules.STARTING_COINS / GameRules.HINT_COST, spends.count { it })
        assertEquals(0, coins())
        assertFalse(wallet.trySpend(GameRules.HINT_COST))

        wallet.refund(GameRules.HINT_COST)
        assertEquals(GameRules.HINT_COST, coins())
        assertEquals(0, db.query("SELECT lifetimeEarned FROM wallet", null).use { it.moveToFirst(); it.getInt(0) })
    }

    @Test
    fun arenaWinPaysTwentyCoinsExactlyOnce() = runBlocking {
        val id = arena.startMatch(arenaState(finished = false))
        val won = arenaState(finished = true, playerScore = 12, opponentScore = 5)
        val result = arena.finishMatch(id, won)
        assertEquals(ArenaOutcome.WON, result?.outcome)
        assertEquals(ArenaRules.WIN_REWARD, result?.coinsEarned)
        assertEquals(GameRules.STARTING_COINS + 20, coins())

        assertNull(arena.finishMatch(id, won))
        assertEquals(GameRules.STARTING_COINS + 20, coins())
    }

    @Test
    fun arenaLossPaysNothing() = runBlocking {
        val id = arena.startMatch(arenaState(finished = false))
        val result = arena.finishMatch(id, arenaState(finished = true, playerScore = 3, opponentScore = 9))
        assertEquals(0, result?.coinsEarned)
        assertEquals(GameRules.STARTING_COINS, coins())
    }

    @Test
    fun boughtArenaHintSurvivesReopeningTheMatch() = runBlocking {
        val id = arena.startMatch(arenaState(finished = false))
        arena.saveMatch(id, arenaState(finished = false).copy(turnNumber = 2, hintTurn = 2))
        val reloaded = arena.loadMatch(id)?.state
        assertEquals(2, reloaded?.hintTurn)
        assertEquals(reloaded?.turnNumber, reloaded?.hintTurn)
    }

    private fun arenaState(finished: Boolean, playerScore: Int = 0, opponentScore: Int = 0) = ArenaState(
        puzzleId = "l1",
        fingerprint = levels[0].fingerprint,
        difficulty = Difficulty.EASY,
        seed = 1,
        owners = "",
        values = "",
        bag = "",
        playerRack = "",
        opponentRack = "",
        playerScore = playerScore,
        opponentScore = opponentScore,
        finished = finished,
    )

    private fun level(order: Int): Puzzle = PuzzleParser().parse(
        """
        {
          "id": "l$order",
          "title": "Level $order",
          "difficulty": "EASY",
          "order": $order,
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
