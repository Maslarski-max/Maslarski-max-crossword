package com.maslarski.crossword.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.maslarski.crossword.domain.model.Settings
import com.maslarski.crossword.domain.model.ThemeMode
import com.maslarski.crossword.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DataStoreSettingsRepository(private val store: DataStore<Preferences>) : SettingsRepository {

    override val settings: Flow<Settings> = store.data.map { p ->
        val defaults = Settings()
        Settings(
            themeMode = ThemeMode.entries.firstOrNull { it.name == p[THEME] } ?: defaults.themeMode,
            dynamicColor = p[DYNAMIC_COLOR] ?: defaults.dynamicColor,
            hintEconomyEnabled = p[HINT_ECONOMY] ?: defaults.hintEconomyEnabled,
            hapticsEnabled = p[HAPTICS] ?: defaults.hapticsEnabled,
            showTimer = p[SHOW_TIMER] ?: defaults.showTimer,
        )
    }

    override suspend fun setThemeMode(mode: ThemeMode) { store.edit { it[THEME] = mode.name } }
    override suspend fun setDynamicColor(enabled: Boolean) { store.edit { it[DYNAMIC_COLOR] = enabled } }
    override suspend fun setHintEconomy(enabled: Boolean) { store.edit { it[HINT_ECONOMY] = enabled } }
    override suspend fun setHaptics(enabled: Boolean) { store.edit { it[HAPTICS] = enabled } }
    override suspend fun setShowTimer(enabled: Boolean) { store.edit { it[SHOW_TIMER] = enabled } }

    private companion object {
        val THEME = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val HINT_ECONOMY = booleanPreferencesKey("hint_economy")
        val HAPTICS = booleanPreferencesKey("haptics")
        val SHOW_TIMER = booleanPreferencesKey("show_timer")
    }
}
