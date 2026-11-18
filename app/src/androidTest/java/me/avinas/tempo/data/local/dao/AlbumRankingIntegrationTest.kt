package me.avinas.tempo.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import me.avinas.tempo.data.local.AppDatabase
import me.avinas.tempo.data.local.entities.EnrichedMetadata
import me.avinas.tempo.data.local.entities.ListeningEvent
import me.avinas.tempo.data.local.entities.Track
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlbumRankingIntegrationTest {
    @Test fun albumCountsAndPagesUseTheSameArtistAndReleaseRules() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build().use { db ->
            suspend fun play(artist: String, album: String, track: Int, releaseType: String? = null) {
                val trackId = db.trackDao().insert(Track(
                    title = "Track $track", artist = artist, album = album, duration = 180_000,
                    albumArtUrl = null, spotifyId = null, musicbrainzId = null,
                ))
                if (releaseType != null) {
                    db.enrichedMetadataDao().upsert(EnrichedMetadata(trackId = trackId, releaseType = releaseType))
                }
                db.listeningEventDao().insertAllBatchedWithDedup(listOf(ListeningEvent(
                    track_id = trackId, timestamp = 1_700_000_000_000 + track,
                    playDuration = 60_000, completionPercentage = 33, source = "fixture", wasSkipped = false,
                )))
            }
            for (track in 1..2) {
                play("Artist B", "Same Title", track)
                play("Artist A", "Same Title", track, "EP")
                play("Artist C", "Single", track, "SINGLE")
            }
            play("Artist D", "One Track", 1, "Album")
            val dao = db.statsDao()
            val end = Long.MAX_VALUE
            assertEquals(2, dao.getUniqueAlbumsCountFiltered(0, end, false, false))
            val overview = dao.getCombinedBasicStatsFiltered(0, end, false, false)
            assertEquals(2, overview.uniqueAlbums)
            assertEquals(7, overview.playCount)
            assertEquals("Artist A", dao.getTopAlbumsByPlayCountFiltered(0, end, false, false, 1, 0).single().artist)
            assertEquals("Artist B", dao.getTopAlbumsByPlayCountFiltered(0, end, false, false, 1, 1).single().artist)
            assertEquals(emptyList<Any>(), dao.getTopAlbumsByPlayCountFiltered(0, end, false, false, 1, 2))
        }
    }
}
