package com.maslarski.crossword.data.repository

import androidx.room.withTransaction
import com.maslarski.crossword.data.local.ArenaMatchEntity
import com.maslarski.crossword.data.local.CrosswordDatabase
import com.maslarski.crossword.domain.arena.ArenaMatch
import com.maslarski.crossword.domain.arena.ArenaOutcome
import com.maslarski.crossword.domain.arena.ArenaResult
import com.maslarski.crossword.domain.arena.ArenaResultRecord
import com.maslarski.crossword.domain.arena.ArenaRules
import com.maslarski.crossword.domain.arena.ArenaState
import com.maslarski.crossword.domain.arena.ArenaStats
import com.maslarski.crossword.domain.model.Difficulty
import com.maslarski.crossword.domain.repository.ArenaRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.time.Clock

class RoomArenaRepository(
    private val db: CrosswordDatabase,
    private val clock: Clock,
    private val ledger: CoinLedger,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : ArenaRepository {

    private val matches = db.arenaMatchDao()
    private val wallet = db.walletDao()

    /** Keeps saves of one screen ordered ahead of a later load, finish or new match. */
    private val lock = Mutex()

    override fun observeStats(): Flow<ArenaStats> = matches.observeResults().map { rows ->
        ArenaStats.from(
            rows.mapNotNull { row ->
                val difficulty = Difficulty.entries.firstOrNull { it.name == row.difficulty } ?: return@mapNotNull null
                val outcome = ArenaOutcome.entries.firstOrNull { it.name == row.outcome } ?: return@mapNotNull null
                ArenaResultRecord(difficulty, outcome, row.playerScore)
            },
        )
    }

    override fun observeActiveMatch(): Flow<ArenaMatch?> = matches.observeActive().map { it?.toMatch() }

    override suspend fun startMatch(state: ArenaState): Long = lock.withLock {
        ledger.commit({
            db.withTransaction {
                val now = clock.millis()
                val forfeited = matches.activeIds()
                matches.closeActive(ArenaOutcome.FORFEIT.name, now)
                val lost = if (forfeited.isNotEmpty()) chargePenalty(ArenaOutcome.FORFEIT) else 0
                forfeited.forEach { matches.recordCoins(it, earned = 0, lost = lost) }
                matches.insert(
                    ArenaMatchEntity(
                        puzzleId = state.puzzleId,
                        difficulty = state.difficulty.name,
                        state = json.encodeToString(ArenaState.serializer(), state),
                        playerScore = state.playerScore,
                        opponentScore = state.opponentScore,
                        outcome = null,
                        startedAt = now,
                        updatedAt = now,
                        finishedAt = null,
                    ),
                ) to lost
            }
        }) { -it.second }.first
    }

    override suspend fun loadMatch(id: Long): ArenaMatch? = lock.withLock { matches.get(id)?.toMatch() }

    override suspend fun saveMatch(id: Long, state: ArenaState) {
        lock.withLock {
            matches.updateState(id, json.encodeToString(ArenaState.serializer(), state), state.playerScore, state.opponentScore, clock.millis())
        }
    }

    override suspend fun discardMatch(id: Long) {
        lock.withLock { matches.deleteActive(id) }
    }

    override suspend fun finishMatch(id: Long, state: ArenaState): ArenaResult? = lock.withLock {
        val outcome = state.outcome ?: return@withLock null
        ledger.commit({
            db.withTransaction {
                val updated = matches.finish(
                    id = id,
                    state = json.encodeToString(ArenaState.serializer(), state),
                    playerScore = state.playerScore,
                    opponentScore = state.opponentScore,
                    outcome = outcome.name,
                    now = clock.millis(),
                )
                if (updated == 0) return@withTransaction null
                val coins = ArenaRules.rewardCoins(outcome)
                if (coins > 0) wallet.earn(coins)
                val lost = chargePenalty(outcome)
                matches.recordCoins(id, earned = coins, lost = lost)
                ArenaResult(outcome, state.playerScore, state.opponentScore, coins, lost)
            }
        }) { it?.let { r -> r.coinsEarned - r.coinsLost } ?: 0 }
    }

    /** Deducts the loss penalty inside the caller's transaction and returns the coins actually taken. */
    private suspend fun chargePenalty(outcome: ArenaOutcome): Int {
        val penalty = ArenaRules.penaltyCoins(outcome, wallet.coins() ?: 0)
        return if (penalty > 0 && wallet.spend(penalty) == 1) penalty else 0
    }

    private fun ArenaMatchEntity.toMatch(): ArenaMatch? = try {
        ArenaMatch(id, ArenaRules.topUpRacks(json.decodeFromString(ArenaState.serializer(), state)), coinsEarned, coinsLost)
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
}
