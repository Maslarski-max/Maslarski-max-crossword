package com.maslarski.crossword.data

import androidx.room.migration.AutoMigrationSpec
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.maslarski.crossword.data.local.CrosswordDatabase
import com.maslarski.crossword.data.local.CrosswordDatabase_Impl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LevelProgressMigrationTest {
    private val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        CrosswordDatabase::class.java,
        emptyList<AutoMigrationSpec>(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrationResetsLevelCompletionKeepsUnlocksAndDeletesOnlyLevelBoards() {
        helper.createDatabase(TEST_DATABASE, 6).apply {
            execSQL(
                "INSERT INTO level_progress (puzzleId, levelOrder, unlocked, completed, bestScore, " +
                    "bestTimeSeconds, stars, completedAt) VALUES ('level-008', 8, 1, 1, 120, 45, 3, 1000)",
            )
            execSQL(
                "INSERT INTO level_progress (puzzleId, levelOrder, unlocked, completed, bestScore, " +
                    "bestTimeSeconds, stars, completedAt) VALUES ('level-009', 9, 0, 1, 80, 60, 2, 1100)",
            )
            insertBoard(this, "level:level-008", "level-008")
            insertBoard(this, "daily:2026-10-01", "daily-01")
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DATABASE,
            7,
            true,
            CrosswordDatabase.MIGRATION_6_7,
        )
        try {
            migrated.query(
                "SELECT unlocked, completed, bestScore, bestTimeSeconds, stars, completedAt " +
                    "FROM level_progress ORDER BY levelOrder",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
                assertReset(cursor)
                assertTrue(cursor.moveToNext())
                assertEquals(0, cursor.getInt(0))
                assertReset(cursor)
                assertFalse(cursor.moveToNext())
            }
            migrated.query("SELECT COUNT(*) FROM board_progress WHERE sessionId LIKE 'level:%'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
            migrated.query("SELECT COUNT(*) FROM board_progress WHERE sessionId LIKE 'daily:%'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
            }
        } finally {
            migrated.close()
        }
    }

    @Test
    fun migration7To8PreservesProgressAndWalletAndCreatesEmptyReviewPromptTable() {
        helper.createDatabase(TEST_DATABASE_REVIEW_PROMPT, 7).apply {
            execSQL(
                "INSERT INTO level_progress (puzzleId, levelOrder, unlocked, completed, bestScore, " +
                    "bestTimeSeconds, stars, completedAt) VALUES ('level-007', 7, 1, 1, 87, 30, 3, 123456)",
            )
            execSQL("INSERT INTO wallet (id, coins, lifetimeEarned) VALUES (0, 4321, 9876)")
            close()
        }

        val migrations = CrosswordDatabase_Impl().createAutoMigrations(emptyMap()) +
            CrosswordDatabase.MIGRATION_6_7
        val migrated = helper.runMigrationsAndValidate(
            TEST_DATABASE_REVIEW_PROMPT,
            8,
            true,
            *migrations.toTypedArray(),
        )
        try {
            migrated.query(
                "SELECT puzzleId, levelOrder, unlocked, completed, bestScore, bestTimeSeconds, stars, completedAt " +
                    "FROM level_progress",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("level-007", cursor.getString(0))
                assertEquals(7, cursor.getInt(1))
                assertEquals(1, cursor.getInt(2))
                assertEquals(1, cursor.getInt(3))
                assertEquals(87, cursor.getInt(4))
                assertEquals(30, cursor.getInt(5))
                assertEquals(3, cursor.getInt(6))
                assertEquals(123456L, cursor.getLong(7))
                assertFalse(cursor.moveToNext())
            }
            migrated.query("SELECT coins, lifetimeEarned FROM wallet WHERE id = 0").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(4321, cursor.getInt(0))
                assertEquals(9876, cursor.getInt(1))
                assertFalse(cursor.moveToNext())
            }
            migrated.query("SELECT COUNT(*) FROM review_prompt").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
        } finally {
            migrated.close()
        }
    }

    private fun insertBoard(db: SupportSQLiteDatabase, sessionId: String, puzzleId: String) {
        db.execSQL(
            "INSERT INTO board_progress (sessionId, puzzleId, solutionFingerprint, entries, revealedMask, " +
                "incorrectMask, selectedIndex, direction, elapsedSeconds, checksUsed, completed, score, stars, " +
                "updatedAt) VALUES ('$sessionId', '$puzzleId', 'fp', '', '', '', 0, 'ACROSS', 0, 0, 0, 0, 0, 1)",
        )
    }

    private fun assertReset(cursor: android.database.Cursor) {
        assertEquals(0, cursor.getInt(1))
        assertEquals(0, cursor.getInt(2))
        assertTrue(cursor.isNull(3))
        assertEquals(0, cursor.getInt(4))
        assertTrue(cursor.isNull(5))
    }

    private companion object {
        const val TEST_DATABASE = "level-progress-migration"
        const val TEST_DATABASE_REVIEW_PROMPT = "review-prompt-migration-v8"
    }
}
