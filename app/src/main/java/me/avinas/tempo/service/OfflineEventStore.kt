package me.avinas.tempo.service

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

/**
 * Durable storage for [MusicTrackingManager]'s offline event queue.
 *
 * Events land in the offline queue only when a database insert failed. Keeping
 * them on disk means an OEM kill, crash, or reboot can no longer discard plays
 * that were already recorded but not yet persisted — they are restored into
 * the queue on the next process start and retried.
 *
 * Uses SharedPreferences + Moshi, matching [SessionPersistence].
 */
class OfflineEventStore(context: Context) {

    companion object {
        private const val TAG = "OfflineEventStore"
        private const val PREFS_NAME = "tempo_offline_event_queue"
        private const val KEY_EVENTS = "offline_events"
    }

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences by lazy {
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val listType = Types.newParameterizedType(List::class.java, PendingEvent::class.java)
    private val adapter = moshi.adapter<List<PendingEvent>>(listType)

    /**
     * Persist the full offline queue (replaces whatever was stored before).
     * An empty collection removes the stored entry.
     */
    @Synchronized fun save(events: Collection<PendingEvent>) {
        try {
            preserveCorruptQueue()
            if (events.isEmpty()) {
                checkedPayload = null
                check(prefs.edit().remove(KEY_EVENTS).commit()) { "Unable to clear offline events" }
                return
            }
            val payload = adapter.toJson(events.toList())
            check(prefs.edit().putString(KEY_EVENTS, payload).commit()) { "Unable to persist offline events" }
            checkedPayload = payload
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save offline events", e)
            throw e
        }
    }

    private var checkedPayload: String? = null
    private var corruptionPending: Pair<String, String>? = null

    /** Preserve unreadable payloads before allowing any replacement queue to be written. */
    private fun preserveCorruptQueue() {
        if (corruptionPending == null) {
            val raw = prefs.getString(KEY_EVENTS, null) ?: return
            if (raw === checkedPayload) return
            if (runCatching { requireNotNull(adapter.fromJson(raw)) }.isSuccess) {
                checkedPayload = raw
                return
            }
            corruptionPending = "quarantined_events_${java.util.UUID.randomUUID()}" to raw
        }
        val (key, raw) = requireNotNull(corruptionPending)
        // Retain pending bytes in memory too: commit(false) still mutates SharedPreferences memory.
        check(prefs.edit().putString(key, raw).remove(KEY_EVENTS)
            .putBoolean("queue_recovery_required", true).commit()) {
            "Unable to preserve corrupt offline queue; refusing overwrite"
        }
        corruptionPending = null
        checkedPayload = null
    }

    fun hasRecoveryData(): Boolean = prefs.getBoolean("queue_recovery_required", false)

    /** Load active events; corrupt payloads remain available in the recovery entries. */
    @Synchronized fun load(): List<PendingEvent> {
        return try {
            val json = prefs.getString(KEY_EVENTS, null) ?: return emptyList()
            requireNotNull(adapter.fromJson(json)).also { checkedPayload = json }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load offline events; preserving original queue", e)
            preserveCorruptQueue()
            emptyList()
        }
    }

    @Synchronized fun clear() {
        preserveCorruptQueue()
        check(prefs.edit().remove(KEY_EVENTS).commit()) { "Unable to clear offline events" }
        checkedPayload = null
    }
}
