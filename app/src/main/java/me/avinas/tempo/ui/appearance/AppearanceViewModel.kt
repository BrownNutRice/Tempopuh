package me.avinas.tempo.ui.appearance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppearanceViewModel @Inject constructor(
    private val preferences: AppearancePreferencesRepository,
) : ViewModel() {
    val settings = preferences.settings.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000L), AppearanceSettings(),
    )

    fun applyPreset(preset: AppearancePreset) {
        viewModelScope.launch { preferences.setPreset(preset) }
    }

    fun setFontStyle(style: AppearanceFontStyle) {
        viewModelScope.launch { preferences.setFontStyle(style) }
    }

    fun setReduceMotion(reduced: Boolean) {
        viewModelScope.launch { preferences.setReducedMotion(reduced) }
    }

    fun restoreDefaults() {
        viewModelScope.launch { preferences.restoreDefaults() }
    }
}
