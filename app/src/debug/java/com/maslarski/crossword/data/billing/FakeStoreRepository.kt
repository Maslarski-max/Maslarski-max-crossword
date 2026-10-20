package com.maslarski.crossword.data.billing

import android.app.Activity
import com.maslarski.crossword.domain.repository.EntitlementRepository
import com.maslarski.crossword.domain.repository.StoreRepository
import com.maslarski.crossword.domain.shop.OwnedPurchase
import com.maslarski.crossword.domain.shop.PurchaseGateway
import com.maslarski.crossword.domain.shop.PurchaseProcessor
import com.maslarski.crossword.domain.shop.PurchaseState
import com.maslarski.crossword.domain.shop.ShopEvent
import com.maslarski.crossword.domain.shop.ShopProduct
import com.maslarski.crossword.domain.shop.StoreListing
import com.maslarski.crossword.domain.shop.StoreStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

class FakeStoreRepository(
    private val entitlements: EntitlementRepository,
    private val scope: CoroutineScope,
) : StoreRepository {

    private val mutableStatus = MutableStateFlow(StoreStatus.READY)
    override val status: StateFlow<StoreStatus> = mutableStatus.asStateFlow()

    private val mutableListings = MutableStateFlow(
        ShopProduct.entries.map { product ->
            val title = when (product) {
                ShopProduct.COINS_1000 -> "1,000 Coins (test)"
                ShopProduct.UNLIMITED -> "Unlimited Mode (test)"
            }
            StoreListing(product, title, product.usdPrice)
        },
    )
    override val listings: StateFlow<List<StoreListing>> = mutableListings.asStateFlow()

    private val eventChannel = Channel<ShopEvent>(Channel.BUFFERED)
    override val events: Flow<ShopEvent> = eventChannel.receiveAsFlow()

    private val gateway = object : PurchaseGateway {
        override suspend fun consume(token: String) = true
        override suspend fun acknowledge(token: String) = true
    }
    private val processor = PurchaseProcessor(entitlements, gateway)
    private val processing = Mutex()

    override fun purchase(activity: Activity, product: ShopProduct) {
        scope.launch { processPurchase(product) }
    }

    internal suspend fun processPurchase(product: ShopProduct) {
        processing.withLock {
            if (product == ShopProduct.UNLIMITED && entitlements.isUnlimited()) {
                eventChannel.send(ShopEvent.AlreadyOwned)
                return@withLock
            }
            val purchase = OwnedPurchase(
                token = "debug-${UUID.randomUUID()}",
                productIds = listOf(product.id),
                state = PurchaseState.PURCHASED,
                acknowledged = false,
            )
            processor.process(purchase).forEach { eventChannel.send(ShopEvent.Delivered(it)) }
        }
    }

    override suspend fun refresh() = Unit
}
