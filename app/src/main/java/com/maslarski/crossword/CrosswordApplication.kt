package com.maslarski.crossword

import android.app.Application
import com.google.android.gms.games.PlayGamesSdk
import com.maslarski.crossword.data.telemetry.FirebaseTelemetry
import com.maslarski.crossword.domain.profile.AchievementTracker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class CrosswordApplication : Application() {

    @Inject lateinit var telemetry: FirebaseTelemetry
    @Inject lateinit var achievements: AchievementTracker

    override fun onCreate() {
        super.onCreate()
        PlayGamesSdk.initialize(this)
        telemetry.initialize()
        achievements.start()
    }
}
