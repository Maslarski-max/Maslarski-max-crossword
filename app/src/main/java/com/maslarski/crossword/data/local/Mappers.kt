package com.maslarski.crossword.data.local

import com.maslarski.crossword.domain.model.BoardState
import com.maslarski.crossword.domain.model.Direction
import com.maslarski.crossword.domain.model.LevelProgress
import com.maslarski.crossword.domain.model.SavedBoard

internal fun Set<Int>.toMask(length: Int): String = buildString(length) {
    for (i in 0 until length) append(if (i in this@toMask) '1' else '0')
}

internal fun String.maskToSet(): Set<Int> = indices.filterTo(mutableSetOf()) { this[it] == '1' }

fun BoardProgressEntity.toSavedBoard(): SavedBoard = SavedBoard(
    sessionId = sessionId,
    puzzleId = puzzleId,
    solutionFingerprint = solutionFingerprint,
    board = BoardState(
        entries = entries,
        revealed = revealedMask.maskToSet(),
        incorrect = incorrectMask.maskToSet(),
        selected = selectedIndex,
        direction = Direction.entries.firstOrNull { it.name == direction } ?: Direction.ACROSS,
    ),
    elapsedSeconds = elapsedSeconds,
    checksUsed = checksUsed,
    completed = completed,
    score = score,
    stars = stars,
    updatedAt = updatedAt,
)

fun LevelProgressEntity.toDomain(): LevelProgress = LevelProgress(
    puzzleId = puzzleId,
    order = levelOrder,
    unlocked = unlocked,
    completed = completed,
    bestScore = bestScore,
    bestTimeSeconds = bestTimeSeconds,
    stars = stars,
)
