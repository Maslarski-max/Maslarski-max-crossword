package com.maslarski.crossword.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.maslarski.crossword.ui.clues.CluesScreen
import com.maslarski.crossword.ui.game.GameScreen
import com.maslarski.crossword.ui.home.HomeScreen
import com.maslarski.crossword.ui.levels.LevelsScreen
import com.maslarski.crossword.ui.settings.SettingsScreen
import kotlinx.serialization.Serializable

@Serializable data object HomeRoute
@Serializable data object LevelsRoute
@Serializable data object SettingsRoute
@Serializable data class GameRoute(val sessionId: String, val puzzleId: String)
@Serializable data class CluesRoute(val sessionId: String, val puzzleId: String)

/** Key used to hand the clue picked on the Clues screen back to the game screen. */
const val SELECTED_WORD_KEY = "selected_word"

@Composable
fun CrosswordNavHost() {
    val nav = rememberNavController()
    NavHost(
        navController = nav,
        startDestination = HomeRoute,
        enterTransition = { slideInHorizontally { it / 4 } + fadeIn() },
        exitTransition = { fadeOut() },
        popEnterTransition = { fadeIn() },
        popExitTransition = { slideOutHorizontally { it / 4 } + fadeOut() },
    ) {
        composable<HomeRoute> {
            HomeScreen(
                onPlay = { sessionId, puzzleId -> nav.navigate(GameRoute(sessionId, puzzleId)) },
                onLevels = { nav.navigate(LevelsRoute) },
                onSettings = { nav.navigate(SettingsRoute) },
            )
        }
        composable<LevelsRoute> {
            LevelsScreen(
                onBack = { nav.popBackStack() },
                onPlay = { sessionId, puzzleId -> nav.navigate(GameRoute(sessionId, puzzleId)) },
            )
        }
        composable<SettingsRoute> {
            SettingsScreen(onBack = { nav.popBackStack() })
        }
        composable<GameRoute> { entry ->
            GameScreen(
                selectedWordResult = entry.savedStateHandle.getStateFlow<String?>(SELECTED_WORD_KEY, null),
                onSelectedWordConsumed = { entry.savedStateHandle[SELECTED_WORD_KEY] = null },
                onBack = { nav.popBackStack() },
                onOpenClues = { sessionId, puzzleId -> nav.navigate(CluesRoute(sessionId, puzzleId)) },
                onPlayNext = { sessionId, puzzleId ->
                    nav.navigate(GameRoute(sessionId, puzzleId)) {
                        popUpTo<GameRoute> { inclusive = true }
                    }
                },
            )
        }
        composable<CluesRoute> {
            CluesScreen(
                onBack = { nav.popBackStack() },
                onClueSelected = { wordKey ->
                    nav.previousBackStackEntry?.savedStateHandle?.set(SELECTED_WORD_KEY, wordKey)
                    nav.popBackStack()
                },
            )
        }
    }
}
