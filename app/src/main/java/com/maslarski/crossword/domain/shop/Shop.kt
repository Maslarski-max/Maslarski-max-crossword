package com.maslarski.crossword.domain.shop

/**
 * In-app products. [usdPriceMicros] is the US price tier the product must have in Play Console; Play
 * charges the price configured there, and the shop shows Play's localized price once it is loaded.
 */
enum class ShopProduct(val id: String, val consumable: Boolean, val usdPriceMicros: Long) {
    /** Consumable: every purchase adds [COIN_PACK_SIZE] coins. */
    COINS_1000("crossword_coins_1000", consumable = true, usdPriceMicros = 990_000),

    /** Non-consumable: free hints and every Classic level open. */
    UNLIMITED("crossword_unlimited_mode", consumable = false, usdPriceMicros = 4_990_000),
    ;

    /** [usdPriceMicros] as a label, for example "$0.99". */
    val usdPrice: String
        get() = "$%d.%02d".format(usdPriceMicros / 1_000_000, usdPriceMicros % 1_000_000 / 10_000)

    companion object {
        const val COIN_PACK_SIZE = 1000

        fun fromId(id: String): ShopProduct? = entries.firstOrNull { it.id == id }
    }
}

enum class StoreStatus { CONNECTING, READY, UNAVAILABLE }

data class StoreListing(val product: ShopProduct, val title: String, val price: String)

sealed interface ShopEvent {
    data class Delivered(val product: ShopProduct) : ShopEvent
    data class Pending(val product: ShopProduct) : ShopEvent
    data object Cancelled : ShopEvent
    data object AlreadyOwned : ShopEvent
    data object Failed : ShopEvent
}

enum class PurchaseState { PURCHASED, PENDING, OTHER }

/** A Play purchase reduced to what [PurchaseProcessor] needs. */
data class OwnedPurchase(
    val token: String,
    val productIds: List<String>,
    val state: PurchaseState,
    val acknowledged: Boolean,
    val quantity: Int = 1,
) {
    val products: List<ShopProduct> get() = productIds.mapNotNull(ShopProduct::fromId)
}
