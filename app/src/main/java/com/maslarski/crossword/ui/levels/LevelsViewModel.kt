package com.maslarski.crossword.ui.levels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maslarski.crossword.domain.model.Difficulty
import com.maslarski.crossword.domain.model.GameSession
import com.maslarski.crossword.domain.repository.ProgressRepository
import com.maslarski.crossword.domain.repository.PuzzleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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
    val completed: Boolean,
    val stars: Int,
    val bestScore: Int,
    val bestTimeSeconds: Long?,
)

data class LevelsUiState(val loading: Boolean = true, val levels: List<LevelItem> = emptyList())

@HiltViewModel
class LevelsViewModel @Inject constructor(
    puzzles: PuzzleRepository,
    progress: ProgressRepository,
) : ViewModel() {
    val state: StateFlow<LevelsUiState> = flow {
        val levels = puzzles.levels()
        progress.syncLevels(levels)
        emitAll(
            progress.observeLevels().map { records ->
                val byId = records.associateBy { it.puzzleId }
                LevelsUiState(
                    loading = false,
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
}
