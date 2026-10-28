package com.maslarski.crossword.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maslarski.crossword.domain.profile.Achievement
import com.maslarski.crossword.domain.profile.AchievementTracker
import com.maslarski.crossword.domain.profile.LoginReward
import com.maslarski.crossword.domain.profile.localDates
import com.maslarski.crossword.domain.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject

/** App-wide retention UI: the daily login bonus dialog and achievement unlock banners. */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val profile: ProfileRepository,
    tracker: AchievementTracker,
    private val clock: Clock,
) : ViewModel() {

    private val _reward = MutableStateFlow<LoginReward?>(null)
    val reward: StateFlow<LoginReward?> = _reward.asStateFlow()

    private val _holdCoinToasts = MutableStateFlow(false)

    /** True from the start of a login-bonus claim until its dialog is dismissed, so its coin pop-up follows the dialog. */
    val holdCoinToasts: StateFlow<Boolean> = _holdCoinToasts.asStateFlow()

    private val _achievement = MutableStateFlow<Achievement?>(null)

    /** Achievement banner currently on screen, kept here so it survives configuration changes. */
    val achievement: StateFlow<Achievement?> = _achievement.asStateFlow()

    private var appStartedJob: Job? = null

    init {
        viewModelScope.launch {
            tracker.unlocks.collect { unlocked ->
                _achievement.value = unlocked
                delay(ACHIEVEMENT_SHOW_MS)
                _achievement.value = null
                delay(ACHIEVEMENT_EXIT_MS)
            }
        }
    }

    fun onAppStarted() {
        if (appStartedJob?.isActive == true) return
        appStartedJob = viewModelScope.launch {
            localDates(clock).collect { today ->
                if (_reward.value != null) return@collect
                _holdCoinToasts.value = true
                try {
                    profile.claimDailyLogin(today)?.let { _reward.value = it }
                } finally {
                    if (_reward.value == null) _holdCoinToasts.value = false
                }
            }
        }
    }

    fun onAppStopped() {
        appStartedJob?.cancel()
        appStartedJob = null
    }

    fun onRewardDismissed() {
        _reward.value = null
        _holdCoinToasts.value = false
    }

    private companion object {
        const val ACHIEVEMENT_SHOW_MS = 2_600L
        const val ACHIEVEMENT_EXIT_MS = 400L
    }
}
