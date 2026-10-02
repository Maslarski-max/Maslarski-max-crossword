package com.maslarski.crossword.ui.hub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maslarski.crossword.domain.arena.ArenaStats
import com.maslarski.crossword.domain.model.PlayerStats
import com.maslarski.crossword.domain.profile.Achievement
import com.maslarski.crossword.domain.repository.ProfileRepository
import com.maslarski.crossword.domain.repository.ArenaRepository
import com.maslarski.crossword.domain.repository.ProgressRepository
import com.maslarski.crossword.domain.repository.PuzzleRepository
import com.maslarski.crossword.domain.repository.WalletRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

data class HubUiState(
    val loading: Boolean = true,
    val levelsSolved: Int = 0,
    val levelsTotal: Int = 0,
    val classic: PlayerStats = PlayerStats(0, 0, 0),
    val arena: ArenaStats = ArenaStats(),
    val arenaInProgress: Boolean = false,
    val coins: Int = 0,
    val dailySolvedToday: Boolean = false,
    val achievementsUnlocked: Int = 0,
    val achievementsTotal: Int = Achievement.entries.size,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HubViewModel @Inject constructor(
    puzzles: PuzzleRepository,
    progress: ProgressRepository,
    arena: ArenaRepository,
    wallet: WalletRepository,
    profile: ProfileRepository,
    clock: Clock,
) : ViewModel() {

    /** Current local date, re-checked at least every minute so the streak rolls over at midnight. */
    private val dates: Flow<LocalDate> = flow {
        while (true) {
            val now = LocalDateTime.now(clock)
            emit(now.toLocalDate())
            val untilMidnight = Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay()).toMillis()
            delay(untilMidnight.coerceIn(1, DATE_POLL_MS))
        }
    }.distinctUntilChanged()

    val state: StateFlow<HubUiState> = flow {
        progress.syncLevels(puzzles.levels())
        val extras = combine(
            dates.flatMapLatest(progress::observeDaily),
            profile.observeUnlocked(),
        ) { daily, unlocked -> (daily?.completed == true) to unlocked.size }
        emitAll(
            combine(
                progress.observeLevels(),
                dates.flatMapLatest(progress::observeStats),
                arena.observeStats(),
                arena.observeActiveMatch(),
                combine(wallet.observeCoins(), extras, ::Pair),
            ) { levels, classic, arenaStats, active, (coins, extra) ->
                HubUiState(
                    loading = false,
                    levelsSolved = levels.count { it.completed },
                    levelsTotal = levels.size,
                    classic = classic,
                    arena = arenaStats,
                    arenaInProgress = active != null,
                    coins = coins,
                    dailySolvedToday = extra.first,
                    achievementsUnlocked = extra.second,
                )
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HubUiState())

    private companion object {
        const val DATE_POLL_MS = 60_000L
    }
}
