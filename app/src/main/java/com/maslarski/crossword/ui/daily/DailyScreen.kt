package com.maslarski.crossword.ui.daily

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maslarski.crossword.R
import com.maslarski.crossword.ui.components.CoinChip
import com.maslarski.crossword.ui.game.difficultyLabel
import com.maslarski.crossword.ui.home.PuzzleCard
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.WeekFields

/** Today's Daily Challenge plus a month calendar that stars every solved day. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyScreen(
    onBack: () -> Unit,
    onPlay: (sessionId: String, puzzleId: String) -> Unit,
    viewModel: DailyViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.daily_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) }
                },
                actions = { CoinChip(state.coins, Modifier.padding(end = 12.dp)) },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            val today = state.today
            val month = state.month
            if (state.loading || today == null || month == null) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
                return@Box
            }
            Column(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                state.challenge?.let { ChallengeCard(it, today, state.streak, onPlay) }
                    ?: Text(stringResource(R.string.daily_unavailable), style = MaterialTheme.typography.bodyLarge)
                CalendarCard(
                    month = month,
                    today = today,
                    completed = state.completed,
                    canGoBack = state.canGoBack,
                    canGoForward = state.canGoForward,
                    onPrevious = viewModel::showPreviousMonth,
                    onNext = viewModel::showNextMonth,
                )
            }
        }
    }
}

@Composable
private fun ChallengeCard(card: PuzzleCard, today: LocalDate, streak: Int, onPlay: (String, String) -> Unit) {
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                today.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(card.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(difficultyLabel(card.difficulty), style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Rounded.LocalFireDepartment, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                Text(pluralStringResource(R.plurals.daily_streak, streak, streak), style = MaterialTheme.typography.bodyMedium)
            }
            if (card.completed) {
                Text(
                    pluralStringResource(R.plurals.home_daily_done, card.score, card.score),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else if (card.progress > 0f) {
                LinearProgressIndicator(progress = { card.progress }, modifier = Modifier.fillMaxWidth())
            }
            Button(onClick = { onPlay(card.sessionId, card.puzzleId) }, modifier = Modifier.align(Alignment.End)) {
                Text(
                    stringResource(
                        when {
                            card.completed -> R.string.home_view
                            card.progress > 0f -> R.string.home_resume
                            else -> R.string.daily_play_today
                        },
                    ),
                )
            }
        }
    }
}

@Composable
private fun CalendarCard(
    month: YearMonth,
    today: LocalDate,
    completed: Set<LocalDate>,
    canGoBack: Boolean,
    canGoForward: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val firstDay = WeekFields.of(locale).firstDayOfWeek
    val weekDays = (0L until 7L).map { firstDay.plus(it) }
    val leading = Math.floorMod(month.atDay(1).dayOfWeek.value - firstDay.value, 7)
    val days = List(leading) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
    val solved = days.count { it != null && it in completed }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrevious, enabled = canGoBack) {
                    Icon(Icons.Rounded.ChevronLeft, stringResource(R.string.daily_previous_month))
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        month.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale)).replaceFirstChar { it.titlecase(locale) },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        stringResource(R.string.daily_month_progress, solved, month.lengthOfMonth()),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onNext, enabled = canGoForward) {
                    Icon(Icons.Rounded.ChevronRight, stringResource(R.string.daily_next_month))
                }
            }
            Row(Modifier.fillMaxWidth()) {
                weekDays.forEach { day ->
                    Text(
                        day.getDisplayName(TextStyle.NARROW, locale),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                    )
                }
            }
            days.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    week.forEach { date ->
                        Box(Modifier.weight(1f).aspectRatio(1f)) {
                            if (date != null) DayCell(date, today, date in completed)
                        }
                    }
                    repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun DayCell(date: LocalDate, today: LocalDate, solved: Boolean) {
    val isToday = date == today
    val future = date.isAfter(today)
    val description = stringResource(
        when {
            solved -> R.string.daily_day_solved
            future -> R.string.daily_day_upcoming
            else -> R.string.daily_day_unsolved
        },
        date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)),
    )
    Surface(
        shape = CircleShape,
        color = if (solved) MaterialTheme.colorScheme.tertiaryContainer else Color.Transparent,
        contentColor = when {
            solved -> MaterialTheme.colorScheme.onTertiaryContainer
            future -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            else -> MaterialTheme.colorScheme.onSurface
        },
        border = if (isToday) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier.fillMaxSize().semantics { contentDescription = description },
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (solved) {
                Icon(Icons.Rounded.Star, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.fillMaxSize(0.8f))
                Text(
                    date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiary,
                )
            } else {
                Text(
                    date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}
