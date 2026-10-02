package com.maslarski.crossword.data.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.consumePurchase
import com.android.billingclient.api.queryPurchasesAsync
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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/** Google Play Billing Library 9 store for [ShopProduct]s. */
class PlayStoreRepository(
    context: Context,
    entitlements: EntitlementRepository,
    private val scope: CoroutineScope,
    private val verifier: PurchaseSignatureVerifier,
) : StoreRepository, PurchasesUpdatedListener, PurchaseGateway {

    private val _status = MutableStateFlow(StoreStatus.CONNECTING)
    override val status: StateFlow<StoreStatus> = _status.asStateFlow()

    private val _listings = MutableStateFlow<List<StoreListing>>(emptyList())
    override val listings: StateFlow<List<StoreListing>> = _listings.asStateFlow()

    private val _events = Channel<ShopEvent>(Channel.BUFFERED)
    override val events: Flow<ShopEvent> = _events.receiveAsFlow()

    private val processor = PurchaseProcessor(entitlements, this)
    private val processing = Mutex()
    private var details: Map<ShopProduct, ProductDetails> = emptyMap()

    private val client: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    private val connecting = AtomicBoolean(false)

    init {
        if (!verifier.configured) Log.w(TAG, "No Play licence key configured; purchases will not be delivered")
        connect()
    }

    private fun connect() {
        if (!connecting.compareAndSet(false, true)) return
        _status.value = StoreStatus.CONNECTING
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                connecting.set(false)
                if (result.responseCode != BillingResponseCode.OK) {
                    Log.w(TAG, "Billing setup failed: ${result.responseCode} ${result.debugMessage}")
                    _status.value = StoreStatus.UNAVAILABLE
                    return
                }
                scope.launch { refresh() }
            }

            override fun onBillingServiceDisconnected() {
                connecting.set(false)
                _status.value = StoreStatus.CONNECTING
            }
        })
    }

    private suspend fun loadListings() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                ShopProduct.entries.map {
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(it.id)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                },
            )
            .build()
        val (billing, result) = suspendCancellableCoroutine { cont ->
            client.queryProductDetailsAsync(params) { b, r -> if (cont.isActive) cont.resume(b to r) }
        }
        if (billing.responseCode != BillingResponseCode.OK) {
            Log.w(TAG, "Product query failed: ${billing.responseCode} ${billing.debugMessage}")
            _status.value = StoreStatus.UNAVAILABLE
            return
        }
        result.unfetchedProductList.forEach { Log.w(TAG, "Product ${it.productId} unavailable: ${it.statusCode}") }
        details = result.productDetailsList.mapNotNull { pd -> ShopProduct.fromId(pd.productId)?.let { it to pd } }.toMap()
        details.forEach { (product, pd) ->
            val offer = pd.oneTimePurchaseOfferDetails ?: return@forEach
            if (offer.priceCurrencyCode == "USD" && offer.priceAmountMicros != product.usdPriceMicros) {
                Log.w(TAG, "${product.id} costs ${offer.formattedPrice} in Play Console; expected ${product.usdPrice}")
            }
        }
        _listings.value = details.entries
            .sortedBy { it.key.ordinal }
            .map { (product, pd) -> StoreListing(product, pd.name, pd.oneTimePurchaseOfferDetails?.formattedPrice.orEmpty()) }
        _status.value = if (details.isEmpty()) StoreStatus.UNAVAILABLE else StoreStatus.READY
    }

    override fun purchase(activity: Activity, product: ShopProduct) {
        val pd = details[product]
        if (pd == null || !client.isReady) {
            _events.trySend(ShopEvent.Failed)
            return
        }
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(pd).build()))
            .build()
        val result = client.launchBillingFlow(activity, params)
        if (result.responseCode != BillingResponseCode.OK) onPurchasesUpdated(result, null)
    }

    override suspend fun refresh() {
        if (!client.isReady) {
            connect()
            return
        }
        if (details.isEmpty()) loadListings()
        processing.withLock {
            val result = client.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build(),
            )
            if (result.billingResult.responseCode == BillingResponseCode.OK) {
                processor.sync(result.purchasesList.filter(::verified).map(::owned))
            }
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        when (result.responseCode) {
            BillingResponseCode.OK -> purchases.orEmpty().forEach { purchase ->
                if (!verified(purchase)) {
                    _events.trySend(ShopEvent.Failed)
                    return@forEach
                }
                scope.launch {
                    val owned = owned(purchase)
                    if (owned.state == PurchaseState.PENDING) {
                        owned.products.forEach { _events.send(ShopEvent.Pending(it)) }
                        return@launch
                    }
                    processing.withLock { processor.process(owned) }.forEach { _events.send(ShopEvent.Delivered(it)) }
                }
            }
            BillingResponseCode.USER_CANCELED -> _events.trySend(ShopEvent.Cancelled)
            BillingResponseCode.ITEM_ALREADY_OWNED -> {
                _events.trySend(ShopEvent.AlreadyOwned)
                scope.launch { refresh() }
            }
            else -> {
                Log.w(TAG, "Purchase failed: ${result.responseCode} ${result.debugMessage}")
                _events.trySend(ShopEvent.Failed)
            }
        }
    }

    override suspend fun consume(token: String): Boolean =
        client.consumePurchase(ConsumeParams.newBuilder().setPurchaseToken(token).build())
            .billingResult.responseCode == BillingResponseCode.OK

    override suspend fun acknowledge(token: String): Boolean =
        client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(token).build())
            .responseCode == BillingResponseCode.OK

    private fun verified(purchase: Purchase): Boolean =
        verifier.verify(purchase.originalJson, purchase.signature).also {
            if (!it) Log.w(TAG, "Rejected purchase with an invalid signature: ${purchase.products}")
        }

    private fun owned(purchase: Purchase) = OwnedPurchase(
        token = purchase.purchaseToken,
        productIds = purchase.products,
        state = when (purchase.purchaseState) {
            Purchase.PurchaseState.PURCHASED -> PurchaseState.PURCHASED
            Purchase.PurchaseState.PENDING -> PurchaseState.PENDING
            else -> PurchaseState.OTHER
        },
        acknowledged = purchase.isAcknowledged,
        quantity = purchase.quantity,
    )

    private companion object {
        const val TAG = "PlayStore"
    }
}
