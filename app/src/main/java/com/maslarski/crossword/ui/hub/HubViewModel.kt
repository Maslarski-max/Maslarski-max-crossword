package com.maslarski.crossword.ui.hub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maslarski.crossword.domain.arena.ArenaStats
import com.maslarski.crossword.domain.model.PlayerStats
import com.maslarski.crossword.domain.repository.ArenaRepository
import com.maslarski.crossword.domain.repository.ProgressRepository
import com.maslarski.crossword.domain.repository.PuzzleRepository
import com.maslarski.crossword.domain.repository.WalletRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class HubUiState(
    val loading: Boolean = true,
    val levelsSolved: Int = 0,
    val levelsTotal: Int = 0,
    val classic: PlayerStats = PlayerStats(0, 0, 0),
    val arena: ArenaStats = ArenaStats(),
    val arenaInProgress: Boolean = false,
    val coins: Int = 0,
)

@HiltViewModel
class HubViewModel @Inject constructor(
    puzzles: PuzzleRepository,
    progress: ProgressRepository,
    arena: ArenaRepository,
    wallet: WalletRepository,
    clock: Clock,
) : ViewModel() {

    val state: StateFlow<HubUiState> = flow {
        progress.syncLevels(puzzles.levels())
        emitAll(
            combine(
                progress.observeLevels(),
                progress.observeStats(LocalDate.now(clock)),
                arena.observeStats(),
                arena.observeActiveMatch(),
                wallet.observeCoins(),
            ) { levels, classic, arenaStats, active, coins ->
                HubUiState(
                    loading = false,
                    levelsSolved = levels.count { it.completed },
                    levelsTotal = levels.size,
                    classic = classic,
                    arena = arenaStats,
                    arenaInProgress = active != null,
                    coins = coins,
                )
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HubUiState())
}
