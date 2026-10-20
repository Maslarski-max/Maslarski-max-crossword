package com.maslarski.crossword.data.local

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
        EntitlementEntity::class,
        PurchaseCreditEntity::class,
        ReviewPromptEntity::class,
    ],
    version = 8,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4), AutoMigration(from = 4, to = 5), AutoMigration(from = 5, to = 6), AutoMigration(from = 7, to = 8)],
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
    abstract fun entitlementDao(): EntitlementDao
    abstract fun reviewPromptDao(): ReviewPromptDao

    companion object {
        const val NAME = "crossword.db"

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "UPDATE level_progress SET completed = 0, bestScore = 0, bestTimeSeconds = NULL, " +
                        "stars = 0, completedAt = NULL",
                )
                db.execSQL("DELETE FROM board_progress WHERE sessionId LIKE 'level:%'")
            }
        }
    }
}
