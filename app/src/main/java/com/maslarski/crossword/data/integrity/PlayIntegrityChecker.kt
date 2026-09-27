package com.maslarski.crossword.data.integrity

import android.content.Context
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.StandardIntegrityManager.PrepareIntegrityTokenRequest
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityTokenProvider
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityTokenRequest
import com.maslarski.crossword.BuildConfig
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

/**
 * Play Integrity (Standard API) scaffold. The app is fully offline today, so nothing calls this yet;
 * when a backend is added (e.g. online leaderboards or coin purchases), request a token with a hash of
 * the request payload and verify it server-side via the Play Integrity decodeIntegrityToken endpoint.
 * Disabled unless `playIntegrityCloudProjectNumber` is set (see README).
 */
class PlayIntegrityChecker(private val context: Context) {

    private val mutex = Mutex()
    private var provider: StandardIntegrityTokenProvider? = null

    val isConfigured: Boolean get() = BuildConfig.PLAY_INTEGRITY_CLOUD_PROJECT_NUMBER > 0

    /** Warms up the token provider; call early (e.g. app start) to reduce latency of [requestToken]. */
    suspend fun prepare(): Result<Unit> = runCatching { provider() }

    /** Returns an integrity token bound to [requestHash] (SHA-256 of the request you're protecting). */
    suspend fun requestToken(requestHash: String): Result<String> = runCatching {
        provider().request(StandardIntegrityTokenRequest.builder().setRequestHash(requestHash).build()).await().token()
    }

    private suspend fun provider(): StandardIntegrityTokenProvider = mutex.withLock {
        check(isConfigured) { "Play Integrity cloud project number not configured" }
        provider ?: IntegrityManagerFactory.createStandard(context)
            .prepareIntegrityToken(
                PrepareIntegrityTokenRequest.builder()
                    .setCloudProjectNumber(BuildConfig.PLAY_INTEGRITY_CLOUD_PROJECT_NUMBER)
                    .build(),
            ).await()
            .also { provider = it }
    }
}
