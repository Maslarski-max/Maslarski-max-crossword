package com.maslarski.crossword.ui.components

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.maslarski.crossword.domain.model.CoinChange
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CoinChangeToastTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun defaultPauseShowsAndAcknowledgesEveryChange() {
        val pending = MutableStateFlow(listOf(CoinChange(1, -10), CoinChange(2, 20)))
        val shown = mutableListOf<Long>()
        compose.setContent {
            CoinChangeToast(pending, onShown = { id ->
                shown += id
                pending.value = pending.value.filterNot { it.id == id }
            })
        }
        compose.mainClock.advanceTimeBy(5_000)
        compose.waitForIdle()
        assertEquals(listOf(1L, 2L), shown)
    }

    @Test
    fun pauseSourceFinishingPausedKeepsChangeQueued() {
        assertEquals(emptyList<Long>(), shownWith(flowOf(true)))
    }

    @Test
    fun emptyPauseSourceKeepsChangeQueued() {
        assertEquals(emptyList<Long>(), shownWith(emptyFlow()))
    }

    @Test
    fun replacingAFinishedPausedSourceResumesTheQueuedChange() {
        val pending = MutableStateFlow(listOf(CoinChange(1, -10)))
        val shown = mutableListOf<Long>()
        val paused = mutableStateOf(flowOf(true))
        compose.setContent {
            CoinChangeToast(pending, onShown = { shown += it }, paused = paused.value)
        }
        compose.mainClock.advanceTimeBy(5_000)
        compose.waitForIdle()
        assertEquals(emptyList<Long>(), shown)

        paused.value = flowOf(false)
        compose.mainClock.advanceTimeBy(5_000)
        compose.waitForIdle()
        assertEquals(listOf(1L), shown)
    }

    private fun shownWith(paused: Flow<Boolean>): List<Long> {
        val pending = MutableStateFlow(listOf(CoinChange(1, -10)))
        val shown = mutableListOf<Long>()
        compose.setContent {
            CoinChangeToast(pending, onShown = { shown += it }, paused = paused)
        }
        compose.mainClock.advanceTimeBy(5_000)
        compose.waitForIdle()
        return shown
    }
}
