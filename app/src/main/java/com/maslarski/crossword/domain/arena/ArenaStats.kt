package com.maslarski.crossword.domain.arena

import com.maslarski.crossword.domain.model.Difficulty

data class ArenaMatch(val id: Long, val state: ArenaState)

data class ArenaResultRecord(val difficulty: Difficulty, val outcome: ArenaOutcome, val playerScore: Int)

data class ArenaResult(
    val outcome: ArenaOutcome,
    val playerScore: Int,
    val opponentScore: Int,
    val coinsEarned: Int,
)

/** Arena career stats, kept apart from classic progress. A forfeited match counts as a loss. */
data class ArenaStats(
    val played: Int = 0,
    val wins: Int = 0,
    val losses: Int = 0,
    val draws: Int = 0,
    val bestScore: Int = 0,
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val unlocked: Set<Difficulty> = setOf(Difficulty.EASY),
) {
    companion object {
        /** [results] must be ordered newest first. Beating an opponent unlocks the next tier. */
        fun from(results: List<ArenaResultRecord>): ArenaStats {
            var best = 0
            var run = 0
            results.asReversed().forEach { result ->
                run = if (result.outcome == ArenaOutcome.WON) run + 1 else 0
                best = maxOf(best, run)
            }
            val beaten = results.filter { it.outcome == ArenaOutcome.WON }.map { it.difficulty }.toSet()
            val unlocked = buildSet {
                add(Difficulty.EASY)
                if (beaten.isNotEmpty()) add(Difficulty.MEDIUM)
                if (Difficulty.MEDIUM in beaten || Difficulty.HARD in beaten) add(Difficulty.HARD)
            }
            return ArenaStats(
                played = results.size,
                wins = results.count { it.outcome == ArenaOutcome.WON },
                losses = results.count { it.outcome == ArenaOutcome.LOST || it.outcome == ArenaOutcome.FORFEIT },
                draws = results.count { it.outcome == ArenaOutcome.DRAW },
                bestScore = results.maxOfOrNull { it.playerScore } ?: 0,
                currentStreak = results.takeWhile { it.outcome == ArenaOutcome.WON }.size,
                bestStreak = best,
                unlocked = unlocked,
            )
        }
    }
}
