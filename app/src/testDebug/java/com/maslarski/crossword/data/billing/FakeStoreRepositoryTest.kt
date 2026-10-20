package com.maslarski.crossword.data.billing

import com.maslarski.crossword.domain.repository.EntitlementRepository
import com.maslarski.crossword.domain.shop.ShopEvent
import com.maslarski.crossword.domain.shop.ShopProduct
import com.maslarski.crossword.domain.shop.StoreStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeStoreRepositoryTest {

    private class FakeEntitlements : EntitlementRepository {
        val unlimited = MutableStateFlow(false)
        private val creditedPurchases = mutableSetOf<String>()
        var coins = 0

        override fun observeUnlimited(): Flow<Boolean> = unlimited
        override suspend fun isUnlimited() = unlimited.value
        override suspend fun setUnlimited(active: Boolean) {
            unlimited.value = active
        }

        override suspend fun creditPurchase(token: String, productId: String, coins: Int): Boolean {
            if (!creditedPurchases.add(token)) return false
            this.coins += coins
            return true
        }
    }

    @Test
    fun `two coin purchases credit 2000 coins and emit delivered events`() = runTest {
        val entitlements = FakeEntitlements()
        val store = FakeStoreRepository(entitlements, this)

        store.processPurchase(ShopProduct.COINS_1000)
        store.processPurchase(ShopProduct.COINS_1000)

        assertEquals(2_000, entitlements.coins)
        assertEquals(
            listOf(
                ShopEvent.Delivered(ShopProduct.COINS_1000),
                ShopEvent.Delivered(ShopProduct.COINS_1000),
            ),
            store.events.take(2).toList(),
        )
    }

    @Test
    fun `unlimited purchase is delivered once and then reports already owned`() = runTest {
        val entitlements = FakeEntitlements()
        val store = FakeStoreRepository(entitlements, this)

        store.processPurchase(ShopProduct.UNLIMITED)
        store.processPurchase(ShopProduct.UNLIMITED)

        assertTrue(entitlements.unlimited.value)
        assertEquals(
            listOf(ShopEvent.Delivered(ShopProduct.UNLIMITED), ShopEvent.AlreadyOwned),
            store.events.take(2).toList(),
        )
    }

    @Test
    fun `refresh leaves a purchased unlimited entitlement enabled`() = runTest {
        val entitlements = FakeEntitlements()
        val store = FakeStoreRepository(entitlements, this)
        store.processPurchase(ShopProduct.UNLIMITED)

        store.refresh()

        assertTrue(entitlements.unlimited.value)
    }

    @Test
    fun `status and listings are ready with test prices`() = runTest {
        val store = FakeStoreRepository(FakeEntitlements(), this)

        assertEquals(StoreStatus.READY, store.status.value)
        assertEquals(
            listOf("1,000 Coins (test)", "Unlimited Mode (test)"),
            store.listings.value.map { it.title },
        )
        assertEquals(
            listOf("$0.99", "$4.99"),
            store.listings.value.map { it.price },
        )
    }
}
