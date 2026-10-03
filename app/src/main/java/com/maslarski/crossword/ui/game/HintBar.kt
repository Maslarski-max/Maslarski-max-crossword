package com.maslarski.crossword.ui.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.FactCheck
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.maslarski.crossword.R
import com.maslarski.crossword.domain.engine.Hint

@Composable
fun HintBar(onHint: (Hint) -> Unit, modifier: Modifier = Modifier, free: Boolean = false) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        HintButton(Icons.Rounded.Lightbulb, stringResource(R.string.hint_reveal_letter), Hint.REVEAL_LETTER, free, onHint, Modifier.weight(1f))
        HintButton(Icons.Rounded.AutoFixHigh, stringResource(R.string.hint_reveal_word), Hint.REVEAL_WORD, free, onHint, Modifier.weight(1f))
        HintButton(Icons.Rounded.FactCheck, stringResource(R.string.hint_check), Hint.CHECK_ERRORS, free, onHint, Modifier.weight(1f))
    }
}

@Composable
private fun HintButton(
    icon: ImageVector,
    label: String,
    hint: Hint,
    free: Boolean,
    onHint: (Hint) -> Unit,
    modifier: Modifier,
) {
    FilledTonalButton(
        onClick = { onHint(hint) },
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
        modifier = modifier,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(
            text = if (free) stringResource(R.string.hint_free, label) else stringResource(R.string.hint_with_cost, label, hint.cost),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}
