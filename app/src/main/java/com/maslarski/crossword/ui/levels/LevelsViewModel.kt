package com.maslarski.crossword.ui.levels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maslarski.crossword.domain.engine.GameRules
import com.maslarski.crossword.domain.engine.LevelUnlocks
import com.maslarski.crossword.domain.model.Difficulty
import com.maslarski.crossword.domain.model.GameSession
import com.maslarski.crossword.domain.model.UnlockResult
import com.maslarski.crossword.domain.repository.ProgressRepository
import com.maslarski.crossword.domain.repository.PuzzleRepository
import com.maslarski.crossword.domain.repository.WalletRepository
import com.maslarski.crossword.ui.components.CoinPrompt
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LevelItem(
    val number: Int,
    val puzzleId: String,
    val sessionId: String,
    val title: String,
    val difficulty: Difficulty,
    val rows: Int,
    val cols: Int,
    val wordCount: Int,
    val unlocked: Boolean,
    /** The next locked level, which can be bought for [GameRules.LEVEL_UNLOCK_COST] coins. */
    val unlockable: Boolean,
    val completed: Boolean,
    val stars: Int,
    val bestScore: Int,
    val bestTimeSeconds: Long?,
)

data class LevelsUiState(val loading: Boolean = true, val levels: List<LevelItem> = emptyList(), val coins: Int = 0)

sealed interface LevelsEvent {
    data class Prompt(val prompt: CoinPrompt) : LevelsEvent
    data object PreviousLocked : LevelsEvent
    data class Open(val level: LevelItem) : LevelsEvent
}

@HiltViewModel
class LevelsViewModel @Inject constructor(
    private val puzzles: PuzzleRepository,
    private val progress: ProgressRepository,
    wallet: WalletRepository,
) : ViewModel() {

    private val _events = Channel<LevelsEvent>(Channel.BUFFERED)
    val events: Flow<LevelsEvent> = _events.receiveAsFlow()

    val state: StateFlow<LevelsUiState> = flow {
        val levels = puzzles.levels()
        progress.syncLevels(levels)
        emitAll(
            combine(progress.observeLevels(), wallet.observeCoins()) { records, coins ->
                val byId = records.associateBy { it.puzzleId }
                val unlockedIds = records.filter { it.unlocked }.mapTo(HashSet()) { it.puzzleId }
                val nextUnlockable = LevelUnlocks.nextUnlockable(levels.map { it.id }, unlockedIds)
                LevelsUiState(
                    loading = false,
                    coins = coins,
                    levels = levels.mapIndexed { i, puzzle ->
                        val record = byId[puzzle.id]
                        LevelItem(
                            number = i + 1,
                            puzzleId = puzzle.id,
                            sessionId = GameSession.Level(puzzle.id).id,
                            title = puzzle.title,
                            difficulty = puzzle.difficulty,
                            rows = puzzle.rows,
                            cols = puzzle.cols,
                            wordCount = puzzle.words.size,
                            unlocked = record?.unlocked ?: (i == 0),
                            unlockable = puzzle.id == nextUnlockable,
                            completed = record?.completed == true,
                            stars = record?.stars ?: 0,
                            bestScore = record?.bestScore ?: 0,
                            bestTimeSeconds = record?.bestTimeSeconds,
                        )
                    },
                )
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LevelsUiState())

    fun onLevelTap(level: LevelItem) {
        _events.trySend(
            when {
                level.unlocked -> LevelsEvent.Open(level)
                level.unlockable -> LevelsEvent.Prompt(CoinPrompt.UnlockLevel(level.puzzleId, level.title, GameRules.LEVEL_UNLOCK_COST))
                else -> LevelsEvent.PreviousLocked
            },
        )
    }

    fun unlock(puzzleId: String) {
        viewModelScope.launch {
            val result = progress.unlockLevel(puzzles.levels(), puzzleId)
            _events.send(
                when (result) {
                    UnlockResult.UNLOCKED, UnlockResult.ALREADY_UNLOCKED -> return@launch
                    UnlockResult.NOT_ENOUGH_COINS -> LevelsEvent.Prompt(CoinPrompt.NotEnoughCoins(GameRules.LEVEL_UNLOCK_COST))
                    UnlockResult.NOT_NEXT -> LevelsEvent.PreviousLocked
                },
            )
        }
    }
}
