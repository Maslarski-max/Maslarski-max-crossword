package com.maslarski.crossword.domain.model

/** One committed wallet change; [id] increases in commit order. */
data class CoinChange(val id: Long, val delta: Int)
