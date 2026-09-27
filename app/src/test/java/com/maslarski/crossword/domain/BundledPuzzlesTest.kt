package com.maslarski.crossword.domain

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
        val levels = load("levels", PuzzleType.LEVEL)
        assertTrue("expected at least 5 levels, found ${levels.size}", levels.size >= 5)
        assertEquals(Difficulty.entries.toSet(), levels.map { it.difficulty }.toSet())
        assertEquals(levels.size, levels.map { it.order }.toSet().size)
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
}
