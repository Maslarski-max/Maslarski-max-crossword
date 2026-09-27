package com.maslarski.crossword

import android.app.Application
import com.maslarski.crossword.data.telemetry.FirebaseTelemetry
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class CrosswordApplication : Application() {

    @Inject lateinit var telemetry: FirebaseTelemetry

    override fun onCreate() {
        super.onCreate()
        telemetry.initialize()
    }
}
