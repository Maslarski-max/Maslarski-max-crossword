package com.maslarski.crossword.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Toll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.maslarski.crossword.R

/** A coin decision the player has to acknowledge: a purchase to confirm, or one they can't afford. */
sealed interface CoinPrompt {
    data class NotEnoughCoins(val cost: Int) : CoinPrompt
    data class UnlockLevel(val puzzleId: String, val title: String, val cost: Int) : CoinPrompt
}

@Composable
fun CoinPromptDialog(prompt: CoinPrompt, balance: Int, onUnlock: (puzzleId: String) -> Unit, onDismiss: () -> Unit) {
    when (prompt) {
        is CoinPrompt.NotEnoughCoins -> AlertDialog(
            onDismissRequest = onDismiss,
            icon = { Icon(Icons.Rounded.Toll, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(32.dp)) },
            title = { Text(stringResource(R.string.not_enough_coins_title)) },
            text = { Text(stringResource(R.string.not_enough_coins_message, prompt.cost, balance)) },
            confirmButton = { Button(onClick = onDismiss) { Text(stringResource(R.string.ok)) } },
        )
        is CoinPrompt.UnlockLevel -> AlertDialog(
            onDismissRequest = onDismiss,
            icon = { Icon(Icons.Rounded.LockOpen, contentDescription = null, modifier = Modifier.size(32.dp)) },
            title = { Text(stringResource(R.string.unlock_level_title, prompt.title)) },
            text = { Text(stringResource(R.string.unlock_level_message, prompt.cost, balance)) },
            confirmButton = {
                Button(onClick = { onUnlock(prompt.puzzleId) }) { Text(stringResource(R.string.unlock_level_confirm, prompt.cost)) }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
