package com.maslarski.crossword.domain

import com.maslarski.crossword.domain.repository.EntitlementRepository
import com.maslarski.crossword.domain.shop.OwnedPurchase
import com.maslarski.crossword.domain.shop.PurchaseGateway
import com.maslarski.crossword.domain.shop.PurchaseProcessor
import com.maslarski.crossword.domain.shop.PurchaseState
import com.maslarski.crossword.domain.shop.ShopProduct
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PurchaseProcessorTest {

    private class FakeEntitlements : EntitlementRepository {
        val unlimited = MutableStateFlow(false)
        val credited = mutableSetOf<String>()
        var coins = 0
        override fun observeUnlimited(): Flow<Boolean> = unlimited
        override suspend fun isUnlimited() = unlimited.value
        override suspend fun setUnlimited(active: Boolean) { unlimited.value = active }
        override suspend fun creditPurchase(token: String, productId: String, coins: Int): Boolean {
            if (!credited.add(token)) return false
            this.coins += coins
            return true
        }
    }

    private class FakeGateway(var consumeOk: Boolean = true) : PurchaseGateway {
        val consumed = mutableListOf<String>()
        val acknowledged = mutableListOf<String>()
        override suspend fun consume(token: String) = consumeOk.also { if (it) consumed += token }
        override suspend fun acknowledge(token: String) = true.also { acknowledged += token }
    }

    private val entitlements = FakeEntitlements()
    private val gateway = FakeGateway()
    private val processor = PurchaseProcessor(entitlements, gateway)

    private fun coins(token: String, state: PurchaseState = PurchaseState.PURCHASED) =
        OwnedPurchase(token, listOf(ShopProduct.COINS_1000.id), state, acknowledged = false)

    private fun unlimited(acknowledged: Boolean = false) =
        OwnedPurchase("u1", listOf(ShopProduct.UNLIMITED.id), PurchaseState.PURCHASED, acknowledged)

    @Test
    fun `price tiers match the store configuration`() {
        assertEquals("$0.99", ShopProduct.COINS_1000.usdPrice)
        assertEquals("$4.99", ShopProduct.UNLIMITED.usdPrice)
    }

    @Test
    fun `coin pack credits 1000 coins once and is consumed`() = runTest {
        assertEquals(listOf(ShopProduct.COINS_1000), processor.process(coins("t1")))
        assertEquals(emptyList<ShopProduct>(), processor.process(coins("t1")))
        assertEquals(1000, entitlements.coins)
        assertEquals(listOf("t1", "t1"), gateway.consumed)
    }

    @Test
    fun `failed consume is retried on sync without crediting again`() = runTest {
        gateway.consumeOk = false
        processor.process(coins("t1"))
        gateway.consumeOk = true
        processor.sync(listOf(coins("t1")))
        assertEquals(1000, entitlements.coins)
        assertEquals(listOf("t1"), gateway.consumed)
    }

    @Test
    fun `pending purchases deliver nothing`() = runTest {
        assertTrue(processor.process(coins("t1", PurchaseState.PENDING)).isEmpty())
        assertEquals(0, entitlements.coins)
        assertTrue(gateway.consumed.isEmpty())
    }

    @Test
    fun `unlimited is granted, acknowledged once and revoked when no longer owned`() = runTest {
        assertEquals(listOf(ShopProduct.UNLIMITED), processor.process(unlimited()))
        assertTrue(entitlements.unlimited.value)
        processor.sync(listOf(unlimited(acknowledged = true)))
        assertEquals(listOf("u1"), gateway.acknowledged)
        assertTrue(entitlements.unlimited.value)
        processor.sync(emptyList())
        assertFalse(entitlements.unlimited.value)
    }
}
