package me.avinas.tempo.ui.spotlight.canvas

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.avinas.tempo.ui.spotlight.InsightCardGenerator
import me.avinas.tempo.ui.spotlight.SpotlightCardData
import me.avinas.tempo.data.stats.TimeRange
import javax.inject.Inject

@HiltViewModel
class ShareCanvasViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val insightCardGenerator: InsightCardGenerator
) : ViewModel() {
    
    private val _allCards = MutableStateFlow<List<SpotlightCardData>>(emptyList())
    val allCards: StateFlow<List<SpotlightCardData>> = _allCards.asStateFlow()
    
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val initialCardId = savedStateHandle.get<String>("initialCardId")
    private val requestedRange = savedStateHandle.get<String>("timeRange")
    val timeRange = TimeRange.entries.firstOrNull { it.name == requestedRange } ?: TimeRange.ALL_TIME

    // Card types that have Dashboard UI composables
    private val supportedCardTypes = setOf(
        "cosmic_clock",
        "weekend_warrior", 
        "forgotten_favorite",
        "deep_dive",
        "new_obsession",
        "early_adopter",
        "listening_peak",
        "artist_loyalty",
        "discovery"
    )
    
    init {
        loadCards()
    }
    
    private fun loadCards() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _allCards.value = insightCardGenerator.generateCards(timeRange)
                    .filter { it.id in supportedCardTypes }
                    .distinctBy { it.id }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                android.util.Log.e("ShareCanvasViewModel", "Failed to load cards", e)
                _allCards.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun getInitialCard(): SpotlightCardData? = _allCards.value.firstOrNull { it.id == initialCardId }
}
