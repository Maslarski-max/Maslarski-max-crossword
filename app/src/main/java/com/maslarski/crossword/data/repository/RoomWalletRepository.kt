package com.maslarski.crossword.data.repository

import com.maslarski.crossword.data.local.WalletDao
import com.maslarski.crossword.domain.repository.WalletRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomWalletRepository(private val dao: WalletDao) : WalletRepository {
    override fun observeCoins(): Flow<Int> = dao.observeCoins().map { it ?: 0 }

    override suspend fun trySpend(amount: Int): Boolean = amount <= 0 || dao.spend(amount) == 1

    override suspend fun earn(amount: Int) {
        if (amount > 0) dao.earn(amount)
    }

    override suspend fun refund(amount: Int) {
        if (amount > 0) dao.refund(amount)
    }
}
