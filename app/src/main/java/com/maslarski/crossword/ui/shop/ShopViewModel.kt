package com.maslarski.crossword.ui.shop

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maslarski.crossword.domain.repository.EntitlementRepository
import com.maslarski.crossword.domain.repository.StoreRepository
import com.maslarski.crossword.domain.repository.WalletRepository
import com.maslarski.crossword.domain.shop.ShopEvent
import com.maslarski.crossword.domain.shop.ShopProduct
import com.maslarski.crossword.domain.shop.StoreListing
import com.maslarski.crossword.domain.shop.StoreStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ShopUiState(
    val status: StoreStatus = StoreStatus.CONNECTING,
    val listings: Map<ShopProduct, StoreListing> = emptyMap(),
    val unlimited: Boolean = false,
    val coins: Int = 0,
)

@HiltViewModel
class ShopViewModel @Inject constructor(
    private val store: StoreRepository,
    entitlements: EntitlementRepository,
    wallet: WalletRepository,
) : ViewModel() {

    val state: StateFlow<ShopUiState> = combine(
        store.status,
        store.listings,
        entitlements.observeUnlimited(),
        wallet.observeCoins(),
    ) { status, listings, unlimited, coins ->
        ShopUiState(status, listings.associateBy { it.product }, unlimited, coins)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShopUiState())

    val events: Flow<ShopEvent> = store.events

    init {
        refresh()
    }

    fun buy(activity: Activity, product: ShopProduct) = store.purchase(activity, product)

    fun refresh() {
        viewModelScope.launch { store.refresh() }
    }
}
