package me.avinas.tempo.ui.stats

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.merge
import me.avinas.tempo.ui.appearance.*
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.avinas.tempo.data.repository.SortBy
import me.avinas.tempo.data.repository.StatsRepository
import me.avinas.tempo.data.stats.StatItem
import me.avinas.tempo.data.stats.TimeRange
import me.avinas.tempo.data.stats.TopAlbum
import me.avinas.tempo.data.stats.TopArtist
import me.avinas.tempo.data.stats.TopTrack
import javax.inject.Inject

@OptIn(FlowPreview::class)
@HiltViewModel
class StatsViewModel
    @Inject
    constructor(
        private val statsRepository: StatsRepository,
        private val appearancePreferences: AppearancePreferencesRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(StatsUiState())
        val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

        // Track last loaded analytics time range to skip redundant reloads
        // Analytics is time-range dependent, not tab-dependent
        private var lastAnalyticsTimeRange: TimeRange? = null
        private var loadJob: Job? = null
        private var visibleScope: CoroutineScope? = null
        private var loadMoreJob: Job? = null

        // A filter/search switch must make in-flight query results obsolete.
        // Cancellation alone is insufficient for repositories that complete
        // work just as a cancellation is delivered.
        private var resultGeneration = 0L
        private var userSelectedFilters = false

        private fun invalidateInFlightQueries() {
            resultGeneration++
            loadJob?.cancel()
            loadMoreJob?.cancel()
            // A canceled pagination/refresh job won't reach its cleanup path.
            // Reset transient indicators whenever a newer request supersedes it.
            _uiState.update { it.copy(isLoadingMore = false, isRefreshing = false) }
        }

        private var defaultsLoaded = false

        suspend fun observeWhileVisible() {
            if (!defaultsLoaded && !userSelectedFilters) {
                val defaults = appearancePreferences.settings.first()
                if (!userSelectedFilters) _uiState.update { state -> state.copy(
                    selectedTab = when (defaults.statsTab) {
                        AppearanceStatsTab.SONGS -> StatsTab.TOP_SONGS
                        AppearanceStatsTab.ARTISTS -> StatsTab.TOP_ARTISTS
                        AppearanceStatsTab.ALBUMS -> StatsTab.TOP_ALBUMS
                    },
                    selectedTimeRange = when (defaults.statsRange) {
                        AppearanceStatsRange.WEEK -> TimeRange.THIS_WEEK
                        AppearanceStatsRange.MONTH -> TimeRange.THIS_MONTH
                        AppearanceStatsRange.YEAR -> TimeRange.THIS_YEAR
                        AppearanceStatsRange.ALL_TIME -> TimeRange.ALL_TIME
                    },
                ) }
                defaultsLoaded = true
            }
            try {
                coroutineScope {
                    visibleScope = this
                    merge(statsRepository.observeListeningStatsChanges(), statsRepository.observeMetadataUpdates())
                        .debounce(300)
                        .collect {
                            statsRepository.invalidateCache(_uiState.value.selectedTimeRange)
                            invalidateInFlightQueries()
                            lastAnalyticsTimeRange = null
                            loadJob = loadData()
                        }
                }
            } finally {
                visibleScope = null
                searchJob?.cancel()
                invalidateInFlightQueries()
            }
        }

        fun onTabSelected(tab: StatsTab) {
            userSelectedFilters = true
            if (_uiState.value.selectedTab == tab && _uiState.value.items.isNotEmpty()) return
            searchJob?.cancel()
            invalidateInFlightQueries()
            _uiState.update { it.copy(selectedTab = tab, isLoading = true, items = emptyList(), page = 0, hasMore = true) }
            loadJob = loadData()
        }

        fun onTimeRangeSelected(timeRange: TimeRange) {
            userSelectedFilters = true
            if (_uiState.value.selectedTimeRange == timeRange && _uiState.value.items.isNotEmpty()) return
            searchJob?.cancel()
            invalidateInFlightQueries()
            _uiState.update { it.copy(selectedTimeRange = timeRange, isLoading = true, items = emptyList(), page = 0, hasMore = true) }
            loadJob = loadData()
        }

        fun onSortBySelected(sortBy: SortBy) {
            userSelectedFilters = true
            if (_uiState.value.selectedSortBy == sortBy && _uiState.value.items.isNotEmpty()) return
            searchJob?.cancel()
            invalidateInFlightQueries()
            _uiState.update { it.copy(selectedSortBy = sortBy, isLoading = true, items = emptyList(), page = 0, hasMore = true) }
            loadJob = loadData()
        }

        // Search state: debounced query that live-filters the current ranking.
        // While active, results come from searchTop* (global ranks, capped) and
        // pagination is disabled — search results are a bounded, complete set.
        private var searchJob: Job? = null

        fun onSearchQueryChanged(query: String) {
            if (_uiState.value.searchQuery == query) return
            searchJob?.cancel()
            invalidateInFlightQueries()
            _uiState.update { it.copy(searchQuery = query) }
            searchJob =
                visibleScope?.launch {
                    delay(300) // Debounce keystrokes
                    _uiState.update { it.copy(isLoading = true, items = emptyList(), page = 0, hasMore = false) }
                    loadJob = loadData()
                }
        }

        fun loadMore() {
            if (visibleScope == null) return
            val state = _uiState.value
            if (state.isLoading || state.isLoadingMore || !state.hasMore) return
            if (state.searchQuery.isNotBlank()) return // Search results are not paginated
            _uiState.update { it.copy(isLoadingMore = true) }
            loadMoreJob = loadData(isLoadMore = true)
        }

        /** Retries loading stats data after a failure. */
        fun retry() {
            searchJob?.cancel()
            invalidateInFlightQueries()
            _uiState.update { it.copy(error = null, isLoading = true, items = emptyList(), page = 0, hasMore = true) }
            loadJob = loadData()
        }

        private fun loadData(isLoadMore: Boolean = false): Job? {
            val generation = resultGeneration
            return visibleScope?.launch {
                try {
                    val currentState = _uiState.value
                    val page = if (isLoadMore) currentState.page + 1 else 0
                    val timeRange = currentState.selectedTimeRange
                    val sortBy = currentState.selectedSortBy
                    val searchQuery = currentState.searchQuery.takeIf { it.isNotBlank() }

                    val result: List<StatItem> =
                        if (searchQuery != null) {
                            // Search mode: matches with global ranks, capped, no pagination
                            when (currentState.selectedTab) {
                                StatsTab.TOP_SONGS -> statsRepository.searchTopTracks(timeRange, sortBy, searchQuery, withLeeway = false)
                                StatsTab.TOP_ARTISTS -> statsRepository.searchTopArtists(timeRange, sortBy, searchQuery, withLeeway = false)
                                StatsTab.TOP_ALBUMS -> statsRepository.searchTopAlbums(timeRange, sortBy, searchQuery, withLeeway = false)
                            }
                        } else {
                            when (currentState.selectedTab) {
                                StatsTab.TOP_SONGS -> {
                                    val res = statsRepository.getTopTracks(timeRange, sortBy, page, withLeeway = false)
                                    res.items
                                }

                                StatsTab.TOP_ARTISTS -> {
                                    val res = statsRepository.getTopArtists(timeRange, sortBy, page, withLeeway = false)
                                    res.items
                                }

                                StatsTab.TOP_ALBUMS -> {
                                    val res = statsRepository.getTopAlbums(timeRange, sortBy, page, withLeeway = false)
                                    res.items
                                }
                            }
                        }

                    val hasMore = searchQuery == null && result.isNotEmpty() // Simplified check, ideally use totalCount from PaginatedResult
                    if (generation != resultGeneration) return@launch

                    _uiState.update { state ->
                        if (generation != resultGeneration) return@update state
                        val newItems =
                            if (isLoadMore) {
                                (state.items + result).distinctBy { item ->
                                    when (item) {
                                        is TopTrack -> "track_${item.trackId}"
                                        is TopArtist -> "artist_${item.artistId ?: item.artist}"
                                        is TopAlbum -> "album_${item.album}_${item.artist}"
                                    }
                                }
                            } else {
                                result
                            }
                        state.copy(
                            items = newItems,
                            isLoading = false,
                            isLoadingMore = false,
                            page = page,
                            hasMore = hasMore,
                            error = null,
                        )
                    }

                    // Fetch listening overview only if time range changed (needed for share dialog)
                    if (page == 0 && timeRange != lastAnalyticsTimeRange && generation == resultGeneration) {
                        loadAnalyticsData(timeRange, generation)
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    if (generation == resultGeneration) {
                        _uiState.update { it.copy(isLoading = false, isLoadingMore = false, error = e.message) }
                    }
                }
            }
        }

        private suspend fun loadAnalyticsData(timeRange: TimeRange, generation: Long) {
            try {
                val overview = statsRepository.getListeningOverview(timeRange, withLeeway = false)
                if (generation != resultGeneration) return
                _uiState.update {
                    if (generation != resultGeneration) return@update it
                    lastAnalyticsTimeRange = timeRange
                    it.copy(
                        analyticsData =
                            AnalyticsUiData(
                                overview = overview,
                                hourlyDistribution = emptyList(),
                                insightCards = emptyList(),
                            ),
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // Non-fatal if overview loading fails
            }
        }

        /** Pull-to-refresh handler. */
        suspend fun refresh() {
            visibleScope?.launch { refreshData() }?.join()
        }

        private suspend fun refreshData() {
            searchJob?.cancel()
            invalidateInFlightQueries()
            val generation = resultGeneration
            _uiState.update { it.copy(isRefreshing = true) }
            val startTime = System.currentTimeMillis()
            try {
                statsRepository.invalidateCache()
                lastAnalyticsTimeRange = null // Force analytics reload on manual refresh
                _uiState.update { it.copy(isLoading = true, items = emptyList(), page = 0, hasMore = true) }
                // Fetch data directly (inline, awaitable) rather than via fire-and-forget loadData()
                fetchDataForRefresh(generation)
            } finally {
                // Keep spinner visible for at least 600ms to prevent flicker
                val elapsed = System.currentTimeMillis() - startTime
                if (elapsed < 600) delay(600 - elapsed)
                if (generation == resultGeneration) {
                    _uiState.update { it.copy(isRefreshing = false) }
                }
            }
        }

        /** Awaitable version of data loading used by pull-to-refresh. */
        private suspend fun fetchDataForRefresh(generation: Long) {
            try {
                val currentState = _uiState.value
                val timeRange = currentState.selectedTimeRange
                val sortBy = currentState.selectedSortBy
                val searchQuery = currentState.searchQuery.takeIf { it.isNotBlank() }

                val result: List<StatItem> =
                    if (searchQuery != null) {
                        when (currentState.selectedTab) {
                            StatsTab.TOP_SONGS -> statsRepository.searchTopTracks(timeRange, sortBy, searchQuery, withLeeway = false)
                            StatsTab.TOP_ARTISTS -> statsRepository.searchTopArtists(timeRange, sortBy, searchQuery, withLeeway = false)
                            StatsTab.TOP_ALBUMS -> statsRepository.searchTopAlbums(timeRange, sortBy, searchQuery, withLeeway = false)
                        }
                    } else {
                        when (currentState.selectedTab) {
                            StatsTab.TOP_SONGS -> {
                                val res = statsRepository.getTopTracks(timeRange, sortBy, 0, withLeeway = false)
                                res.items
                            }

                            StatsTab.TOP_ARTISTS -> {
                                val res = statsRepository.getTopArtists(timeRange, sortBy, 0, withLeeway = false)
                                res.items
                            }

                            StatsTab.TOP_ALBUMS -> {
                                val res = statsRepository.getTopAlbums(timeRange, sortBy, 0, withLeeway = false)
                                res.items
                            }
                        }
                    }

                val hasMore = searchQuery == null && result.isNotEmpty()
                if (generation != resultGeneration) return
                _uiState.update { state ->
                    if (generation != resultGeneration) return@update state
                    state.copy(
                        items = result,
                        isLoading = false,
                        isLoadingMore = false,
                        page = 0,
                        hasMore = hasMore,
                    )
                }

                if (generation == resultGeneration) {
                    lastAnalyticsTimeRange = null
                    loadAnalyticsData(timeRange, generation)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (generation == resultGeneration) {
                    _uiState.update { it.copy(isLoading = false, isLoadingMore = false, error = e.message) }
                }
            }
        }
    }

enum class StatsTab {
    TOP_SONGS,
    TOP_ARTISTS,
    TOP_ALBUMS,
}

@Immutable
data class StatsUiState(
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val selectedTab: StatsTab = StatsTab.TOP_SONGS,
    val selectedTimeRange: TimeRange = TimeRange.THIS_WEEK,
    val selectedSortBy: SortBy = SortBy.COMBINED_SCORE, // Default to combined score
    val items: List<StatItem> = emptyList(), // Can be TopTrack, TopArtist, or TopAlbum
    val searchQuery: String = "",
    val page: Int = 0,
    val hasMore: Boolean = true,
    val analyticsData: AnalyticsUiData? = null,
)

@Immutable
data class AnalyticsUiData(
    val overview: me.avinas.tempo.data.stats.ListeningOverview,
    val hourlyDistribution: List<me.avinas.tempo.data.stats.HourlyDistribution>,
    val insightCards: List<me.avinas.tempo.data.stats.InsightCardData>,
)
