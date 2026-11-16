package me.avinas.tempo.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import me.avinas.tempo.data.local.AppDatabase
import me.avinas.tempo.data.local.entities.ListeningEvent
import me.avinas.tempo.data.local.entities.Track
import me.avinas.tempo.data.repository.RoomListeningRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Verifies local + Spotify API write order using actual Room transactions. */
@RunWith(AndroidJUnit4::class)
class ListeningReconciliationIntegrationTest {
    private lateinit var database: AppDatabase
    private lateinit var context: Context

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After fun tearDown() {
        database.close()
    }

    private suspend fun trackId(): Long = database.trackDao().insert(
        Track(
            title = "Same Song", artist = "Same Artist",
            album = null, duration = 180_000L, albumArtUrl = null,
            spotifyId = "spotify-track-1", musicbrainzId = null,
        )
    )

    private fun apiPlay(trackId: Long) = ListeningEvent(
        track_id = trackId,
        timestamp = 1_700_000_180_000L, // Spotify records the END of a play.
        playDuration = 0L, // Unknown, not zero observed music.
        completionPercentage = 0, // Unknown, not a verified skip.
        source = "com.spotify.music.import",
        wasSkipped = false,
    )

    private fun localPlay(trackId: Long) = ListeningEvent(
        track_id = trackId,
        timestamp = 1_700_000_120_000L, // Local records the START.
        playDuration = 60_000L,
        completionPercentage = 40,
        source = "com.spotify.music",
        wasSkipped = false,
        endTimestamp = 1_700_000_180_000L,
        sessionId = "test-live-session",
    )

    private fun repository() = RoomListeningRepository(
        dao = database.listeningEventDao(),
        trackDao = database.trackDao(),
        manualContentMarkDao = database.manualContentMarkDao(),
        enrichedMetadataDao = database.enrichedMetadataDao(),
        context = context,
    )

    @Test fun apiFirstThenLiveReplacesUnknownDuration() = runBlocking {
        val id = trackId()
        val dao = database.listeningEventDao()
        assertEquals(1, dao.insertAllBatchedWithDedup(listOf(apiPlay(id))).inserted)
        assertTrue(repository().insert(localPlay(id)) > 0L)

        val rows = dao.getEventsForTrack(id)
        assertEquals(1, rows.size)
        assertEquals("com.spotify.music", rows.single().source)
        assertEquals(60_000L, rows.single().playDuration)
    }

    @Test fun liveFirstThenApiKeepsLocalTime() = runBlocking {
        val id = trackId()
        val dao = database.listeningEventDao()
        assertTrue(repository().insert(localPlay(id)) > 0L)
        assertEquals(0, dao.insertAllBatchedWithDedup(listOf(apiPlay(id))).inserted)
        assertEquals(1, dao.getEventsForTrack(id).size)
    }

    @Test fun duplicateLiveCompletionIsNotRetriedAsFailedInsert() = runBlocking {
        val id = trackId()
        val live = localPlay(id)
        val listening = repository()
        assertTrue(listening.insert(live) > 0L)
        assertEquals(0L, listening.insert(live.copy()))
        assertEquals(1, database.listeningEventDao().getEventsForTrack(id).size)
    }

    @Test fun importedUnknownCompletionIsNotCountedAsSkipped() = runBlocking {
        val id = trackId()
        database.listeningEventDao().insertAllBatchedWithDedup(listOf(apiPlay(id)))
        assertEquals(0, database.statsDao().getSkipsCount(0L, Long.MAX_VALUE))
    }
    @Test fun historicalRepairOnlyResetsUnsupportedSpotifyApiValues() = runBlocking {
        val id = trackId()
        val dao = database.listeningEventDao()
        // Simulate an older importer that guessed a full song's listening
        // duration from the Spotify recently-played catalog duration.
        val badApi = apiPlay(id).copy(playDuration = 180_000L, completionPercentage = 100)
        val live = localPlay(id).copy(timestamp = badApi.timestamp - 1_000_000L,
            endTimestamp = badApi.timestamp - 940_000L)
        dao.insert(badApi)
        dao.insert(live)

        assertEquals(1, dao.countUnverifiableSpotifyApiPlays())
        assertEquals(1, dao.clearUnverifiableSpotifyApiEstimates())
        assertEquals(0, dao.countUnverifiableSpotifyApiPlays())

        val apiAfter = dao.getEventsForTrack(id).single { it.source == "com.spotify.music.import" }
        assertEquals(0L, apiAfter.playDuration)
        assertEquals(0, apiAfter.completionPercentage)
        assertEquals(id, apiAfter.track_id)
        assertEquals(badApi.timestamp, apiAfter.timestamp)
        assertEquals(60_000L, dao.getEventsForTrack(id)
            .single { it.source == "com.spotify.music" }.playDuration)
    }

    @Test fun distinctFastRepeatsAreSeparateSessions() = runBlocking {
        val id = trackId()
        val listening = repository()
        val first = localPlay(id)
        val repeated = first.copy(
            timestamp = first.timestamp + 2_000L,
            endTimestamp = first.endTimestamp!! + 2_000L,
            sessionId = "second-distinct-session",
        )
        assertTrue(listening.insert(first) > 0L)
        assertTrue(listening.insert(repeated) > 0L)
        assertEquals(2, database.listeningEventDao().getEventsForTrack(id).size)
    }

    @Test fun concurrentDesktopAndAndroidListeningAreIndependent() = runBlocking {
        val id = trackId()
        val listening = repository()
        val phone = localPlay(id)
        val desktop = phone.copy(
            source = "desktop:SpotifyWeb",
            sessionId = "desktop-session",
            playDuration = 60_000L,
        )
        assertTrue(listening.insert(phone) > 0L)
        assertTrue(listening.insert(desktop) > 0L)
        assertEquals(2, database.listeningEventDao().getEventsForTrack(id).size)
    }

}
