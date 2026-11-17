package me.avinas.tempo.data.reset

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppResetState { Idle, Requesting, Requested, Failed }

/** Requested means Android accepted the request, never that deletion has been verified. */
internal class AppResetRequest(private val clearApplicationData: () -> Boolean) {
    private val _state = MutableStateFlow(AppResetState.Idle)
    val state: StateFlow<AppResetState> = _state.asStateFlow()

    fun request() {
        val before = _state.value
        if (before != AppResetState.Idle && before != AppResetState.Failed) return
        if (!_state.compareAndSet(before, AppResetState.Requesting)) return
        _state.value = try {
            if (clearApplicationData()) AppResetState.Requested else AppResetState.Failed
        } catch (_: Exception) {
            AppResetState.Failed
        }
    }
}
