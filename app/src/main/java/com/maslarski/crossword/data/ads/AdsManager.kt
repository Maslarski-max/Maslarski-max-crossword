package com.maslarski.crossword.data.ads

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.maslarski.crossword.BuildConfig
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/** What the UI needs from the ads layer; provided to Compose through LocalAdController. */
interface AdController {
    /** True once consent allows ad requests and the Mobile Ads SDK is initialised. */
    val adsReady: StateFlow<Boolean>
    val bannerAdUnitId: String

    /** Shows an interstitial between levels if one is loaded and the frequency cap allows it. */
    fun showInterstitialBetweenLevels(activity: Activity, onFinished: () -> Unit)
}

class AdsManager(
    private val context: Context,
    private val appScope: CoroutineScope,
    private val io: CoroutineDispatcher,
) : AdController {

    private val initStarted = AtomicBoolean(false)
    private val _adsReady = MutableStateFlow(false)
    override val adsReady: StateFlow<Boolean> = _adsReady.asStateFlow()
    override val bannerAdUnitId: String = BuildConfig.ADMOB_BANNER_ID

    private var interstitial: InterstitialAd? = null
    private var loadingInterstitial = false
    private var levelTransitions = 0
    private var lastInterstitialAt = 0L

    /** Call only after [ConsentManager.canRequestAds] is true. Safe to call repeatedly. */
    fun initialize() {
        if (!initStarted.compareAndSet(false, true)) return
        appScope.launch {
            // MobileAds.initialize does disk I/O; keep it off the main thread.
            withContext(io) { MobileAds.initialize(context) {} }
            _adsReady.value = true
            loadInterstitial()
        }
    }

    private fun loadInterstitial() {
        if (!_adsReady.value || interstitial != null || loadingInterstitial) return
        loadingInterstitial = true
        InterstitialAd.load(
            context,
            BuildConfig.ADMOB_INTERSTITIAL_ID,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitial = ad
                    loadingInterstitial = false
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitial = null
                    loadingInterstitial = false
                }
            },
        )
    }

    override fun showInterstitialBetweenLevels(activity: Activity, onFinished: () -> Unit) {
        levelTransitions++
        val ad = interstitial
        val now = SystemClock.elapsedRealtime()
        val capped = levelTransitions % TRANSITIONS_PER_AD != 0 || now - lastInterstitialAt < MIN_INTERVAL_MS
        if (ad == null || capped) {
            loadInterstitial()
            onFinished()
            return
        }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                interstitial = null
                loadInterstitial()
                onFinished()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                interstitial = null
                loadInterstitial()
                onFinished()
            }
        }
        lastInterstitialAt = now
        ad.show(activity)
    }

    private companion object {
        const val TRANSITIONS_PER_AD = 2
        const val MIN_INTERVAL_MS = 90_000L
    }
}
