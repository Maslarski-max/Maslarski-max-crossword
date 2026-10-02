package com.maslarski.crossword.data.repository

import androidx.room.withTransaction
import com.maslarski.crossword.data.local.AchievementEntity
import com.maslarski.crossword.data.local.CrosswordDatabase
import com.maslarski.crossword.data.local.LoginStreakEntity
import com.maslarski.crossword.domain.engine.Streaks
import com.maslarski.crossword.domain.profile.Achievement
import com.maslarski.crossword.domain.profile.Achievements
import com.maslarski.crossword.domain.profile.LoginReward
import com.maslarski.crossword.domain.profile.LoginStreak
import com.maslarski.crossword.domain.profile.LoginStreaks
import com.maslarski.crossword.domain.profile.PlayerProfile
import com.maslarski.crossword.domain.repository.ArenaRepository
import com.maslarski.crossword.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate

class RoomProfileRepository(
    private val db: CrosswordDatabase,
    private val arena: ArenaRepository,
    private val clock: Clock,
) : ProfileRepository {

    private val highScores = db.highScoreDao()
    private val daily = db.dailyPuzzleDao()
    private val wallet = db.walletDao()
    private val achievements = db.achievementDao()
    private val logins = db.loginStreakDao()

    override fun observeProfile(today: LocalDate): Flow<PlayerProfile> {
        val classic = combine(highScores.observeSolvedCount(), highScores.observeFastest(), daily.observeCompletedDates()) { solved, fastest, dates ->
            Triple(solved, fastest, dates.map(LocalDate::parse))
        }
        return combine(classic, wallet.observeLifetimeEarned(), arena.observeStats(), logins.observe()) { (solved, fastest, dates), earned, arenaStats, login ->
            val streak = login?.toDomain()
            PlayerProfile(
                puzzlesSolved = solved,
                dailySolved = dates.size,
                dailyStreak = Streaks.current(dates, today),
                fastestSolveSeconds = fastest,
                coinsEarned = earned ?: 0,
                arena = arenaStats,
                loginStreak = streak?.takeIf { !it.lastDay.isBefore(today.minusDays(1)) }?.streak ?: 0,
                bestLoginStreak = streak?.bestStreak ?: 0,
            )
        }
    }

    override fun observeUnlocked(): Flow<Map<Achievement, Long>> = achievements.observeAll().map { rows ->
        rows.mapNotNull { row -> Achievement.byId(row.id)?.let { it to row.unlockedAt } }.toMap()
    }

    override suspend fun unlockMet(profile: PlayerProfile): List<Achievement> {
        val now = clock.millis()
        return Achievements.met(profile).filter { achievements.insertIgnore(AchievementEntity(it.id, now)) != -1L }
    }

    override suspend fun claimDailyLogin(today: LocalDate): LoginReward? = db.withTransaction {
        val next = LoginStreaks.next(logins.get()?.toDomain(), today) ?: return@withTransaction null
        logins.upsert(LoginStreakEntity(lastDay = next.lastDay.toString(), streak = next.streak, bestStreak = next.bestStreak, totalDays = next.totalDays))
        val coins = LoginStreaks.reward(next.streak)
        wallet.earn(coins)
        LoginReward(next.streak, LoginStreaks.cycleDay(next.streak), coins)
    }

    private fun LoginStreakEntity.toDomain() = LoginStreak(LocalDate.parse(lastDay), streak, bestStreak, totalDays)
}
