package me.avinas.tempo.service

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.core.database.sqlite.transaction
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.util.UUID

/** Persists pending plays independently of the library so a library write failure can be retried. */
class OfflineEventStore(context: Context) {
    private companion object {
        const val DATABASE = "tempo_offline_events.db"
        const val PREFS_NAME = "tempo_offline_event_queue"
        const val KEY_EVENTS = "offline_events"
        const val KEY_RECOVERY_REQUIRED = "queue_recovery_required"
        val migrationLock = Any()
        private var sharedDatabase: QueueDatabase? = null

        @Synchronized
        fun database(context: Context): QueueDatabase =
            sharedDatabase ?: QueueDatabase(context).also { sharedDatabase = it }
    }

    private val appContext = context.applicationContext
    private val prefs by lazy { appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    private val helper = database(appContext)
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val eventAdapter = moshi.adapter(PendingEvent::class.java)
    private val legacyAdapter = moshi.adapter<List<PendingEvent>>(
        Types.newParameterizedType(List::class.java, PendingEvent::class.java),
    )
    // Only acknowledge rows this instance loaded or wrote. If startup loading fails
    // temporarily, a later checkpoint must leave any unknown durable rows alone.
    private var persisted = mutableMapOf<String, PendingEvent>()
    private var pendingQuarantine: Pair<String, String>? = null

    /** One new play touches one row, regardless of the size of the backlog. */
    @Synchronized
    fun stage(event: PendingEvent) {
        val db = open()
        db.putEvent(event)
        persisted[event.key()] = event
    }

    /** Checkpoint only the rows changed or acknowledged since the last durable write. */
    @Synchronized
    fun save(events: Collection<PendingEvent>) {
        val db = open()
        val current = events.associateBy(PendingEvent::key)
        val removed = persisted.keys - current.keys
        val changed = current.values.filter { persisted[it.key()] != it }
        if (removed.isEmpty() && changed.isEmpty()) return

        db.transaction {
            removed.forEach { key -> delete("pending", "event_key = ?", arrayOf(key)) }
            changed.forEach { putEvent(it) }
        }
        persisted = current.toMutableMap()
    }

    @Synchronized
    fun load(): List<PendingEvent> = readEvents(open()).values.toList()

    @Synchronized
    fun clear() {
        open().delete("pending", null, null)
        persisted.clear()
    }

    @Synchronized
    fun hasRecoveryData(): Boolean =
        prefs.getBoolean(KEY_RECOVERY_REQUIRED, false) ||
            open().rawQuery("SELECT 1 FROM quarantine LIMIT 1", null).use { it.moveToFirst() }

    /** Explicitly discard unreadable records. The active queue is unaffected. */
    @Synchronized
    fun discardRecoveryData() {
        synchronized(migrationLock) {
            val db = open()
            val editor = prefs.edit()
            prefs.all.keys.filter { it.startsWith("quarantined_events_") }.forEach { editor.remove(it) }
            check(editor.remove(KEY_RECOVERY_REQUIRED).commit()) { "Unable to clear legacy recovery data" }
            db.delete("quarantine", null, null)
        }
    }

    private fun open(): SQLiteDatabase {
        val db = helper.writableDatabase
        synchronized(migrationLock) {
            val raw = prefs.getString(KEY_EVENTS, null)
            if (pendingQuarantine == null && raw != null) {
                val legacy = runCatching { requireNotNull(legacyAdapter.fromJson(raw)) }.getOrNull()
                if (legacy != null) {
                    db.transaction {
                        val migrated = rawQuery("SELECT 1 FROM legacy_migration LIMIT 1", null).use { it.moveToFirst() }
                        if (!migrated) {
                            legacy.forEach { putEvent(it) }
                            execSQL("INSERT INTO legacy_migration DEFAULT VALUES")
                        }
                    }
                    // The marker and imported rows commit together. A stale preference left by
                    // a crash cannot put acknowledged plays back into the pending queue.
                    prefs.edit().remove(KEY_EVENTS).commit()
                } else {
                    pendingQuarantine = "quarantined_events_${UUID.randomUUID()}" to raw
                }
            }
            pendingQuarantine?.let { (key, payload) ->
                check(prefs.edit().putString(key, payload).remove(KEY_EVENTS)
                    .putBoolean(KEY_RECOVERY_REQUIRED, true).commit()) {
                    "Unable to preserve the legacy offline event queue"
                }
                pendingQuarantine = null
            }
        }
        return db
    }

    private fun readEvents(db: SQLiteDatabase): MutableMap<String, PendingEvent> {
        val events = linkedMapOf<String, PendingEvent>()
        val unreadable = linkedMapOf<String, String>()
        db.query("pending", arrayOf("event_key", "payload"), null, null, null, null, null).use { cursor ->
            while (cursor.moveToNext()) {
                val key = cursor.getString(0)
                val payload = cursor.getString(1)
                val event = runCatching { eventAdapter.fromJson(payload) }.getOrNull()
                if (event?.key() == key) events[key] = event else unreadable[key] = payload
            }
        }
        if (unreadable.isNotEmpty()) {
            db.transaction {
                unreadable.forEach { (key, payload) ->
                    execSQL("INSERT INTO quarantine(payload) VALUES (?)", arrayOf(payload))
                    delete("pending", "event_key = ?", arrayOf(key))
                }
            }
        }
        persisted = events
        return events
    }

    private fun SQLiteDatabase.putEvent(event: PendingEvent) {
        val row = ContentValues(2).apply {
            put("event_key", event.key())
            put("payload", eventAdapter.toJson(event))
        }
        check(insertWithOnConflict("pending", null, row, SQLiteDatabase.CONFLICT_REPLACE) != -1L) {
            "Unable to persist offline event"
        }
    }

    private class QueueDatabase(context: Context) : SQLiteOpenHelper(context, DATABASE, null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE pending(event_key TEXT PRIMARY KEY NOT NULL, payload TEXT NOT NULL)")
            db.execSQL("CREATE TABLE quarantine(payload TEXT NOT NULL)")
            db.execSQL("CREATE TABLE legacy_migration(id INTEGER PRIMARY KEY)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) =
            error("Unsupported offline queue upgrade from $oldVersion to $newVersion")
    }
}
