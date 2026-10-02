package com.maslarski.crossword.domain.daily

import com.maslarski.crossword.domain.model.Difficulty
import com.maslarski.crossword.domain.model.Puzzle
import com.maslarski.crossword.domain.model.PuzzleType
import com.maslarski.crossword.domain.parser.PuzzleParser
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.random.Random

data class BankEntry(val answer: String, val clue: String)

/**
 * Builds the Daily Challenge for a calendar date from a word bank. The date seeds every random choice, so each day
 * gets its own puzzle and the same date always yields the same grid (as long as the bank is unchanged).
 * Difficulty rises through the week: Monday–Tuesday easy, Wednesday–Friday medium, weekends hard.
 */
class DailyPuzzleGenerator(private val bank: List<BankEntry>, private val parser: PuzzleParser = PuzzleParser()) {

    private data class Spec(val difficulty: Difficulty, val words: Int, val size: Int)

    private data class Placement(val entry: BankEntry, val row: Int, val col: Int, val across: Boolean)

    fun generate(date: LocalDate): Puzzle? {
        if (bank.size < MIN_BANK) return null
        val spec = specFor(date)
        val rng = Random(date.toEpochDay() * SEED_MULTIPLIER + SEED_SALT)
        val pool = bank.filter { it.answer.length in spec.difficulty.lengths }.shuffled(rng).take(spec.words * 2)
        val layout = (0 until ATTEMPTS).map { build(pool, spec, rng) }
            .maxWithOrNull(compareBy<Layout>({ it.placements.size.coerceAtMost(spec.words) }, { it.crossings }, { -it.area }))
            ?: return null
        if (layout.placements.size < MIN_WORDS) return null
        return toPuzzle(idFor(date), spec.difficulty, layout)
    }

    private fun specFor(date: LocalDate): Spec = when (date.dayOfWeek) {
        DayOfWeek.MONDAY, DayOfWeek.TUESDAY -> Spec(Difficulty.EASY, words = 10, size = 11)
        DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY -> Spec(Difficulty.MEDIUM, words = 13, size = 13)
        DayOfWeek.SATURDAY, DayOfWeek.SUNDAY, null -> Spec(Difficulty.HARD, words = 16, size = 15)
    }

    private val Difficulty.lengths: IntRange
        get() = when (this) {
            Difficulty.EASY -> 3..7
            Difficulty.MEDIUM -> 3..9
            Difficulty.HARD -> 4..11
        }

    private fun build(pool: List<BankEntry>, spec: Spec, rng: Random): Layout {
        val layout = Layout(spec.size)
        val pending = pool.map { it to -it.answer.length + rng.nextDouble() * 4 }.sortedBy { it.second }.map { it.first }.toMutableList()
        var progress = true
        while (pending.isNotEmpty() && progress && layout.placements.size < spec.words) {
            progress = false
            val iterator = pending.iterator()
            while (iterator.hasNext() && layout.placements.size < spec.words) {
                val entry = iterator.next()
                if (layout.placements.any { it.entry.answer == entry.answer }) {
                    iterator.remove()
                    continue
                }
                val options = layout.candidates(entry.answer)
                if (options.isEmpty()) continue
                val top = options.maxOf { it.second }
                val choices = if (rng.nextBoolean()) options.filter { it.second == top } else options
                layout.place(choices[rng.nextInt(choices.size)].first.copy(entry = entry))
                iterator.remove()
                progress = true
            }
        }
        return layout
    }

    private fun toPuzzle(id: String, difficulty: Difficulty, layout: Layout): Puzzle? {
        val r0 = layout.cells.keys.minOf { it.first }
        val c0 = layout.cells.keys.minOf { it.second }
        val r1 = layout.cells.keys.maxOf { it.first }
        val c1 = layout.cells.keys.maxOf { it.second }
        val grid = (r0..r1).map { r -> (c0..c1).map { c -> layout.cells[r to c] ?: Puzzle.BLOCK }.joinToString("") }
        val numbers = number(grid)
        val across = HashMap<Int, String>()
        val down = HashMap<Int, String>()
        layout.placements.forEach { p ->
            val number = numbers[(p.row - r0) to (p.col - c0)] ?: return null
            (if (p.across) across else down)[number] = p.entry.clue
        }
        return runCatching { parser.fromGrid(id, TITLE, AUTHOR, difficulty, PuzzleType.DAILY, grid, across, down) }.getOrNull()
    }

    private fun number(grid: List<String>): Map<Pair<Int, Int>, Int> {
        fun open(r: Int, c: Int) = r in grid.indices && c in grid[0].indices && grid[r][c] != Puzzle.BLOCK
        val numbers = HashMap<Pair<Int, Int>, Int>()
        var n = 0
        for (r in grid.indices) for (c in grid[0].indices) {
            if (!open(r, c)) continue
            if ((!open(r, c - 1) && open(r, c + 1)) || (!open(r - 1, c) && open(r + 1, c))) numbers[r to c] = ++n
        }
        return numbers
    }

