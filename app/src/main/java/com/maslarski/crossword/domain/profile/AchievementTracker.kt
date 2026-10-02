package com.maslarski.crossword.domain.profile

import com.maslarski.crossword.domain.repository.ProfileRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.time.Clock

/**
 * Watches the player's stats for the whole app lifetime and stores each achievement the moment it is earned,
 * whichever screen earned it. Fresh unlocks are queued in [unlocks] until the UI shows them.
 */
class AchievementTracker(
    private val profile: ProfileRepository,
    private val clock: Clock,
    private val scope: CoroutineScope,
) {
    private val pending = Channel<Achievement>(Channel.UNLIMITED)
    val unlocks: Flow<Achievement> = pending.receiveAsFlow()

    private var started = false

    @OptIn(ExperimentalCoroutinesApi::class)
    fun start() {
        if (started) return
        started = true
        scope.launch {
            localDates(clock).flatMapLatest(profile::observeProfile).collect { stats ->
                profile.unlockMet(stats).forEach { pending.trySend(it) }
            }
        }
    }
}
