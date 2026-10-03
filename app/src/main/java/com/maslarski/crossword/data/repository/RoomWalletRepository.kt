package com.maslarski.crossword.data.repository

import com.maslarski.crossword.data.local.WalletDao
import com.maslarski.crossword.domain.repository.WalletRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomWalletRepository(private val dao: WalletDao, private val ledger: CoinLedger) : WalletRepository {
    override fun observeCoins(): Flow<Int> = dao.observeCoins().map { it ?: 0 }

    override fun observeChanges(): Flow<Int> = ledger.observe()

    override suspend fun trySpend(amount: Int): Boolean {
        if (amount <= 0) return true
        if (dao.spend(amount) != 1) return false
        ledger.record(-amount)
        return true
    }

    override suspend fun earn(amount: Int) {
        if (amount > 0 && dao.earn(amount) == 1) ledger.record(amount)
    }

    override suspend fun refund(amount: Int) {
        if (amount > 0 && dao.refund(amount) == 1) ledger.record(amount)
    }
}
