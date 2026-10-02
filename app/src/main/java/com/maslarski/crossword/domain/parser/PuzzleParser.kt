package com.maslarski.crossword.domain.parser

import com.maslarski.crossword.domain.model.Difficulty
import com.maslarski.crossword.domain.model.Direction
import com.maslarski.crossword.domain.model.Puzzle
import com.maslarski.crossword.domain.model.PuzzleType
import com.maslarski.crossword.domain.model.Word
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

class PuzzleFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * JSON puzzle format (see tools/puzzle_builder.py to generate it):
 *
 * ```json
 * {
 *   "id": "easy-01", "title": "Beach Day", "author": "…", "difficulty": "EASY", "order": 1,
 *   "grid": ["SUN", "A#O", "DOT"],         // A–Z letters are the solution, '#' is a block
 *   "clues": { "across": { "1": "…" }, "down": { "1": "…" } }
 * }
 * ```
 *
 * Clue numbers are derived from the grid with standard crossword numbering and every answer slot
 * (a run of 2+ open cells) must have exactly one clue.
 */
class PuzzleParser(private val json: Json = DefaultJson) {

    @Serializable
    private data class PuzzleDto(
        val id: String,
        val title: String,
        val author: String = "",
        val difficulty: String,
        val order: Int = 0,
        val grid: List<String>,
        val clues: CluesDto,
    )

    @Serializable
    private data class CluesDto(
        val across: Map<String, String> = emptyMap(),
        val down: Map<String, String> = emptyMap(),
    )

    fun parse(text: String, type: PuzzleType): Puzzle {
        val dto = try {
            json.decodeFromString<PuzzleDto>(text)
        } catch (e: SerializationException) {
            throw PuzzleFormatException("Malformed puzzle JSON: ${e.message}", e)
        } catch (e: IllegalArgumentException) {
            throw PuzzleFormatException("Malformed puzzle JSON: ${e.message}", e)
        }
        return build(dto, type)
    }

    /** Validates a grid built in code, e.g. a generated daily puzzle, with the same rules as a JSON file. */
    fun fromGrid(
        id: String,
        title: String,
        author: String,
        difficulty: Difficulty,
        type: PuzzleType,
        grid: List<String>,
        across: Map<Int, String>,
        down: Map<Int, String>,
    ): Puzzle = build(
        PuzzleDto(
            id = id,
            title = title,
            author = author,
            difficulty = difficulty.name,
            grid = grid,
            clues = CluesDto(across.mapKeys { it.key.toString() }, down.mapKeys { it.key.toString() }),
        ),
        type,
    )

    private fun build(dto: PuzzleDto, type: PuzzleType): Puzzle {
        val id = dto.id.trim()
        fail(id.isNotEmpty() && id.all { it.isLetterOrDigit() || it == '-' || it == '_' }) { "Invalid puzzle id '${dto.id}'" }
        val difficulty = Difficulty.entries.firstOrNull { it.name.equals(dto.difficulty, ignoreCase = true) }
            ?: throw PuzzleFormatException("$id: unknown difficulty '${dto.difficulty}'")
        val rows = dto.grid.size
        fail(rows in 2..MAX_SIZE) { "$id: grid must have 2..$MAX_SIZE rows" }
        val cols = dto.grid.first().length
        fail(cols in 2..MAX_SIZE) { "$id: grid must have 2..$MAX_SIZE columns" }
        fail(dto.grid.all { it.length == cols }) { "$id: grid rows must all have $cols columns" }
        val solution = dto.grid.joinToString("").uppercase()
        fail(solution.all { it in 'A'..'Z' || it == Puzzle.BLOCK }) { "$id: grid may only contain A-Z and '${Puzzle.BLOCK}'" }

        val acrossClues = dto.clues.across.mapKeys { (k, _) -> k.toIntOrNull() ?: throw PuzzleFormatException("$id: bad clue number '$k'") }
        val downClues = dto.clues.down.mapKeys { (k, _) -> k.toIntOrNull() ?: throw PuzzleFormatException("$id: bad clue number '$k'") }

        fun open(r: Int, c: Int) = r in 0 until rows && c in 0 until cols && solution[r * cols + c] != Puzzle.BLOCK

        val words = mutableListOf<Word>()
        var number = 0
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                if (!open(r, c)) continue
                val startsAcross = !open(r, c - 1) && open(r, c + 1)
                val startsDown = !open(r - 1, c) && open(r + 1, c)
                if (!startsAcross && !startsDown) continue
                number++
                if (startsAcross) words += slot(id, solution, cols, number, Direction.ACROSS, r, c, acrossClues) { rr, cc -> open(rr, cc) }
                if (startsDown) words += slot(id, solution, cols, number, Direction.DOWN, r, c, downClues) { rr, cc -> open(rr, cc) }
            }
        }
        fail(words.isNotEmpty()) { "$id: grid contains no words" }
        val usedAcross = words.filter { it.direction == Direction.ACROSS }.map { it.number }.toSet()
        val usedDown = words.filter { it.direction == Direction.DOWN }.map { it.number }.toSet()
        (acrossClues.keys - usedAcross).firstOrNull()?.let { throw PuzzleFormatException("$id: clue $it Across has no slot in the grid") }
        (downClues.keys - usedDown).firstOrNull()?.let { throw PuzzleFormatException("$id: clue $it Down has no slot in the grid") }
        val covered = words.flatMapTo(mutableSetOf()) { it.cells }
        fail(solution.indices.all { solution[it] == Puzzle.BLOCK || it in covered }) { "$id: every open cell must belong to a word" }

        return Puzzle(
            id = id,
            title = dto.title.trim(),
            author = dto.author.trim(),
            difficulty = difficulty,
            type = type,
            order = dto.order,
            rows = rows,
            cols = cols,
            solution = solution,
            words = words,
        )
    }

    private inline fun slot(
        id: String,
        solution: String,
        cols: Int,
        number: Int,
        direction: Direction,
        row: Int,
        col: Int,
        clues: Map<Int, String>,
        open: (Int, Int) -> Boolean,
    ): Word {
        val (dr, dc) = if (direction == Direction.ACROSS) 0 to 1 else 1 to 0
        val cells = mutableListOf<Int>()
        var r = row
        var c = col
        while (open(r, c)) {
            cells += r * cols + c
            r += dr
            c += dc
        }
        val clue = clues[number]?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw PuzzleFormatException("$id: missing clue for $number ${direction.name.lowercase().replaceFirstChar { it.uppercase() }}")
        return Word(number, direction, row, col, cells.map { solution[it] }.joinToString(""), clue, cells)
    }

    private inline fun fail(condition: Boolean, message: () -> String) {
        if (!condition) throw PuzzleFormatException(message())
    }

    companion object {
        const val MAX_SIZE = 25
        val DefaultJson = Json { ignoreUnknownKeys = true }
    }
}
