package me.avinas.tempo.service

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import me.avinas.tempo.data.local.entities.ListeningEvent
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflineEventRecoveryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val prefs get() = context.getSharedPreferences("tempo_offline_event_queue", Context.MODE_PRIVATE)

    @Before fun reset() {
        check(prefs.edit().clear().commit())
        OfflineEventStore(context).clear()
    }
    @After fun cleanup() {
        OfflineEventStore(context).clear()
        check(prefs.edit().clear().commit())
    }

    @Test fun stagedPlaysAndAcknowledgementsSurviveAReopenedStore() {
        val first = pending("first", 10)
        val second = pending("second", 20)
        OfflineEventStore(context).apply {
            stage(first)
            stage(second)
        }
        val retried = second.copy(retryCount = 1, nextAttemptAt = 30)
        OfflineEventStore(context).apply {
            assertEquals(setOf(first, second), load().toSet())
            save(listOf(retried))
        }
        assertEquals(listOf(retried), OfflineEventStore(context).load())
    }

    @Test fun checkpointWithoutAnInitialLoadPreservesUnknownRows() {
        val recoveredLater = pending("previous-run", 10)
        val newPlay = pending("new-run", 20)
        OfflineEventStore(context).stage(recoveredLater)
        OfflineEventStore(context).apply {
            stage(newPlay)
            save(emptyList())
        }
        assertEquals(listOf(recoveredLater), OfflineEventStore(context).load())
    }

    private fun pending(sessionId: String, timestamp: Long) = PendingEvent(
        event = ListeningEvent(
            track_id = 1, timestamp = timestamp, playDuration = 10, completionPercentage = 90,
            source = "test", wasSkipped = false,
        ),
        sessionId = sessionId,
        queuedAt = timestamp,
        retryCount = 0,
    )

    @Test fun corruptQueueSurvivesLoadThenReplacementAndRestart() {
        val raw = "[{truncated-listening-event"
        check(prefs.edit().putString("offline_events", raw).commit())
        val store = OfflineEventStore(context)
        assertTrue(store.load().isEmpty())
        store.save(emptyList())
        assertTrue(OfflineEventStore(context).hasRecoveryData())
        assertTrue(prefs.all.filterKeys { it.startsWith("quarantined_events_") }.containsValue(raw))
    }

    @Test fun differentCorruptQueuesArePreservedIndependently() {
        val store = OfflineEventStore(context)
        listOf("{first-broken-queue", "{second-broken-queue").forEach { raw ->
            check(prefs.edit().putString("offline_events", raw).commit())
            store.save(emptyList())
        }
        val recovered = prefs.all.filterKeys { it.startsWith("quarantined_events_") }.values
        assertEquals(2, recovered.size)
        assertTrue(recovered.contains("{first-broken-queue"))
        assertTrue(recovered.contains("{second-broken-queue"))
    }

    @Test fun oldEmptyQueueNeedsNoRecovery() {
        check(prefs.edit().putString("offline_events", "[]").commit())
        val store = OfflineEventStore(context)
        assertTrue(store.load().isEmpty())
        assertFalse(store.hasRecoveryData())
    }

    @Test fun discardingRecoveryKeepsActivePlays() {
        check(prefs.edit().putString("offline_events", "{broken").commit())
        val event = pending("valid", 10)
        OfflineEventStore(context).stage(event)
        OfflineEventStore(context).apply {
            assertTrue(hasRecoveryData())
            discardRecoveryData()
        }
        OfflineEventStore(context).apply {
            assertFalse(hasRecoveryData())
            assertEquals(listOf(event), load())
        }
    }
}
