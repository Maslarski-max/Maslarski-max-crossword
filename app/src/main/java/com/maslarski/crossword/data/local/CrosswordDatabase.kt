package com.maslarski.crossword.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        BoardProgressEntity::class,
        LevelProgressEntity::class,
        DailyPuzzleEntity::class,
        HighScoreEntity::class,
        WalletEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class CrosswordDatabase : RoomDatabase() {
    abstract fun boardProgressDao(): BoardProgressDao
    abstract fun levelProgressDao(): LevelProgressDao
    abstract fun dailyPuzzleDao(): DailyPuzzleDao
    abstract fun highScoreDao(): HighScoreDao
    abstract fun walletDao(): WalletDao

    companion object {
        const val NAME = "crossword.db"
    }
}
