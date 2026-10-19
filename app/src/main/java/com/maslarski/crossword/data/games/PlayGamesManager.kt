package com.maslarski.crossword.data.games

import android.app.Activity
import android.util.Log
import com.google.android.gms.games.PlayGames
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

sealed interface PlayGamesState {
    data object Checking : PlayGamesState
    data object SignedOut : PlayGamesState
    data class SignedIn(val displayName: String) : PlayGamesState
}

@Singleton
class PlayGamesManager @Inject constructor() {

    private val mutableState = MutableStateFlow<PlayGamesState>(PlayGamesState.Checking)
    val state: StateFlow<PlayGamesState> = mutableState.asStateFlow()

    suspend fun refresh(activity: Activity) {
        try {
            val result = PlayGames.getGamesSignInClient(activity).isAuthenticated().await()
            mutableState.value = if (result.isAuthenticated) {
                val displayName = PlayGames.getPlayersClient(activity).currentPlayer.await().displayName
                PlayGamesState.SignedIn(displayName)
            } else {
                PlayGamesState.SignedOut
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (exception: Exception) {
            mutableState.value = PlayGamesState.SignedOut
            Log.w(TAG, "Could not refresh Play Games sign-in state", exception)
        }
    }

    suspend fun signIn(activity: Activity) {
        try {
            PlayGames.getGamesSignInClient(activity).signIn().await()
            refresh(activity)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (exception: Exception) {
            mutableState.value = PlayGamesState.SignedOut
            Log.w(TAG, "Play Games sign-in failed", exception)
        }
    }

    private companion object {
        const val TAG = "PlayGamesManager"
    }
}
