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
        viewModelScope, SharingStarted.WhileSubscribed(5_000L), null,
    )
    private val _saving = kotlinx.coroutines.flow.MutableStateFlow(false)
    val saving: kotlinx.coroutines.flow.StateFlow<Boolean> = _saving
    private val _saveFailed = kotlinx.coroutines.flow.MutableStateFlow(false)
    val saveFailed: kotlinx.coroutines.flow.StateFlow<Boolean> = _saveFailed

    fun apply(settings: AppearanceSettings) {
        if (saving.value) return
        _saving.value = true
        _saveFailed.value = false
        viewModelScope.launch {
            try {
                preferences.apply(settings)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _saveFailed.value = true
            } finally {
                _saving.value = false
            }
        }
    }
}