    /** Freestyle layout: words only meet at perpendicular crossings and never touch side by side. */
    private class Layout(private val size: Int) {
        val cells = HashMap<Pair<Int, Int>, Char>()
        private val directions = HashMap<Pair<Int, Int>, MutableSet<Boolean>>()
        val placements = mutableListOf<Placement>()
        private var minRow = 0
        private var maxRow = 0
        private var minCol = 0
        private var maxCol = 0

        val crossings: Int get() = placements.sumOf { it.entry.answer.length } - cells.size
        val area: Int get() = if (cells.isEmpty()) 0 else (maxRow - minRow + 1) * (maxCol - minCol + 1)

        fun candidates(word: String): List<Pair<Placement, Int>> {
            val stub = BankEntry(word, "")
            if (placements.isEmpty()) return listOf(Placement(stub, 0, 0, across = true) to 0)
            val out = mutableListOf<Pair<Placement, Int>>()
            for ((cell, ch) in cells) {
                word.forEachIndexed { i, w ->
                    if (w != ch) return@forEachIndexed
                    for (across in BOOLEANS) {
                        val row = if (across) cell.first else cell.first - i
                        val col = if (across) cell.second - i else cell.second
                        val crossings = fits(word, row, col, across)
                        if (crossings > 0) out += Placement(stub, row, col, across) to crossings
                    }
                }
            }
            return out
        }

        private fun fits(word: String, row: Int, col: Int, across: Boolean): Int {
            val dr = if (across) 0 else 1
            val dc = if (across) 1 else 0
            if (cells.containsKey(row - dr to col - dc) || cells.containsKey(row + dr * word.length to col + dc * word.length)) return -1
            var crossings = 0
            for (i in word.indices) {
                val cell = row + dr * i to col + dc * i
                val existing = cells[cell]
                if (existing != null) {
                    if (existing != word[i] || across in directions.getValue(cell)) return -1
                    crossings++
                } else if (cells.containsKey(cell.first + dc to cell.second + dr) || cells.containsKey(cell.first - dc to cell.second - dr)) {
                    return -1
                }
            }
            if (crossings == word.length) return -1
            val endRow = row + dr * (word.length - 1)
            val endCol = col + dc * (word.length - 1)
            if (maxOf(maxRow, endRow) - minOf(minRow, row) + 1 > size || maxOf(maxCol, endCol) - minOf(minCol, col) + 1 > size) return -1
            return crossings
        }

        fun place(p: Placement) {
            if (placements.isEmpty()) {
                minRow = p.row
                maxRow = p.row
                minCol = p.col
                maxCol = p.col
            }
            p.entry.answer.forEachIndexed { i, ch ->
                val cell = (if (p.across) p.row else p.row + i) to (if (p.across) p.col + i else p.col)
                minRow = minOf(minRow, cell.first)
                maxRow = maxOf(maxRow, cell.first)
                minCol = minOf(minCol, cell.second)
                maxCol = maxOf(maxCol, cell.second)
                cells[cell] = ch
                directions.getOrPut(cell) { mutableSetOf() } += p.across
            }
            placements += p
        }
    }

    companion object {
        private const val PREFIX = "daily-"
        private const val TITLE = "Daily Challenge"
        private const val AUTHOR = "Crossword Team"
        private const val ATTEMPTS = 30
        private const val MIN_WORDS = 6
        private const val MIN_BANK = 20
        private const val SEED_MULTIPLIER = 6_364_136_223_846_793_005L
        private const val SEED_SALT = 1_442_695_040_888_963_407L
        private val BOOLEANS = booleanArrayOf(true, false)

        fun idFor(date: LocalDate): String = "$PREFIX$date"

        /** The date of a generated daily puzzle id, or null for any other id (including bundled `daily-01` files). */
        fun dateOf(id: String): LocalDate? =
            id.removePrefix(PREFIX).takeIf { id.startsWith(PREFIX) && it.length == ISO_DATE_LENGTH }
                ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

        /** Parses `ANSWER | clue` lines; blank lines and `#` comments are skipped. */
        fun parseBank(text: String): List<BankEntry> = text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && '|' in it }
            .map { line -> line.split('|', limit = 2).let { (a, c) -> BankEntry(a.trim().uppercase(), c.trim()) } }
            .filter { e -> e.answer.length >= 2 && e.answer.all { it in 'A'..'Z' } && e.clue.isNotEmpty() }
            .distinctBy { it.answer }
            .toList()

        private const val ISO_DATE_LENGTH = 10
    }
}
