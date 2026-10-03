package com.maslarski.crossword.data.repository

import androidx.room.withTransaction
import com.maslarski.crossword.data.local.CrosswordDatabase
import com.maslarski.crossword.data.local.EntitlementEntity
import com.maslarski.crossword.data.local.PurchaseCreditEntity
import com.maslarski.crossword.domain.repository.EntitlementRepository
import com.maslarski.crossword.domain.shop.ShopProduct
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.Clock

class RoomEntitlementRepository(
    private val db: CrosswordDatabase,
    private val clock: Clock,
    private val ledger: CoinLedger,
) : EntitlementRepository {
    private val dao = db.entitlementDao()
    private val wallet = db.walletDao()

    override fun observeUnlimited(): Flow<Boolean> =
        dao.observeActive(ShopProduct.UNLIMITED.id).map { it == true }.distinctUntilChanged()

    override suspend fun isUnlimited(): Boolean = dao.isActive(ShopProduct.UNLIMITED.id) == true

    override suspend fun setUnlimited(active: Boolean) {
        if (isUnlimited() == active) return
        dao.upsert(EntitlementEntity(ShopProduct.UNLIMITED.id, active, clock.millis()))
    }

    override suspend fun creditPurchase(token: String, productId: String, coins: Int): Boolean = db.withTransaction {
        val inserted = dao.insertCredit(PurchaseCreditEntity(token, productId, coins, clock.millis())) != -1L
        if (inserted && coins > 0) wallet.depositPurchased(coins)
        inserted
    }.also { if (it) ledger.record(coins) }
}
