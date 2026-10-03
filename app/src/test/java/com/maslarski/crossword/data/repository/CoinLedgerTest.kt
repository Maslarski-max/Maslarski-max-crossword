package com.maslarski.crossword.data.repository

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class CoinLedgerTest {

    @Test
    fun replaysEveryChangeInOrderAndSkipsZero() = runTest {
        val ledger = CoinLedger()
        val seen = async(start = CoroutineStart.UNDISPATCHED) { ledger.observe().take(3).toList() }
        ledger.record(-10)
        ledger.record(0)
        ledger.record(10)
        ledger.record(20)
        assertEquals(listOf(-10, 10, 20), seen.await())
    }
}
