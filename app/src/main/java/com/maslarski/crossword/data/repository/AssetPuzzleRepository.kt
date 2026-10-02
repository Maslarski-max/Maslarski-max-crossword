package com.maslarski.crossword.data.repository

import android.content.res.AssetManager
import android.util.Log
import com.maslarski.crossword.domain.daily.DailyPuzzleGenerator
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
 * The Daily Challenge is generated per date from `assets/puzzles/wordbank.txt`; the bundled daily files are the
 * fallback if generation fails.
 */
class AssetPuzzleRepository(
    private val assets: AssetManager,
    private val parser: PuzzleParser,
    private val io: CoroutineDispatcher,
) : PuzzleRepository {

    private data class Catalog(val levels: List<Puzzle>, val daily: List<Puzzle>, val generator: DailyPuzzleGenerator) {
        val byId: Map<String, Puzzle> = (levels + daily).associateBy { it.id }
    }

    private val mutex = Mutex()
    private var catalog: Catalog? = null
    private val generated = LinkedHashMap<LocalDate, Puzzle?>()

    override suspend fun levels(): List<Puzzle> = catalog().levels

    override suspend fun puzzle(id: String): Puzzle? =
        catalog().byId[id] ?: DailyPuzzleGenerator.dateOf(id)?.let { generated(it) }?.takeIf { it.id == id }

    override suspend fun dailyPuzzle(date: LocalDate): Puzzle? {
        generated(date)?.let { return it }
        val pool = catalog().daily.ifEmpty { catalog().levels }
        if (pool.isEmpty()) return null
        return pool[Math.floorMod(date.toEpochDay(), pool.size.toLong()).toInt()]
    }

    private suspend fun generated(date: LocalDate): Puzzle? {
        val generator = catalog().generator
        return mutex.withLock {
            if (generated.containsKey(date)) return@withLock generated[date]
            withContext(io) { generator.generate(date) }.also {
                generated[date] = it
                if (generated.size > GENERATED_CACHE) generated.remove(generated.keys.first())
            }
        }
    }

    private suspend fun catalog(): Catalog = mutex.withLock {
        catalog ?: withContext(io) {
            Catalog(
                levels = load(LEVELS_DIR, PuzzleType.LEVEL).sortedWith(compareBy({ it.order }, { it.id })),
                daily = load(DAILY_DIR, PuzzleType.DAILY).sortedBy { it.id },
                generator = DailyPuzzleGenerator(loadBank(), parser),
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

    private fun loadBank() = try {
        assets.open(WORD_BANK).bufferedReader().use { DailyPuzzleGenerator.parseBank(it.readText()) }
    } catch (e: IOException) {
        Log.e(TAG, "Couldn't read $WORD_BANK", e)
        emptyList()
    }

    private companion object {
        const val WORD_BANK = "puzzles/wordbank.txt"
        const val GENERATED_CACHE = 8
        const val TAG = "PuzzleRepository"
        const val LEVELS_DIR = "puzzles/levels"
        const val DAILY_DIR = "puzzles/daily"
    }
}
