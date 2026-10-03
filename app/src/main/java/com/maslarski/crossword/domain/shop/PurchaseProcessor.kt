package com.maslarski.crossword.domain.shop

import com.maslarski.crossword.domain.repository.EntitlementRepository

/** The store calls that finish a purchase. */
interface PurchaseGateway {
    suspend fun consume(token: String): Boolean
    suspend fun acknowledge(token: String): Boolean
}

/**
 * Applies Play purchases to the local wallet and entitlements.
 *
 * Coin packs are credited once per purchase token before the purchase is consumed, so a failed or
 * interrupted consume is retried on the next sync without crediting the coins again. Unlimited Mode
 * follows Play's list of owned purchases, so a refunded purchase removes it.
 */
class PurchaseProcessor(private val entitlements: EntitlementRepository, private val gateway: PurchaseGateway) {

    /** Returns the products newly delivered by [purchase]. */
    suspend fun process(purchase: OwnedPurchase): List<ShopProduct> {
        if (purchase.state != PurchaseState.PURCHASED) return emptyList()
        return purchase.products.filter { product ->
            when (product) {
                ShopProduct.COINS_1000 -> {
                    val credited = entitlements.creditPurchase(
                        purchase.token,
                        product.id,
                        ShopProduct.COIN_PACK_SIZE * purchase.quantity.coerceAtLeast(1),
                    )
                    gateway.consume(purchase.token)
                    credited
                }
                ShopProduct.UNLIMITED -> {
                    entitlements.setUnlimited(true)
                    !purchase.acknowledged && gateway.acknowledge(purchase.token)
                }
            }
        }
    }

    /** Reconciles with the complete list of purchases Play reports as owned. */
    suspend fun sync(owned: List<OwnedPurchase>) {
        entitlements.setUnlimited(
            owned.any { it.state == PurchaseState.PURCHASED && ShopProduct.UNLIMITED in it.products },
        )
        owned.forEach { process(it) }
    }
}
