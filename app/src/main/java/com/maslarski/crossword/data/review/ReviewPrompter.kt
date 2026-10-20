package com.maslarski.crossword.data.review

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.android.play.core.ktx.launchReview
import com.google.android.play.core.ktx.requestReview
import com.maslarski.crossword.data.local.CrosswordDatabase
import com.maslarski.crossword.data.local.ReviewPromptEntity
import com.maslarski.crossword.domain.repository.ArenaRepository
import com.maslarski.crossword.domain.review.ReviewPromptRecord
import com.maslarski.crossword.domain.review.ReviewSchedule
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReviewPrompter @Inject constructor(
    @ApplicationContext context: Context,
    private val database: CrosswordDatabase,
    private val arena: ArenaRepository,
    private val clock: Clock,
) {
    private val manager by lazy { ReviewManagerFactory.create(context) }
    private val mutex = Mutex()

    /** Requests the Play review sheet if [ReviewSchedule] says it's due; the request is recorded first so it never repeats early. */
    suspend fun requestIfDue(activity: Activity) = mutex.withLock {
        val dao = database.reviewPromptDao()
        val levels = database.levelProgressDao().observeCompletedCount().first()
        val wins = arena.observeStats().first().wins
        val last = dao.get()
        if (!ReviewSchedule.isDue(levels, wins, last?.let { ReviewPromptRecord(it.levelsSolved, it.arenaWins) })) return@withLock
        dao.upsert(
            ReviewPromptEntity(
                levelsSolved = levels,
                arenaWins = wins,
                promptCount = (last?.promptCount ?: 0) + 1,
                lastPromptAt = clock.millis(),
            ),
        )
        try {
            manager.launchReview(activity, manager.requestReview())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (exception: Exception) {
            Log.w(TAG, "In-App Review flow failed", exception)
        }
    }

    private companion object {
        const val TAG = "ReviewPrompter"
    }
}
