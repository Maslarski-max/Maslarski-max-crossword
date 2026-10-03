package com.maslarski.crossword.data.repository

import com.maslarski.crossword.data.local.WalletDao
import com.maslarski.crossword.domain.model.CoinChange
import com.maslarski.crossword.domain.repository.WalletRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomWalletRepository(private val dao: WalletDao, private val ledger: CoinLedger) : WalletRepository {
    override fun observeCoins(): Flow<Int> = dao.observeCoins().map { it ?: 0 }

    override fun observeChanges(): Flow<List<CoinChange>> = ledger.pending

    override fun acknowledgeChange(id: Long) = ledger.acknowledge(id)

    override suspend fun trySpend(amount: Int): Boolean =
        amount <= 0 || ledger.commit({ dao.spend(amount) == 1 }) { if (it) -amount else 0 }

    override suspend fun earn(amount: Int) {
        if (amount > 0) ledger.commit({ dao.earn(amount) == 1 }) { if (it) amount else 0 }
    }

    override suspend fun refund(amount: Int) {
        if (amount > 0) ledger.commit({ dao.refund(amount) == 1 }) { if (it) amount else 0 }
    }
}
