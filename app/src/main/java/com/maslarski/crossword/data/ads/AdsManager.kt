package com.maslarski.crossword.data.ads

import android.content.Context
import com.google.android.gms.ads.MobileAds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Initialises the Mobile Ads SDK once consent allows it. The app currently shows no ads; this is the hook for
 * opt-in formats such as rewarded ads.
 */
class AdsManager(
    private val context: Context,
    private val appScope: CoroutineScope,
    private val io: CoroutineDispatcher,
) {

    private val initStarted = AtomicBoolean(false)
    private val _adsReady = MutableStateFlow(false)

    /** True once consent allows ad requests and the Mobile Ads SDK is initialised. */
    val adsReady: StateFlow<Boolean> = _adsReady.asStateFlow()

    /** Call only after [ConsentManager.canRequestAds] is true. Safe to call repeatedly. */
    fun initialize() {
        if (!initStarted.compareAndSet(false, true)) return
        appScope.launch {
            // MobileAds.initialize does disk I/O; keep it off the main thread.
            withContext(io) { MobileAds.initialize(context) {} }
            _adsReady.value = true
        }
    }
}
