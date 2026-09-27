package com.maslarski.crossword

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
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

        // Consent from a previous session lets ads start loading while the consent info refreshes.
        if (consentManager.canRequestAds.value) adsManager.initialize()
        consentManager.gatherConsent(this) {
            val granted = consentManager.canRequestAds.value
            telemetry.updateConsent(granted)
            if (granted) adsManager.initialize()
        }

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
