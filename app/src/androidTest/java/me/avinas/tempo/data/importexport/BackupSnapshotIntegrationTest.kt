package me.avinas.tempo.data.importexport

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.squareup.moshi.JsonReader
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.avinas.tempo.data.local.AppDatabase
import me.avinas.tempo.data.local.entities.ListeningEvent
import me.avinas.tempo.data.local.entities.Track
import me.avinas.tempo.data.profile.ProfileIdentity
import okio.buffer
import okio.source
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class BackupSnapshotIntegrationTest {
    private lateinit var database: AppDatabase
    private lateinit var file: File

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        file = File.createTempFile("backup-snapshot-test", ".json", context.cacheDir)
    }

    @After fun tearDown() {
        database.close()
        file.delete()
    }

    @Test fun concurrentMergeCannotRemapEventsOutsideTheExportedTrackSet() = runBlocking {
        withTimeout(15_000) {
            val originalId = database.trackDao().insert(Track(
                title = "Original", artist = "Artist", album = null, duration = 180_000,
                albumArtUrl = null, spotifyId = null, musicbrainzId = null
            ))
            database.listeningEventDao().insertAllBatchedWithDedup(listOf(ListeningEvent(
                track_id = originalId, timestamp = 1_700_000_000_000,
                playDuration = 120_000, completionPercentage = 66, source = "test", wasSkipped = false
            )))
            val tablesRead = CompletableDeferred<Unit>()
            val mergeStarted = CompletableDeferred<Unit>()
            val allowPaging = CountDownLatch(1)
            val codec = TempoExportJsonCodec(buildImportExportMoshi())
            val export = async(Dispatchers.IO) {
                BackupSnapshotWriter(database, codec).write(file, ProfileIdentity()) { shell ->
                    tablesRead.complete(Unit)
                    check(allowPaging.await(5, TimeUnit.SECONDS)) { "Snapshot barrier timed out" }
                    shell
                }
            }
            try {
                tablesRead.await()
                val merge = async(Dispatchers.IO) {
                    mergeStarted.complete(Unit)
                    database.withTransaction {
                        val targetId = database.trackDao().insert(Track(
                            title = "Merged", artist = "Artist", album = null, duration = 180_000,
                            albumArtUrl = null, spotifyId = null, musicbrainzId = null
                        ))
                        database.openHelper.writableDatabase.execSQL(
                            "UPDATE listening_events SET track_id = ? WHERE track_id = ?",
                            arrayOf(targetId, originalId)
                        )
                        database.openHelper.writableDatabase.execSQL(
                            "DELETE FROM tracks WHERE id = ?", arrayOf(originalId)
                        )
                    }
                }
                mergeStarted.await()
                assertFalse(merge.isCompleted)
                allowPaging.countDown()
                assertEquals(1, export.await().eventsCount)
                merge.await()

                val events = mutableListOf<ListeningEvent>()
                val shell = file.source().buffer().use { source ->
                    codec.read(JsonReader.of(source), TempoExportJsonCodec.StreamHandlers(
                        onListeningEvent = { events += it }
                    ))
                }
                assertEquals(listOf(originalId), shell.tracks.map { it.id })
                assertEquals(listOf(originalId), events.map { it.track_id })
                assertTrue(events.all { event -> shell.tracks.any { it.id == event.track_id } })
                assertEquals("Merged", database.trackDao().getAllSync().single().title)
            } finally {
                allowPaging.countDown()
            }
        }
    }
}
