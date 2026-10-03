package com.maslarski.crossword.data.repository

import com.maslarski.crossword.domain.model.CoinChange
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Committed wallet changes not yet shown, in commit order. Room may merge several writes into one balance emission,
 * so every wallet write goes through [commit]. A change stays queued until [acknowledge], so a collector that starts
 * late or is recreated still gets it.
 */
class CoinLedger(private val capacity: Int = MAX_PENDING) {
    private val lock = Mutex()
    private val queue = MutableStateFlow<List<CoinChange>>(emptyList())
    private var lastId = 0L

    val pending: StateFlow<List<CoinChange>> = queue.asStateFlow()

    /** Runs [write] and queues its [delta] under one lock shared by all wallet writers, so queue order is commit order. */
    suspend fun <T> commit(write: suspend () -> T, delta: (T) -> Int): T = lock.withLock {
        write().also { result ->
            val change = delta(result)
            if (change != 0) queue.update { (it + CoinChange(++lastId, change)).takeLast(capacity) }
        }
    }

    fun acknowledge(id: Long) = queue.update { changes -> changes.filterNot { it.id == id } }

    private companion object {
        const val MAX_PENDING = 50
    }
}
