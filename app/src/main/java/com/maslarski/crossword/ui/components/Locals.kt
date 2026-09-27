package com.maslarski.crossword.ui.components

import androidx.compose.runtime.staticCompositionLocalOf
import com.maslarski.crossword.data.ads.ConsentManager

/** Null in previews and tests. */
val LocalConsentManager = staticCompositionLocalOf<ConsentManager?> { null }
