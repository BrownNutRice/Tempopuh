package me.avinas.tempo.data.drive

import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale

/** Ordering is independent of the stable, often random, run ID in the filename. */
internal object LocalBackupRetention {
    data class Backup(val documentId: String, val createdAt: Long?)

    /**
     * Unknown-age documents are preserved rather than guessed old. A provider may therefore
     * retain more than [limit] files until reliable dates are available. The just-verified
     * document always occupies a retained slot, including on idempotent retries.
     */
    fun deletions(backups: List<Backup>, protectedId: String, limit: Int): List<String> {
        require(limit > 0)
        val known = backups.distinctBy { it.documentId }
            .filter { it.createdAt != null && it.createdAt > 0L }
        val retained = known.filterNot { it.documentId == protectedId }
            .sortedWith(compareByDescending<Backup> { it.createdAt }.thenBy { it.documentId })
            .take(limit - 1)
            .mapTo(mutableSetOf()) { it.documentId }
        retained += protectedId
        return known.filterNot { it.documentId in retained }.map { it.documentId }
    }

    // Legacy names used local wall time. Only parse the exact timestamp prefix, never a UUID.
    fun legacyTimestamp(displayName: String): Long? {
        val timestamp = Regex("^tempo_auto_backup_(\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2}-\\d{3})(?:\\.tempo|[._ -])")
            .find(displayName)?.groupValues?.get(1) ?: return null
        val format = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss-SSS", Locale.US).apply { isLenient = false }
        val position = ParsePosition(0)
        return format.parse(timestamp, position)?.time
            ?.takeIf { position.index == timestamp.length && it > 0L }
    }
}
