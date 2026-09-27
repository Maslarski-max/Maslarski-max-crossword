package com.maslarski.crossword.ui.arena

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maslarski.crossword.data.telemetry.Telemetry
import com.maslarski.crossword.domain.arena.ArenaMatch
import com.maslarski.crossword.domain.arena.ArenaStats
import com.maslarski.crossword.domain.arena.StartArenaMatch
import com.maslarski.crossword.domain.model.Difficulty
import com.maslarski.crossword.domain.repository.ArenaRepository
import com.maslarski.crossword.domain.repository.WalletRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ArenaLobbyUiState(
    val loading: Boolean = true,
    val stats: ArenaStats = ArenaStats(),
    val active: ArenaMatch? = null,
    val coins: Int = 0,
    val starting: Boolean = false,
)

sealed interface ArenaLobbyEvent {
    data class Open(val matchId: Long) : ArenaLobbyEvent
    data object NoPuzzles : ArenaLobbyEvent
}

@HiltViewModel
class ArenaLobbyViewModel @Inject constructor(
    arena: ArenaRepository,
    wallet: WalletRepository,
    private val startMatch: StartArenaMatch,
    private val telemetry: Telemetry,
) : ViewModel() {

    private val starting = MutableStateFlow(false)

    val state: StateFlow<ArenaLobbyUiState> = combine(
        arena.observeStats(),
        arena.observeActiveMatch(),
        wallet.observeCoins(),
        starting,
    ) { stats, active, coins, starting ->
        ArenaLobbyUiState(loading = false, stats = stats, active = active, coins = coins, starting = starting)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ArenaLobbyUiState())

    private val _events = Channel<ArenaLobbyEvent>(Channel.BUFFERED)
    val events: Flow<ArenaLobbyEvent> = _events.receiveAsFlow()

    fun start(difficulty: Difficulty) {
        if (starting.value || difficulty !in state.value.stats.unlocked) return
        starting.value = true
        viewModelScope.launch {
            try {
                val match = startMatch(difficulty)
                if (match == null) {
                    _events.send(ArenaLobbyEvent.NoPuzzles)
                } else {
                    telemetry.arenaMatchStarted(match.state)
                    _events.send(ArenaLobbyEvent.Open(match.id))
                }
            } finally {
                starting.value = false
            }
        }
    }
}
