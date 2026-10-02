package com.maslarski.crossword.domain.profile

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/** The current local date, re-checked at least every minute so it rolls over at midnight. */
fun localDates(clock: Clock): Flow<LocalDate> = flow {
    while (true) {
        val now = LocalDateTime.now(clock)
        emit(now.toLocalDate())
        val untilMidnight = Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay()).toMillis()
        delay(untilMidnight.coerceIn(1, DATE_POLL_MS))
    }
}.distinctUntilChanged()

private const val DATE_POLL_MS = 60_000L
