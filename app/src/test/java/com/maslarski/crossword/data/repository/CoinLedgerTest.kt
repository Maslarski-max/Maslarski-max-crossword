package com.maslarski.crossword.data.repository

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class CoinLedgerTest {

    private fun CoinLedger.deltas() = pending.value.map { it.delta }

    @Test
    fun keepsEveryChangeInOrderUntilAcknowledgedAndSkipsZero() = runTest {
        val ledger = CoinLedger()
        ledger.commit<Int>({ -10 }) { it }
        ledger.commit<Int>({ 0 }) { it }
        ledger.commit<Int>({ 10 }) { it }
        ledger.commit<Int>({ 20 }) { it }
        assertEquals(listOf(-10, 10, 20), ledger.deltas())

        ledger.acknowledge(ledger.pending.value.first().id)
        assertEquals(listOf(10, 20), ledger.deltas())
    }

    @Test
    fun queueOrderFollowsCommitOrderWhenAnEarlierWriteIsSlow() = runTest {
        val ledger = CoinLedger()
        launch { ledger.commit<Int>({ delay(100); -10 }) { it } }
        launch { ledger.commit<Int>({ 20 }) { it } }
        testScheduler.advanceUntilIdle()
        assertEquals(listOf(-10, 20), ledger.deltas())
        assertEquals(ledger.pending.value.map { it.id }.sorted(), ledger.pending.value.map { it.id })
    }

    @Test
    fun dropsTheOldestChangesBeyondCapacity() = runTest {
        val ledger = CoinLedger(capacity = 2)
        listOf(1, 2, 3).forEach { delta -> ledger.commit<Int>({ delta }) { it } }
        assertEquals(listOf(2, 3), ledger.deltas())
    }
}
