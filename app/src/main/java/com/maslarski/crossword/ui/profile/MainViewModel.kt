package com.maslarski.crossword.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maslarski.crossword.domain.profile.AchievementTracker
import com.maslarski.crossword.domain.profile.LoginReward
import com.maslarski.crossword.domain.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
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

    val achievementUnlocks = tracker.unlocks

    /** Called whenever the app comes to the foreground; pays at most one bonus per calendar day. */
    fun onAppOpened() {
        viewModelScope.launch {
            profile.claimDailyLogin(LocalDate.now(clock))?.let { _reward.value = it }
        }
    }

    fun onRewardDismissed() {
        _reward.value = null
    }
}
