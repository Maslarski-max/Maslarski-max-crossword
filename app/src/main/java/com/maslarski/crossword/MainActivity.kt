package com.maslarski.crossword

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maslarski.crossword.data.ads.AdsManager
import com.maslarski.crossword.data.ads.ConsentManager
import com.maslarski.crossword.data.telemetry.FirebaseTelemetry
import com.maslarski.crossword.domain.model.Settings
import com.maslarski.crossword.domain.repository.SettingsRepository
import com.maslarski.crossword.domain.repository.WalletRepository
import com.maslarski.crossword.ui.components.CoinChangeToast
import com.maslarski.crossword.ui.components.LocalConsentManager
import com.maslarski.crossword.ui.navigation.CrosswordNavHost
import com.maslarski.crossword.ui.profile.AchievementToast
import com.maslarski.crossword.ui.profile.LoginRewardDialog
import com.maslarski.crossword.ui.profile.MainViewModel
import com.maslarski.crossword.ui.theme.CrosswordTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var consentManager: ConsentManager
    @Inject lateinit var adsManager: AdsManager
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var telemetry: FirebaseTelemetry
    @Inject lateinit var walletRepository: WalletRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Emits the previous session's consent immediately, then every change from the consent or privacy options forms.
        lifecycleScope.launch {
            consentManager.canRequestAds.collect { granted ->
                telemetry.updateConsent(granted)
                if (granted) adsManager.initialize()
            }
        }
        consentManager.gatherConsent(this) {}

        setContent {
            val settings by settingsRepository.settings.collectAsStateWithLifecycle(initialValue = Settings())
            val main: MainViewModel = hiltViewModel()
            val reward by main.reward.collectAsStateWithLifecycle()
            LifecycleEventEffect(Lifecycle.Event.ON_START) { main.onAppOpened() }
            CrosswordTheme(themeMode = settings.themeMode, dynamicColor = settings.dynamicColor) {
                CompositionLocalProvider(
                    LocalConsentManager provides consentManager,
                ) {
                    Box(Modifier.fillMaxSize()) {
                        CrosswordNavHost()
                        CoinChangeToast(remember { walletRepository.observeCoins() })
                        AchievementToast(main.achievementUnlocks)
                        reward?.let { LoginRewardDialog(it, onDismiss = main::onRewardDismissed) }
                    }
                }
            }
        }
    }
}
