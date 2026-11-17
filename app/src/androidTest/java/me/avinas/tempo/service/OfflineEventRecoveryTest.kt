package me.avinas.tempo.service

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflineEventRecoveryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val prefs get() = context.getSharedPreferences("tempo_offline_event_queue", Context.MODE_PRIVATE)

    @Before fun reset() { check(prefs.edit().clear().commit()) }
    @After fun cleanup() { check(prefs.edit().clear().commit()) }

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
}
