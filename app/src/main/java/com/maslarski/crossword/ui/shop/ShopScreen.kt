package com.maslarski.crossword.ui.shop

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Toll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maslarski.crossword.R
import com.maslarski.crossword.domain.shop.ShopEvent
import com.maslarski.crossword.domain.shop.ShopProduct
import com.maslarski.crossword.domain.shop.StoreStatus
import com.maslarski.crossword.ui.components.CoinChip

@Composable
fun ShopScreen(onBack: () -> Unit, viewModel: ShopViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    val resources = LocalResources.current
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            val message = when (event) {
                is ShopEvent.Delivered -> when (event.product) {
                    ShopProduct.COINS_1000 -> resources.getString(R.string.shop_delivered_coins)
                    ShopProduct.UNLIMITED -> resources.getString(R.string.shop_delivered_unlimited)
                }
                is ShopEvent.Pending -> resources.getString(R.string.shop_pending)
                ShopEvent.AlreadyOwned -> resources.getString(R.string.shop_already_owned)
                ShopEvent.Failed -> resources.getString(R.string.shop_failed)
                ShopEvent.Cancelled -> resources.getString(R.string.shop_cancelled)
            }
            snackbar.showSnackbar(message)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.shop_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) }
                },
                actions = { CoinChip(state.coins, Modifier.padding(horizontal = 12.dp)) },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    stringResource(R.string.shop_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ProductCard(
                    icon = Icons.Rounded.Toll,
                    accent = MaterialTheme.colorScheme.tertiary,
                    container = MaterialTheme.colorScheme.tertiaryContainer,
                    title = stringResource(R.string.shop_coins_title),
                    summary = stringResource(R.string.shop_coins_summary),
                    price = state.listings[ShopProduct.COINS_1000]?.price,
                    referencePrice = ShopProduct.COINS_1000.usdPrice,
                    owned = false,
                    status = state.status,
                    onBuy = { activity?.let { viewModel.buy(it, ShopProduct.COINS_1000) } },
                )
                ProductCard(
                    icon = Icons.Rounded.AllInclusive,
                    accent = MaterialTheme.colorScheme.primary,
                    container = MaterialTheme.colorScheme.primaryContainer,
                    title = stringResource(R.string.shop_unlimited_title),
                    summary = stringResource(R.string.shop_unlimited_summary),
                    price = state.listings[ShopProduct.UNLIMITED]?.price,
                    referencePrice = ShopProduct.UNLIMITED.usdPrice,
                    owned = state.unlimited,
                    status = state.status,
                    onBuy = { activity?.let { viewModel.buy(it, ShopProduct.UNLIMITED) } },
                )
                when (state.status) {
                    StoreStatus.UNAVAILABLE -> Text(
                        stringResource(R.string.shop_unavailable),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    StoreStatus.CONNECTING -> Text(
                        stringResource(R.string.shop_connecting),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    StoreStatus.READY -> Unit
                }
                TextButton(onClick = viewModel::refresh, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text(stringResource(R.string.shop_restore))
                }
            }
        }
    }
}

@Composable
private fun ProductCard(
    icon: ImageVector,
    accent: Color,
    container: Color,
    title: String,
    summary: String,
    price: String?,
    referencePrice: String,
    owned: Boolean,
    status: StoreStatus,
    onBuy: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = container), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(36.dp))
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Text(summary, style = MaterialTheme.typography.bodyMedium)
            if (owned) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = accent)
                    Text(stringResource(R.string.shop_owned), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
            } else {
                Button(
                    onClick = onBuy,
                    enabled = status == StoreStatus.READY && !price.isNullOrEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.shop_buy, price?.takeUnless { it.isEmpty() } ?: referencePrice))
                }
            }
        }
    }
}
