package com.maslarski.crossword.data.games

import android.app.Activity
import android.util.Log
import com.google.android.gms.games.PlayGames
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

sealed interface PlayGamesState {
    data object Checking : PlayGamesState
    data object SignedOut : PlayGamesState
    data class SignedIn(val displayName: String?) : PlayGamesState
}

@Singleton
class PlayGamesManager @Inject constructor() {

    private val mutableState = MutableStateFlow<PlayGamesState>(PlayGamesState.Checking)
    private val mutex = Mutex()
    val state: StateFlow<PlayGamesState> = mutableState.asStateFlow()

    suspend fun refresh(activity: Activity) = mutex.withLock {
        refreshLocked(activity)
    }

    private suspend fun refreshLocked(activity: Activity) {
        try {
            val result = PlayGames.getGamesSignInClient(activity).isAuthenticated().await()
            if (!result.isAuthenticated) {
                mutableState.value = PlayGamesState.SignedOut
                return
            }
            try {
                val displayName = PlayGames.getPlayersClient(activity).currentPlayer.await().displayName
                mutableState.value = PlayGamesState.SignedIn(displayName)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (exception: Exception) {
                mutableState.value = PlayGamesState.SignedIn(null)
                Log.w(TAG, "Could not load Play Games player profile", exception)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (exception: Exception) {
            mutableState.value = PlayGamesState.SignedOut
            Log.w(TAG, "Could not refresh Play Games sign-in state", exception)
        }
    }

    suspend fun signIn(activity: Activity): Boolean = mutex.withLock {
        if (mutableState.value is PlayGamesState.SignedIn) return@withLock true
        mutableState.value = PlayGamesState.Checking
        try {
            PlayGames.getGamesSignInClient(activity).signIn().await()
        } catch (cancelled: CancellationException) {
            mutableState.value = PlayGamesState.SignedOut
            currentCoroutineContext().ensureActive()
            Log.w(TAG, "Play Games sign-in was cancelled", cancelled)
        } catch (exception: Exception) {
            Log.w(TAG, "Play Games sign-in failed", exception)
        }
        refreshLocked(activity)
        mutableState.value is PlayGamesState.SignedIn
    }

    private companion object {
        const val TAG = "PlayGamesManager"
    }
}
