package com.maslarski.crossword.ui.clues

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.maslarski.crossword.domain.engine.CrosswordEngine
import com.maslarski.crossword.domain.model.BoardState
import com.maslarski.crossword.domain.model.Puzzle
import com.maslarski.crossword.domain.repository.ProgressRepository
import com.maslarski.crossword.domain.repository.PuzzleRepository
import com.maslarski.crossword.ui.navigation.CluesRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class CluesUiState(
    val loading: Boolean = true,
    val puzzle: Puzzle? = null,
    val board: BoardState? = null,
) {
    val activeKey: String?
        get() = if (puzzle != null && board != null) CrosswordEngine.activeWord(puzzle, board)?.key else null
}

@HiltViewModel
class CluesViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    puzzles: PuzzleRepository,
    progress: ProgressRepository,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<CluesRoute>()

    val state: StateFlow<CluesUiState> = flow {
        val puzzle = puzzles.puzzle(route.puzzleId)
        if (puzzle == null) {
            emit(CluesUiState(loading = false))
            return@flow
        }
        emitAll(
            progress.observeBoard(route.sessionId).map { saved ->
                CluesUiState(
                    loading = false,
                    puzzle = puzzle,
                    board = saved?.takeIf { it.puzzleId == puzzle.id && it.board.entries.length == puzzle.cellCount }?.board,
                )
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CluesUiState())
}
