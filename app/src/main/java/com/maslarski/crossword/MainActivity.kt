package com.maslarski.crossword

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maslarski.crossword.data.ads.AdsManager
import com.maslarski.crossword.data.ads.ConsentManager
import com.maslarski.crossword.data.telemetry.FirebaseTelemetry
import com.maslarski.crossword.domain.model.Settings
import com.maslarski.crossword.domain.repository.SettingsRepository
import com.maslarski.crossword.ui.components.LocalAdController
import com.maslarski.crossword.ui.components.LocalConsentManager
import com.maslarski.crossword.ui.navigation.CrosswordNavHost
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
            CrosswordTheme(themeMode = settings.themeMode, dynamicColor = settings.dynamicColor) {
                CompositionLocalProvider(
                    LocalAdController provides adsManager,
                    LocalConsentManager provides consentManager,
                ) {
                    CrosswordNavHost()
                }
            }
        }
    }
}
