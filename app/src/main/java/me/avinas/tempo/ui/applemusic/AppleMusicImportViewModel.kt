package me.avinas.tempo.ui.applemusic

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.avinas.tempo.data.applemusic.AppleMusicImportService
import javax.inject.Inject

@HiltViewModel
class AppleMusicImportViewModel @Inject constructor(
    private val importer: AppleMusicImportService,
) : ViewModel() {
    private val _files = MutableStateFlow<List<Uri>>(emptyList())
    val files = _files.asStateFlow()
    private val _state = MutableStateFlow<State>(State.Ready)
    val state = _state.asStateFlow()
    private var importJob: Job? = null

    fun selectFiles(files: List<Uri>) {
        if (_state.value == State.Ready) _files.value = files
    }

    fun start() {
        if (importJob?.isCompleted == false || _state.value != State.Ready) return
        val files = _files.value
        if (files.isEmpty()) return

        var progress = AppleMusicImportService.Summary()
        _state.value = State.Importing(progress)
        importJob = viewModelScope.launch {
            try {
                val summary = importer.importHistory(files) {
                    progress = it
                    _state.value = State.Importing(it)
                }
                _state.value = State.Complete(summary)
            } catch (e: CancellationException) {
                _state.value = State.Cancelled(progress)
                throw e
            } catch (e: AppleMusicImportService.ImportException) {
                _state.value = State.Failed(e.problem, progress)
            } catch (_: Exception) {
                _state.value = State.Failed(AppleMusicImportService.Problem.UNREADABLE, progress)
            }
        }
    }

    fun cancel() {
        importJob?.cancel()
    }

    fun reset() {
        _files.value = emptyList()
        _state.value = State.Ready
    }

    sealed interface State {
        data object Ready : State
        data class Importing(val summary: AppleMusicImportService.Summary) : State
        data class Complete(val summary: AppleMusicImportService.Summary) : State
        data class Cancelled(val summary: AppleMusicImportService.Summary) : State
        data class Failed(
            val problem: AppleMusicImportService.Problem,
            val summary: AppleMusicImportService.Summary,
        ) : State
    }
}
