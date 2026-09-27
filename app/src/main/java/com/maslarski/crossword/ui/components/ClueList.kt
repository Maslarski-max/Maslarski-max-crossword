package com.maslarski.crossword.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.maslarski.crossword.domain.engine.CrosswordEngine
import com.maslarski.crossword.domain.model.BoardState
import com.maslarski.crossword.domain.model.Word

/** Adds a titled section of clues to a LazyColumn. */
@OptIn(ExperimentalFoundationApi::class)
fun LazyListScope.clueSection(
    title: String,
    words: List<Word>,
    board: BoardState?,
    activeKey: String?,
    onSelect: (Word) -> Unit,
) {
    stickyHeader(key = "header:$title") {
        Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
    items(words, key = { it.key }) { word ->
        ClueRow(
            word = word,
            active = word.key == activeKey,
            filled = board != null && CrosswordEngine.isWordFilled(board, word),
            onClick = { onSelect(word) },
        )
    }
}

@Composable
fun ClueRow(word: Word, active: Boolean, filled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val background = if (active) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    val content = when {
        active -> MaterialTheme.colorScheme.onSecondaryContainer
        filled -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface
    }
    Surface(color = background, contentColor = content, modifier = modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.Top) {
            Text(
                word.number.toString(),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End,
                modifier = Modifier.width(32.dp),
            )
            Text(
                "${word.clue} (${word.length})",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f).padding(start = 12.dp),
            )
            if (filled) {
                Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 8.dp).size(20.dp),
                )
            }
        }
    }
}
