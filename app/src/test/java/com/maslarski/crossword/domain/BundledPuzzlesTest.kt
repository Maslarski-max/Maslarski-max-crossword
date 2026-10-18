package com.maslarski.crossword.domain

import com.maslarski.crossword.domain.arena.ArenaLayout
import com.maslarski.crossword.domain.model.Difficulty
import com.maslarski.crossword.domain.model.PuzzleType
import com.maslarski.crossword.domain.parser.PuzzleParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Every puzzle shipped in assets must parse; a broken file would silently disappear from the app. */
class BundledPuzzlesTest {

    private val parser = PuzzleParser()
    private val root = File("src/main/assets/puzzles")

    private fun load(dir: String, type: PuzzleType) =
        File(root, dir).listFiles { f -> f.extension == "json" }.orEmpty().sortedBy { it.name }.map { parser.parse(it.readText(), type) }

    @Test
    fun `levels parse and span every difficulty`() {
        val levels = load("levels", PuzzleType.LEVEL).sortedBy { it.order }
        assertEquals(720, levels.size)
        assertEquals(Difficulty.entries.toSet(), levels.map { it.difficulty }.toSet())
        assertEquals((1..720).toList(), levels.map { it.order })
        levels.forEachIndexed { index, puzzle ->
            val order = index + 1
            val expectedWords = 6 + (order - 1) * 51 / 720
            val expectedSize = 8 + (order - 1) * 15 / 720
            assertEquals("$order word count", expectedWords, puzzle.orderedWords.size)
            assertEquals("$order row count", expectedSize, puzzle.rows)
            assertEquals("$order column count", expectedSize, puzzle.cols)
            if (order >= 481) assertTrue("$order has fewer than 40 words", puzzle.orderedWords.size >= 40)
            if (index > 0) {
                val previous = levels[index - 1]
                assertTrue(previous.orderedWords.size <= puzzle.orderedWords.size)
                assertTrue(previous.rows <= puzzle.rows)
                assertTrue(previous.cols <= puzzle.cols)
            }
        }
    }

    @Test
    fun `every level builds an Arena board with clues for all words`() {
        load("levels", PuzzleType.LEVEL).forEach { puzzle ->
            val arena = ArenaLayout(puzzle)
            assertEquals(puzzle.id, puzzle.orderedWords.size, arena.words.size)
            assertTrue(puzzle.id, arena.words.all { w -> arena.clueAt(w.clueCell) != null })
        }
    }

    @Test
    fun `daily puzzles parse`() {
        assertTrue(load("daily", PuzzleType.DAILY).isNotEmpty())
    }

    @Test
    fun `puzzle ids are unique`() {
        val all = load("levels", PuzzleType.LEVEL) + load("daily", PuzzleType.DAILY)
        assertEquals(all.size, all.map { it.id }.toSet().size)
    }

    @Test
    fun `a 20x20 level ships and builds an Arena board`() {
        val large = load("levels", PuzzleType.LEVEL).filter { it.rows >= 20 && it.cols >= 20 }
        assertTrue("expected a level of at least 20x20", large.isNotEmpty())
        large.forEach { puzzle ->
            val arena = ArenaLayout(puzzle)
            assertEquals(puzzle.rows + 1, arena.rows)
            assertEquals(puzzle.orderedWords.size, arena.words.size)
            assertTrue(arena.words.all { w -> arena.clueAt(w.clueCell) != null })
        }
    }
}
