package com.maslarski.crossword.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.maslarski.crossword.R

private val Rows = listOf("QWERTYUIOP", "ASDFGHJKL", "ZXCVBNM")

/** In-app A–Z keyboard so the system IME never covers the grid. */
@Composable
fun LetterKeyboard(
    onLetter: (Char) -> Unit,
    onDelete: () -> Unit,
    onToggleDirection: () -> Unit,
    hapticsEnabled: Boolean,
    modifier: Modifier = Modifier,
    keyHeight: Dp = 50.dp,
) {
    val haptics = LocalHapticFeedback.current
    val feedback = { if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.KeyboardTap) }
    Column(
        modifier = modifier.widthIn(max = 600.dp).fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Rows.forEachIndexed { rowIndex, row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                if (rowIndex == 1) Spacer(0.5f)
                if (rowIndex == 2) {
                    IconKey(Icons.Rounded.SwapVert, stringResource(R.string.key_toggle_direction), keyHeight, 1.5f) {
                        feedback(); onToggleDirection()
                    }
                }
                row.forEach { letter ->
                    LetterKey(letter, keyHeight) { feedback(); onLetter(letter) }
                }
                if (rowIndex == 2) {
                    IconKey(Icons.AutoMirrored.Rounded.Backspace, stringResource(R.string.key_delete), keyHeight, 1.5f) {
                        feedback(); onDelete()
                    }
                }
                if (rowIndex == 1) Spacer(0.5f)
            }
        }
    }
}

@Composable
private fun RowScope.Spacer(weight: Float) = Box(Modifier.weight(weight))

@Composable
private fun RowScope.LetterKey(letter: Char, height: Dp, onClick: () -> Unit) {
    KeySurface(height, 1f, MaterialTheme.colorScheme.surfaceContainerHighest, letter.toString(), onClick) {
        Text(
            letter.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun RowScope.IconKey(icon: ImageVector, label: String, height: Dp, weight: Float, onClick: () -> Unit) {
    KeySurface(height, weight, MaterialTheme.colorScheme.secondaryContainer, label, onClick) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

@Composable
private fun RowScope.KeySurface(
    height: Dp,
    weight: Float,
    color: Color,
    label: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, label = "keyScale")
    val shape = RoundedCornerShape(8.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .weight(weight)
            .height(height)
            .padding(horizontal = 3.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(color)
            .clickable(interactionSource = interaction, indication = ripple(), role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
    ) { content() }
}
