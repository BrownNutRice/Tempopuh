package me.avinas.tempo.data.local

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Pre-migration safety net, originally added for the schema-52 reconciliation release (v4.8.3)
 * and now guarding the squashed schema 55 -> 58 upgrade.
 *
 * Context: two divergent schema-51 lineages exist in the wild (public 4.8.2 and
 * internal builds). MIGRATION_51_52 reconciles them, but the app manifest sets
 * android:allowBackup="false", so for most users the on-device database is the
 * ONLY copy of their listening history.
 *
 * This net takes a one-time raw file snapshot of the database BEFORE Room ever
 * opens/migrates it on this install. If the migration were to fail on some
 * unforeseen third schema shape, the untouched pre-migration files remain on
 * disk in a tempo-pre* directory for manual recovery (restore as tempo.db — the previous version can
 * open it again).
 *
 * Design constraints:
 * - Never opens the database through SQLite/Room (no WAL recovery side effects,
 *   no locks): it only copies files.
 * - Runs at most once per target schema version per install (flagged in
 *   SharedPreferences), so there is no per-launch cost.
 * - Any failure here is logged and swallowed: the safety net must never itself
 *   block app startup.
 */
object MigrationSafetyNet {

    private const val TAG = "MigrationSafetyNet"
    private const val PREFS_NAME = "migration_safety_net"
    private const val KEY_SNAPSHOT_DONE_FOR_VERSION = "snapshot_done_for_version"

    /**
     * The schema version this safety net guards. Bump when the next risky migration ships.
     *
     * 55 is what v4.8.11 shipped and therefore what every existing install is on, so the net now
     * fires once for the whole 55 -> 58 upgrade. It sat at 52 while the two riskiest steps in
     * that upgrade were still local-only, which meant a user already past schema 52 got no
     * snapshot at all -- including before the tz_offset_minutes backfill, which rewrites every
     * row of the largest table in the database.
     */
    private const val GUARDED_VERSION = 55

    /** Raw suffixes Room/SQLite may have created next to the main database file. */
    private val DB_FILE_SUFFIXES = listOf("", "-wal", "-shm", "-journal")

    fun snapshotBeforeMigrationIfNeeded(context: Context) {
        try {
            val dbFile = context.getDatabasePath("tempo.db")
            if (!dbFile.exists()) {
                // Fresh install — nothing to protect.
                return
            }

            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (prefs.getInt(KEY_SNAPSHOT_DONE_FOR_VERSION, 0) >= GUARDED_VERSION) {
                // Already snapshotted for this schema step.
                return
            }

            val snapshotDir = File(dbFile.parentFile, "tempo-pre$GUARDED_VERSION-${java.util.UUID.randomUUID()}")
            check(snapshotDir.mkdir()) { "Unable to create migration snapshot directory" }
            var complete = false
            try {
                val sources = DB_FILE_SUFFIXES.map { File(dbFile.parentFile, "tempo.db$it") }
                    .filter { it.exists() }
                check(sources.any { it == dbFile }) { "Database disappeared before snapshot" }
                sources.forEach { source ->
                    val target = File(snapshotDir, source.name)
                    source.inputStream().use { input ->
                        java.io.FileOutputStream(target).use { output ->
                            input.copyTo(output)
                            output.fd.sync()
                        }
                    }
                    check(target.length() == source.length()) { "Incomplete migration snapshot" }
                }
                check(prefs.edit().putInt(KEY_SNAPSHOT_DONE_FOR_VERSION, GUARDED_VERSION).commit()) {
                    "Unable to record complete migration snapshot"
                }
                complete = true
                Log.i(TAG, "Pre-migration snapshot complete (${sources.size} files)")
            } finally {
                // Failed attempts cannot suppress the next attempt or accumulate partial copies.
                if (!complete) snapshotDir.deleteRecursively()
            }

        } catch (e: Exception) {
            // Absolutely never block startup because of the safety net itself.
            Log.w(TAG, "Safety snapshot skipped due to error", e)
        }
    }
}
