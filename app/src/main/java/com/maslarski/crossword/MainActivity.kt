package com.maslarski.crossword

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.maslarski.crossword.data.games.PlayGamesManager
import com.maslarski.crossword.data.games.PlayGamesState
import com.maslarski.crossword.domain.model.Settings
import com.maslarski.crossword.domain.repository.ProfileRepository
import com.maslarski.crossword.domain.repository.SettingsRepository
import com.maslarski.crossword.domain.repository.StoreRepository
import com.maslarski.crossword.domain.repository.WalletRepository
import com.maslarski.crossword.ui.components.CoinChangeToast
import com.maslarski.crossword.ui.navigation.CrosswordNavHost
import com.maslarski.crossword.ui.profile.AchievementToast
import com.maslarski.crossword.ui.profile.LoginRewardDialog
import com.maslarski.crossword.ui.profile.MainViewModel
import com.maslarski.crossword.ui.theme.CrosswordTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var walletRepository: WalletRepository
    @Inject lateinit var storeRepository: StoreRepository
    @Inject lateinit var playGames: PlayGamesManager
    @Inject lateinit var profileRepository: ProfileRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(playGames.state, profileRepository.observeUnlocked().map { it.keys }, ::Pair)
                    .distinctUntilChanged()
                    .collect { (state, unlocked) ->
                        if (state is PlayGamesState.SignedIn) {
                            playGames.unlockAchievements(this@MainActivity, unlocked)
                        }
                    }
            }
        }

        setContent {
            val settings by settingsRepository.settings.collectAsStateWithLifecycle(initialValue = Settings())
            val main: MainViewModel = hiltViewModel()
            val reward by main.reward.collectAsStateWithLifecycle()
            LifecycleStartEffect(Unit) {
                main.onAppStarted()
                onStopOrDispose { main.onAppStopped() }
            }
            CrosswordTheme(themeMode = settings.themeMode, dynamicColor = settings.dynamicColor) {
                Box(Modifier.fillMaxSize()) {
                    CrosswordNavHost()
                    CoinChangeToast(
                        changes = remember { walletRepository.observeChanges() },
                        onShown = walletRepository::acknowledgeChange,
                        paused = main.holdCoinToasts,
                    )
                    AchievementToast(main.achievement.collectAsStateWithLifecycle().value)
                    reward?.let { LoginRewardDialog(it, onDismiss = main::onRewardDismissed) }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        lifecycleScope.launch { playGames.refresh(this@MainActivity) }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch { storeRepository.refresh() }
    }
}
