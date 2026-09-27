package com.maslarski.crossword.ui.clues

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maslarski.crossword.R
import com.maslarski.crossword.domain.engine.CrosswordEngine
import com.maslarski.crossword.domain.model.BoardState
import com.maslarski.crossword.domain.model.Direction
import com.maslarski.crossword.domain.model.Word
import com.maslarski.crossword.ui.components.BannerAdSlot
import com.maslarski.crossword.ui.components.ClueRow
import com.maslarski.crossword.ui.components.clueSection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CluesScreen(
    onBack: () -> Unit,
    onClueSelected: (wordKey: String) -> Unit,
    viewModel: CluesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.puzzle?.let { stringResource(R.string.clues_title_for, it.title) } ?: stringResource(R.string.clues_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) }
                },
            )
        },
        bottomBar = { BannerAdSlot() },
    ) { padding ->
        val puzzle = state.puzzle
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (puzzle == null) {
                if (state.loading) CircularProgressIndicator(Modifier.align(Alignment.Center))
                else Text(stringResource(R.string.puzzle_not_found), Modifier.align(Alignment.Center))
                return@Box
            }
            val select: (Word) -> Unit = { onClueSelected(it.key) }
            val acrossTitle = stringResource(R.string.across)
            val downTitle = stringResource(R.string.down)
            BoxWithConstraints(Modifier.fillMaxSize()) {
                if (maxWidth >= 600.dp) {
                    Row(Modifier.fillMaxSize()) {
                        LazyColumn(Modifier.weight(1f).fillMaxHeight(), contentPadding = PaddingValues(bottom = 16.dp)) {
                            clueSection(acrossTitle, puzzle.acrossWords, state.board, state.activeKey, select)
                        }
                        VerticalDivider()
                        LazyColumn(Modifier.weight(1f).fillMaxHeight(), contentPadding = PaddingValues(bottom = 16.dp)) {
                            clueSection(downTitle, puzzle.downWords, state.board, state.activeKey, select)
                        }
                    }
                } else {
                    val initialTab = if (state.board?.direction == Direction.DOWN) 1 else 0
                    var tab by rememberSaveable { mutableIntStateOf(initialTab) }
                    Column(Modifier.fillMaxSize()) {
                        PrimaryTabRow(selectedTabIndex = tab) {
                            Tab(tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.across_count, puzzle.acrossWords.size)) })
                            Tab(tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.down_count, puzzle.downWords.size)) })
                        }
                        val words = if (tab == 0) puzzle.acrossWords else puzzle.downWords
                        ClueColumn(words, state.board, state.activeKey, select, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun ClueColumn(
    words: List<Word>,
    board: BoardState?,
    activeKey: String?,
    onSelect: (Word) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(words, activeKey) {
        val index = words.indexOfFirst { it.key == activeKey }
        if (index >= 0) listState.scrollToItem((index - 2).coerceAtLeast(0))
    }
    LazyColumn(modifier, state = listState, contentPadding = PaddingValues(vertical = 8.dp)) {
        items(words, key = { it.key }) { word ->
            ClueRow(
                word = word,
                active = word.key == activeKey,
                filled = board != null && CrosswordEngine.isWordFilled(board, word),
                onClick = { onSelect(word) },
            )
        }
    }
}
