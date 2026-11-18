package me.avinas.tempo.data.local

import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * A durable revision for the tables read by the full XP and badge pass. Triggers also
 * cover imports, edits, and deletes that do not go through GamificationRepository.
 */
internal object GamificationFreshness {
    private const val TABLE = "gamification_refresh_state"
    private val sources = listOf(
        "listening_events", "track_artists", "tracks", "enriched_metadata",
        "daily_challenges", "user_level", "badges",
    )

    fun install(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS $TABLE " +
                "(id INTEGER PRIMARY KEY, revision INTEGER NOT NULL, refreshed_for TEXT)",
        )
        db.execSQL("INSERT OR IGNORE INTO $TABLE (id, revision) VALUES (1, 0)")
        for (table in sources) {
            for (operation in listOf("INSERT", "UPDATE", "DELETE")) {
                db.execSQL(
                    "CREATE TRIGGER IF NOT EXISTS gamification_${table}_$operation " +
                        "AFTER $operation ON $table BEGIN " +
                        "UPDATE $TABLE SET revision = revision + 1, refreshed_for = NULL WHERE id = 1; END",
                )
            }
        }
    }

    fun read(db: SupportSQLiteDatabase): State =
        db.query("SELECT revision, refreshed_for FROM $TABLE WHERE id = 1").use { cursor ->
            check(cursor.moveToFirst()) { "Gamification refresh state is missing" }
            State(cursor.getLong(0), cursor.getString(1))
        }

    fun markFresh(db: SupportSQLiteDatabase, revision: Long, key: String) {
        // A concurrent import, correction or changed badge output leaves the pass dirty.
        db.execSQL("UPDATE $TABLE SET refreshed_for = ? WHERE id = 1 AND revision = ?", arrayOf<Any>(key, revision))
    }

    data class State(val revision: Long, val refreshedFor: String?)
}
