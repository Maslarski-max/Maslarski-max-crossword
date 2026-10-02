package com.maslarski.crossword.data.local

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        BoardProgressEntity::class,
        LevelProgressEntity::class,
        DailyPuzzleEntity::class,
        HighScoreEntity::class,
        WalletEntity::class,
        ArenaMatchEntity::class,
        AchievementEntity::class,
        LoginStreakEntity::class,
    ],
    version = 4,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4)],
)
abstract class CrosswordDatabase : RoomDatabase() {
    abstract fun boardProgressDao(): BoardProgressDao
    abstract fun levelProgressDao(): LevelProgressDao
    abstract fun dailyPuzzleDao(): DailyPuzzleDao
    abstract fun highScoreDao(): HighScoreDao
    abstract fun walletDao(): WalletDao
    abstract fun arenaMatchDao(): ArenaMatchDao
    abstract fun achievementDao(): AchievementDao
    abstract fun loginStreakDao(): LoginStreakDao

    companion object {
        const val NAME = "crossword.db"
    }
}
