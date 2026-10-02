package com.maslarski.crossword.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maslarski.crossword.domain.profile.Achievement
import com.maslarski.crossword.domain.profile.PlayerProfile
import com.maslarski.crossword.domain.profile.localDates
import com.maslarski.crossword.domain.repository.ProfileRepository
import com.maslarski.crossword.domain.repository.WalletRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import javax.inject.Inject

data class BadgeUi(val achievement: Achievement, val progress: Long, val unlockedAt: Long?)

data class StatsUiState(
    val loading: Boolean = true,
    val profile: PlayerProfile = PlayerProfile(),
    val badges: List<BadgeUi> = emptyList(),
    val coins: Int = 0,
) {
    val unlockedCount: Int get() = badges.count { it.unlockedAt != null }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StatsViewModel @Inject constructor(
    profile: ProfileRepository,
    wallet: WalletRepository,
    clock: Clock,
) : ViewModel() {

    val state: StateFlow<StatsUiState> = combine(
        localDates(clock).flatMapLatest(profile::observeProfile),
        profile.observeUnlocked(),
        wallet.observeCoins(),
    ) { stats, unlocked, coins ->
        StatsUiState(
            loading = false,
            profile = stats,
            badges = Achievement.entries.map { BadgeUi(it, it.progress(stats), unlocked[it]) },
            coins = coins,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())
}
