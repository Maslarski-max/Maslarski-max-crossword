package com.maslarski.crossword.data.repository

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Each committed wallet change in order; Room may merge several writes into one balance emission. */
class CoinLedger {
    private val changes = MutableSharedFlow<Int>(extraBufferCapacity = Channel.UNLIMITED)

    fun observe(): Flow<Int> = changes.asSharedFlow()

    fun record(delta: Int) {
        if (delta != 0) changes.tryEmit(delta)
    }
}
