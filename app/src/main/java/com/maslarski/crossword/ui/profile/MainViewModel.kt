package com.maslarski.crossword.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maslarski.crossword.domain.profile.Achievement
import com.maslarski.crossword.domain.profile.AchievementTracker
import com.maslarski.crossword.domain.profile.LoginReward
import com.maslarski.crossword.domain.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
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

    private val _achievement = MutableStateFlow<Achievement?>(null)

    /** Achievement banner currently on screen, kept here so it survives configuration changes. */
    val achievement: StateFlow<Achievement?> = _achievement.asStateFlow()

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

    /** Called whenever the app comes to the foreground; pays at most one bonus per calendar day. */
    fun onAppOpened() {
        viewModelScope.launch {
            profile.claimDailyLogin(LocalDate.now(clock))?.let { _reward.value = it }
        }
    }

    fun onRewardDismissed() {
        _reward.value = null
    }

    private companion object {
        const val ACHIEVEMENT_SHOW_MS = 2_600L
        const val ACHIEVEMENT_EXIT_MS = 400L
    }
}
