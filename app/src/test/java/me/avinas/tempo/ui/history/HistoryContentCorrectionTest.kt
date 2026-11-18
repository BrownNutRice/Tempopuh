package me.avinas.tempo.ui.history

import androidx.lifecycle.ViewModelStore
import java.lang.reflect.Proxy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import me.avinas.tempo.data.local.dao.*
import me.avinas.tempo.data.local.DatabaseTransactionRunner
import me.avinas.tempo.data.local.entities.*
import me.avinas.tempo.data.repository.*
import me.avinas.tempo.data.analytics.NoOpAnalyticsTracker
import me.avinas.tempo.data.stats.PaginatedResult
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryContentCorrectionTest {
    private var failPage: Int? = null
    private var historyRows = emptyList<HistoryItem>()
    private var failDelete = false
    private val requestedPages = mutableListOf<Int>()
    private val tracks = mutableMapOf<Long, Track>()
    private val marks = mutableListOf<ManualContentMark>()
    private val deletedPlays = mutableListOf<Long>()
    private val deletedTracks = mutableListOf<Long>()
    private inline fun <reified T> fake(crossinline call: (String, Array<out Any?>) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, args ->
            call(method.name, args ?: emptyArray())
        } as T
    private fun track(id: Long, title: String = "Song") = Track(
        id = id, title = title, artist = "Artist", album = null, duration = null,
        albumArtUrl = null, spotifyId = null, musicbrainzId = null, contentType = "PODCAST"
    )
    private fun viewModel() = HistoryViewModel(
        fake<StatsRepository> { name, args -> when (name) {
            "observeListeningStatsChanges" -> emptyFlow<Any>()
            "getHistory" -> {
                val page = args[7] as Int
                requestedPages.add(page)
                check(page != failPage) { "History query failed" }
                PaginatedResult(if (page == 0) historyRows else emptyList(), historyRows.size, page, 20, page == 0 && historyRows.isNotEmpty())
            }
            "invalidateCache" -> Unit
            else -> error(name)
        } },
        fake<ListeningRepository> { name, args -> when (name) {
            "deleteById" -> { check(!failDelete) { "Delete failed" }; 1 }
            "deleteByTrackId" -> { deletedPlays.add(args[0] as Long); Unit }
            // History now observes one Room row as its inexpensive invalidation
            // signal. Keep the test double aligned with that dependency rather
            // than throwing IllegalStateException at ViewModel construction.
            "recentEvents" -> emptyFlow<List<ListeningEvent>>()
            else -> error(name)
        } },
        fake<TrackRepository> { name, args -> when (name) {
            "getById" -> flowOf(tracks[args[0]])
            "all" -> flowOf(tracks.values.toList())
            "update" -> { val track = args[0] as Track; tracks[track.id] = track; Unit }
            "deleteById" -> { deletedTracks.add(args[0] as Long); 1 }
            else -> error(name)
        } },
        fake<ManualContentMarkDao> { name, args -> when (name) {
            "getAllSync" -> marks.toList()
            "insertMark" -> { marks.add(args[0] as ManualContentMark); marks.size.toLong() }
            else -> error(name)
        } },
        fake<UserPreferencesDao> { name, _ -> when (name) {
            "getSync" -> UserPreferences()
            "upsert" -> Unit
            else -> error(name)
        } },
        fake<LastFmImportMetadataDao> { name, _ -> if (name == "getLatestCompleted") null else error(name) },
        fake<ScrobbleArchiveDao> { name, _ -> error(name) },
        RefreshCoordinator(),
        NoOpAnalyticsTracker(),
        object : DatabaseTransactionRunner {
            override suspend fun <T> run(block: suspend () -> T): T = block()
        }
    )

    private fun checkCorrection(block: suspend TestScope.(HistoryViewModel) -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val model = viewModel()
        val store = ViewModelStore().apply { put("history", model) }
        try {
            runCurrent()
            block(model)
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }

    @Test fun `always music from history retains plays and fixes content type`() = checkCorrection { model ->
        tracks[1] = track(1)
        model.markContent(1, "ALWAYS_MUSIC", false)
        runCurrent()
        assertEquals("MUSIC", tracks[1]?.contentType)
        assertEquals("ALWAYS_MUSIC", marks.single().contentType)
        assertTrue(deletedPlays.isEmpty())
        assertTrue(deletedTracks.isEmpty())
        assertNull(model.uiState.value.mutationError)
    }

    @Test fun `video correction removes all matching plays but keeps rule and track`() = checkCorrection { model ->
        tracks[1] = track(1)
        tracks[2] = track(2)
        tracks[3] = track(3, "Other")
        model.markContent(1, "NON_MUSIC", true)
        runCurrent()
        assertEquals(setOf(1L, 2L), deletedPlays.toSet())
        assertEquals("NON_MUSIC", marks.single().contentType)
        assertTrue(deletedTracks.isEmpty())
        assertNull(model.uiState.value.mutationError)
    }

    @Test fun `artist block preserves specific always music exception`() = checkCorrection { model ->
        tracks[1] = track(1)
        tracks[2] = track(2, "Other")
        marks.add(ManualContentMark(targetTrackId = 1, patternType = "TITLE_ARTIST",
            originalTitle = "Song", originalArtist = "Artist", patternValue = "Song", contentType = "ALWAYS_MUSIC"))
        model.markArtistContent(2, "NON_MUSIC", true)
        runCurrent()
        assertEquals(listOf(2L), deletedPlays)
        assertEquals("MUSIC", tracks[1]?.contentType)
        assertTrue(deletedTracks.isEmpty())
        assertNull(model.uiState.value.mutationError)
    }

    @Test fun `artist always music retains listening history`() = checkCorrection { model ->
        tracks[1] = track(1)
        tracks[2] = track(2, "Other")
        model.markArtistContent(1, "ALWAYS_MUSIC", false)
        runCurrent()
        assertTrue(tracks.values.all { it.contentType == "MUSIC" })
        assertTrue(deletedPlays.isEmpty())
    }

    @Test fun `failed page stays queued for explicit retry and preserves current rows`() = checkCorrection { model ->
        val row = HistoryItem(1, 1, 1_700_000_000_000, 30_000, 50, "Song", "Artist", null, album_art_url = null)
        historyRows = listOf(row)
        model.refresh()
        failPage = 1
        model.loadMore()
        runCurrent()
        assertTrue(model.uiState.value.pageFailed)
        assertEquals(listOf(row), model.uiState.value.rawItems)
        model.loadMore()
        runCurrent()
        assertEquals(1, requestedPages.count { it == 1 })

        failPage = null
        model.retryNextPage()
        runCurrent()
        assertFalse(model.uiState.value.pageFailed)
        assertEquals(2, requestedPages.count { it == 1 })
        assertEquals(listOf(row), model.uiState.value.rawItems)
    }

    @Test fun `failed refresh and deletion preserve rows and the search query`() = checkCorrection { model ->
        val row = HistoryItem(1, 1, 1_700_000_000_000, 30_000, 50, "Song", "Artist", null, album_art_url = null)
        historyRows = listOf(row)
        model.onSearchQueryChanged("Song")
        advanceTimeBy(500)
        runCurrent()
        failPage = 0
        model.refresh()
        assertTrue(model.uiState.value.loadFailed)

        failDelete = true
        model.deleteListeningEvent(row.id)
        runCurrent()
        assertEquals(me.avinas.tempo.R.string.history_delete_failed, model.uiState.value.mutationError)
        assertEquals(listOf(row), model.uiState.value.rawItems)
        assertEquals("Song", model.uiState.value.searchQuery)
        model.clearMutationError()
        assertNull(model.uiState.value.mutationError)

        failPage = null
        model.refresh()
        assertFalse(model.uiState.value.loadFailed)
    }
}
