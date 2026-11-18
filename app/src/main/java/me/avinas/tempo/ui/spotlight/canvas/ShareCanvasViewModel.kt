package me.avinas.tempo.ui.spotlight.canvas

import android.content.Context
import android.os.Bundle
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    savedStateHandle: SavedStateHandle,
    private val insightCardGenerator: InsightCardGenerator,
    @ApplicationContext context: Context,
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
    
    internal val editorState = mutableStateOf(CanvasState())
    internal val editorReady = mutableStateOf(false)
    private var viewport: CanvasViewport? = null
    private var baseline: CanvasDraft? = null
    private val restoredSession = savedStateHandle.get<Bundle>(SESSION_KEY)
    private var pendingDraft = CanvasDraftCodec.decode(restoredSession?.getString("draft"))
    private val restoredDirty = restoredSession?.getBoolean("dirty") == true && pendingDraft != null
    private val draftPreferences = context.getSharedPreferences("spotlight_canvas_drafts", Context.MODE_PRIVATE)
    private val draftKey = "${timeRange.name}:${initialCardId.orEmpty()}"

    init {
        savedStateHandle.setSavedStateProvider(SESSION_KEY) {
            Bundle().apply {
                val draft = currentDraft() ?: pendingDraft
                putString("draft", draft?.let(CanvasDraftCodec::encode))
                putBoolean("dirty", if (editorReady.value) isDirty() else restoredDirty)
            }
        }
        loadCards()
    }

    internal fun prepareEditor(
        target: CanvasViewport,
        cards: List<SpotlightCardData>,
        initial: SpotlightCardData?,
    ) {
        val previous = viewport
        if (previous == null) {
            val restored = pendingDraft?.restore(target, cards)
            editorState.value = restored ?: CanvasState(
                canvasItems = initial?.let { listOf(CanvasCardItem(
                    cardData = it,
                    offsetX = target.width / 2 - 160f * target.density,
                    offsetY = target.height / 2 - 280f * target.density,
                    scale = target.width / (640f * target.density),
                )) }.orEmpty(),
                availableCards = cards,
            )
            viewport = target
            baseline = if (restoredDirty) null else currentDraft()
            pendingDraft = null
            editorReady.value = true
        } else if (previous != target) {
            val draft = CanvasDraft.from(editorState.value, previous)
            val wasDirty = isDirty()
            val old = editorState.value
            val restored = draft.restore(target, (old.canvasItems.map { it.cardData } + cards).distinctBy { it.id })
            editorState.value = old.copy(
                canvasItems = restored.canvasItems,
                textItems = restored.textItems,
                availableCards = cards,
            )
            viewport = target
            if (!wasDirty) baseline = currentDraft()
        } else if (editorState.value.availableCards != cards) {
            editorState.value = editorState.value.copy(availableCards = cards)
        }
    }

    private fun currentDraft(): CanvasDraft? = viewport?.let { CanvasDraft.from(editorState.value, it) }

    internal fun isDirty(): Boolean {
        if (!editorReady.value) return restoredDirty
        return currentDraft()?.copy(editingTextId = null) != baseline?.copy(editingTextId = null)
    }

    internal suspend fun saveDraft(): Boolean {
        val draft = currentDraft() ?: pendingDraft ?: return false
        val json = CanvasDraftCodec.encode(draft.copy(editingTextId = null)) ?: return false
        return withContext(Dispatchers.IO) {
            runCatching { draftPreferences.edit().putString(draftKey, json).commit() }.getOrDefault(false)
        }
    }

    private companion object { const val SESSION_KEY = "share_canvas_editor" }

    private fun loadCards() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                if (pendingDraft == null) {
                    pendingDraft = withContext(Dispatchers.IO) {
                        CanvasDraftCodec.decode(draftPreferences.getString(draftKey, null))
                    }
                }
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
