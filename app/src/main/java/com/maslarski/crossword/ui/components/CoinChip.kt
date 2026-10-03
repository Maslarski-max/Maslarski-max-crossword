package com.maslarski.crossword.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Toll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.maslarski.crossword.R

@Composable
fun CoinChip(coins: Int, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val description = pluralStringResource(R.plurals.coins_balance, coins, coins)
    val shopLabel = stringResource(R.string.shop_title)
    val content: @Composable () -> Unit = { CoinChipContent(coins) }
    if (onClick == null) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = modifier.clearAndSetSemantics { contentDescription = description },
            content = content,
        )
    } else {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = modifier.clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
                onClick(label = shopLabel) {
                    onClick()
                    true
                }
            },
            content = content,
        )
    }
}

@Composable
private fun CoinChipContent(coins: Int) {
    Row(
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(Icons.Rounded.Toll, contentDescription = null, modifier = Modifier.size(18.dp))
        AnimatedContent(
            targetState = coins,
            transitionSpec = {
                val up = targetState > initialState
                slideInVertically { if (up) it else -it } togetherWith slideOutVertically { if (up) -it else it }
            },
            label = "coins",
        ) { value ->
            Text(value.toString(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
    }
}
