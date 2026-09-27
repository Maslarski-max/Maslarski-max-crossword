package com.maslarski.crossword.data.repository

import android.content.res.AssetManager
import android.util.Log
import com.maslarski.crossword.domain.model.Puzzle
import com.maslarski.crossword.domain.model.PuzzleType
import com.maslarski.crossword.domain.parser.PuzzleFormatException
import com.maslarski.crossword.domain.parser.PuzzleParser
import com.maslarski.crossword.domain.repository.PuzzleRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.LocalDate

/**
 * Loads every `*.json` file under `assets/puzzles/levels` and `assets/puzzles/daily`. Dropping a new
 * file into either folder is all it takes to ship a new level; invalid files are logged and skipped.
 */
class AssetPuzzleRepository(
    private val assets: AssetManager,
    private val parser: PuzzleParser,
    private val io: CoroutineDispatcher,
) : PuzzleRepository {

    private data class Catalog(val levels: List<Puzzle>, val daily: List<Puzzle>) {
        val byId: Map<String, Puzzle> = (levels + daily).associateBy { it.id }
    }

    private val mutex = Mutex()
    private var catalog: Catalog? = null

    override suspend fun levels(): List<Puzzle> = catalog().levels

    override suspend fun puzzle(id: String): Puzzle? = catalog().byId[id]

    override suspend fun dailyPuzzle(date: LocalDate): Puzzle? {
        val pool = catalog().daily.ifEmpty { catalog().levels }
        if (pool.isEmpty()) return null
        return pool[Math.floorMod(date.toEpochDay(), pool.size.toLong()).toInt()]
    }

    private suspend fun catalog(): Catalog = mutex.withLock {
        catalog ?: withContext(io) {
            Catalog(
                levels = load(LEVELS_DIR, PuzzleType.LEVEL).sortedWith(compareBy({ it.order }, { it.id })),
                daily = load(DAILY_DIR, PuzzleType.DAILY).sortedBy { it.id },
            )
        }.also { catalog = it }
    }

    private fun load(dir: String, type: PuzzleType): List<Puzzle> {
        val files = assets.list(dir).orEmpty().filter { it.endsWith(".json") }
        return files.mapNotNull { name ->
            try {
                assets.open("$dir/$name").bufferedReader().use { parser.parse(it.readText(), type) }
            } catch (e: PuzzleFormatException) {
                Log.e(TAG, "Skipping invalid puzzle $dir/$name", e)
                null
            } catch (e: IOException) {
                Log.e(TAG, "Couldn't read puzzle $dir/$name", e)
                null
            }
        }.distinctBy { it.id }
    }

    private companion object {
        const val TAG = "PuzzleRepository"
        const val LEVELS_DIR = "puzzles/levels"
        const val DAILY_DIR = "puzzles/daily"
    }
}
