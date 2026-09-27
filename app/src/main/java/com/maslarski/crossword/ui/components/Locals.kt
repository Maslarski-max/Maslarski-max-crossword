package com.maslarski.crossword.ui.components

import androidx.compose.runtime.staticCompositionLocalOf
import com.maslarski.crossword.data.ads.AdController
import com.maslarski.crossword.data.ads.ConsentManager

/** Null in previews and tests, where no ads are shown. */
val LocalAdController = staticCompositionLocalOf<AdController?> { null }

val LocalConsentManager = staticCompositionLocalOf<ConsentManager?> { null }
