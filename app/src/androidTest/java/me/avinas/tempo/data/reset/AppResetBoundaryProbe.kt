package me.avinas.tempo.data.reset

import android.content.Context
import android.os.Build
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import me.avinas.tempo.data.local.AppDatabase
import me.avinas.tempo.data.local.entities.Track
import me.avinas.tempo.data.local.entities.ListeningEvent
import me.avinas.tempo.data.remote.spotify.SpotifyTokenStorage
import me.avinas.tempo.service.OfflineEventStore
import me.avinas.tempo.service.PendingEvent
import me.avinas.tempo.service.SessionPersistence
import me.avinas.tempo.service.SessionState
import me.avinas.tempo.ui.onboarding.dataStore
import me.avinas.tempo.worker.StatsPrecomputeWorker
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * External two-invocation probe, not a normal instrumentation test. Reset intentionally kills
 * its own runner; invoke the verification separately. See docs/audits/APP_RESET_2026-11-18.md.
 */
@RunWith(AndroidJUnit4::class)
class AppResetBoundaryProbe {
    private fun disposableEmulator(): Context {
        assumeTrue(InstrumentationRegistry.getArguments().getString("allowDestructiveResetProbe") == "yes")
        assumeTrue(Build.HARDWARE in setOf("ranchu", "goldfish"))
        return ApplicationProvider.getApplicationContext()
    }

    @Test fun requestResetWithPendingData() = runBlocking {
        val context = disposableEmulator()
        val database = Room.databaseBuilder(context, AppDatabase::class.java, "tempo.db").build()
        val trackId = database.trackDao().insert(Track(
            title = "Tempo reset probe", artist = "Reset fixture", album = null,
            duration = 180_000, albumArtUrl = null, spotifyId = null, musicbrainzId = null
        ))
        val event = ListeningEvent(track_id = trackId, timestamp = System.currentTimeMillis(),
            playDuration = 120_000, completionPercentage = 66, source = "reset-probe", wasSkipped = false)
        database.listeningEventDao().insertAllBatchedWithDedup(listOf(event))
        OfflineEventStore(context).save(listOf(PendingEvent(event, "reset-probe-session", event.timestamp, 0)))
        SessionPersistence(context).saveSessions(mapOf("reset-probe-session" to SessionState(
            sessionId = "reset-probe-session", packageName = "reset-probe", trackId = trackId,
            trackTitle = "Tempo reset probe", trackArtist = "Reset fixture", trackAlbum = null,
            startTimestamp = event.timestamp, lastResumeTimestamp = event.timestamp,
            totalPlayedMs = 120_000, isPlaying = true, pauseCount = 0, estimatedDurationMs = 180_000
        )))
        context.dataStore.edit { it[booleanPreferencesKey("onboarding_completed")] = true }
        val prefs = context.getSharedPreferences("tempo_offline_event_queue", Context.MODE_PRIVATE)
        check(prefs.edit().putString("quarantined_events_probe", "private probe bytes")
            .putBoolean("queue_recovery_required", true).commit())
        check(context.getSharedPreferences("tempo_session_persistence", Context.MODE_PRIVATE)
            .edit().putString("reset_probe", "old recovery state").commit())
        SpotifyTokenStorage(context).apply {
            saveTokens("reset-probe-access", "reset-probe-refresh", Long.MAX_VALUE, "probe")
            savePendingAuth("reset-probe-verifier", "reset-probe-state")
        }
        File(context.filesDir, "reset_probe_before").writeText("private fixture")
        File(context.cacheDir, "reset_probe_snapshot").writeText("staged private fixture")
        val work = OneTimeWorkRequestBuilder<StatsPrecomputeWorker>()
            .setInitialDelay(1, TimeUnit.DAYS).addTag("tempo-reset-probe").build()
        WorkManager.getInstance(context).enqueue(work).result.get(5, TimeUnit.SECONDS)
        assertTrue(OfflineEventStore(context).hasRecoveryData())

        // This writer is deliberately independent of ViewModel/service coroutine cancellation.
        // The operating-system boundary must stop the process before it can recreate a file.
        Thread {
            Thread.sleep(1_500)
            File(context.filesDir, "reset_probe_stale_writer").apply { parentFile?.mkdirs() }
                .writeText("old writer survived")
        }.start()
        AppDataResetter(context).requestReset()
        CountDownLatch(1).await(10, TimeUnit.SECONDS)
        fail("The reset probe process remained alive; inspect device logs")
    }

    @Test fun verifyAfterReset() = runBlocking {
        val context = disposableEmulator()
        // Allow the deliberately delayed pre-reset writer's deadline to pass.
        Thread.sleep(2_000)
        assertFalse(File(context.filesDir, "reset_probe_before").exists())
        assertFalse(File(context.filesDir, "reset_probe_stale_writer").exists())
        assertFalse(File(context.cacheDir, "reset_probe_snapshot").exists())
        val queuePrefs = context.getSharedPreferences("tempo_offline_event_queue", Context.MODE_PRIVATE)
        assertFalse(queuePrefs.contains("quarantined_events_probe"))
        assertFalse(queuePrefs.getBoolean("queue_recovery_required", false))
        assertTrue(OfflineEventStore(context).load().isEmpty())
        assertTrue(SessionPersistence(context).loadSessions().isEmpty())
        assertNotEquals(true, context.dataStore.data.first()[booleanPreferencesKey("onboarding_completed")])
        assertFalse(context.getSharedPreferences("tempo_session_persistence", Context.MODE_PRIVATE).contains("reset_probe"))
        SpotifyTokenStorage(context).apply {
            assertNull(getAccessToken())
            assertNull(getRefreshToken())
            assertFalse(hasPendingAuth())
        }
        assertTrue(WorkManager.getInstance(context).getWorkInfosByTag("tempo-reset-probe")
            .get(5, TimeUnit.SECONDS).isEmpty())
        val database = Room.databaseBuilder(context, AppDatabase::class.java, "tempo.db").build()
        try {
            assertFalse(database.trackDao().getAllSync().any { it.title == "Tempo reset probe" })
            assertEquals(0, database.listeningEventDao().getCount())
        } finally { database.close() }
    }
}
