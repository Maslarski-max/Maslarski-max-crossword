package com.maslarski.crossword.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maslarski.crossword.domain.model.Settings
import com.maslarski.crossword.domain.model.ThemeMode
import com.maslarski.crossword.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(private val repository: SettingsRepository) : ViewModel() {

    val settings: StateFlow<Settings> =
        repository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Settings())

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { repository.setThemeMode(mode) }
    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch { repository.setDynamicColor(enabled) }
    fun setHaptics(enabled: Boolean) = viewModelScope.launch { repository.setHaptics(enabled) }
    fun setShowTimer(enabled: Boolean) = viewModelScope.launch { repository.setShowTimer(enabled) }
}
