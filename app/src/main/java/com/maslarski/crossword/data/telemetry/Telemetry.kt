package com.maslarski.crossword.data.telemetry

import android.content.Context
import android.os.Bundle
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.maslarski.crossword.BuildConfig
import com.maslarski.crossword.domain.arena.ArenaResult
import com.maslarski.crossword.domain.arena.ArenaState
import com.maslarski.crossword.domain.engine.Hint
import com.maslarski.crossword.domain.model.CompletionResult
import com.maslarski.crossword.domain.model.Puzzle

interface Telemetry {
    fun puzzleStarted(puzzle: Puzzle)
    fun puzzleCompleted(puzzle: Puzzle, result: CompletionResult)
    fun hintUsed(puzzle: Puzzle, hint: Hint)
    fun arenaMatchStarted(state: ArenaState)
    fun arenaMatchFinished(state: ArenaState, result: ArenaResult)
    fun recordNonFatal(throwable: Throwable)
}

/**
 * Firebase Analytics + Crashlytics. Everything is a no-op when the build has no google-services.json
 * (FirebaseApp not initialised), so local/dev builds work without a Firebase project.
 */
class FirebaseTelemetry(private val context: Context) : Telemetry {

    private val enabled: Boolean
        get() = BuildConfig.FIREBASE_CONFIGURED && FirebaseApp.getApps(context).isNotEmpty()

    private val analytics: FirebaseAnalytics? by lazy { if (enabled) FirebaseAnalytics.getInstance(context) else null }

    fun initialize() {
        if (!enabled) return
        // Crash reports only from release builds; debug crashes stay in logcat.
        FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled = !BuildConfig.DEBUG
    }

    /** Consent Mode v2: ad-related signals follow the UMP result; analytics storage stays on for stability metrics. */
    fun updateConsent(adsConsentGranted: Boolean) {
        val ads = if (adsConsentGranted) FirebaseAnalytics.ConsentStatus.GRANTED else FirebaseAnalytics.ConsentStatus.DENIED
        analytics?.setConsent(
            mapOf(
                FirebaseAnalytics.ConsentType.ANALYTICS_STORAGE to FirebaseAnalytics.ConsentStatus.GRANTED,
                FirebaseAnalytics.ConsentType.AD_STORAGE to ads,
                FirebaseAnalytics.ConsentType.AD_USER_DATA to ads,
                FirebaseAnalytics.ConsentType.AD_PERSONALIZATION to ads,
            ),
        )
    }

    override fun puzzleStarted(puzzle: Puzzle) = log(FirebaseAnalytics.Event.LEVEL_START) {
        putString(FirebaseAnalytics.Param.LEVEL_NAME, puzzle.id)
        putString(PARAM_DIFFICULTY, puzzle.difficulty.name)
    }

    override fun puzzleCompleted(puzzle: Puzzle, result: CompletionResult) = log(FirebaseAnalytics.Event.LEVEL_END) {
        putString(FirebaseAnalytics.Param.LEVEL_NAME, puzzle.id)
        putString(FirebaseAnalytics.Param.SUCCESS, "true")
        putLong(FirebaseAnalytics.Param.SCORE, result.score.toLong())
        putLong(PARAM_SECONDS, result.elapsedSeconds)
        putLong(PARAM_STARS, result.stars.toLong())
    }

    override fun hintUsed(puzzle: Puzzle, hint: Hint) = log(EVENT_HINT) {
        putString(FirebaseAnalytics.Param.LEVEL_NAME, puzzle.id)
        putString(PARAM_HINT, hint.name.lowercase())
    }

    override fun arenaMatchStarted(state: ArenaState) = log(EVENT_ARENA_START) {
        putString(FirebaseAnalytics.Param.LEVEL_NAME, state.puzzleId)
        putString(PARAM_DIFFICULTY, state.difficulty.name)
    }

    override fun arenaMatchFinished(state: ArenaState, result: ArenaResult) = log(EVENT_ARENA_END) {
        putString(FirebaseAnalytics.Param.LEVEL_NAME, state.puzzleId)
        putString(PARAM_DIFFICULTY, state.difficulty.name)
        putString(PARAM_OUTCOME, result.outcome.name.lowercase())
        putLong(FirebaseAnalytics.Param.SCORE, result.playerScore.toLong())
        putLong(PARAM_OPPONENT_SCORE, result.opponentScore.toLong())
    }

    override fun recordNonFatal(throwable: Throwable) {
        if (enabled) FirebaseCrashlytics.getInstance().recordException(throwable)
    }

    private inline fun log(event: String, params: Bundle.() -> Unit) {
        analytics?.logEvent(event, Bundle().apply(params))
    }

    private companion object {
        const val EVENT_HINT = "hint_used"
        const val EVENT_ARENA_START = "arena_match_start"
        const val EVENT_ARENA_END = "arena_match_end"
        const val PARAM_OUTCOME = "outcome"
        const val PARAM_OPPONENT_SCORE = "opponent_score"
        const val PARAM_HINT = "hint_type"
        const val PARAM_DIFFICULTY = "difficulty"
        const val PARAM_SECONDS = "elapsed_seconds"
        const val PARAM_STARS = "stars"
    }
}
