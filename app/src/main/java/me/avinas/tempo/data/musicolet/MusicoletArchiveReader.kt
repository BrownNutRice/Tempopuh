package me.avinas.tempo.data.musicolet

import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.io.PushbackInputStream
import java.util.Locale
import java.util.zip.ZipInputStream
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * The play-count databases Musicolet keeps alongside `DB_SONGS_LOG`.
 *
 * Musicolet does not store a timestamp per play. It stores a *count* per song per
 * period, in one SQLite file per period: `PCs_M_<year>.<month>` for months,
 * `PCs_W_<year>.<week>` for weeks and `PCs_Y_<year>` for years. Those period files
 * are what makes a dated reconstruction possible at all.
 */
internal enum class MusicoletPeriodKind { MONTH, WEEK, YEAR }

/** One decrypted `PCs_*` play-count database, plus the period it covers. */
internal data class MusicoletPeriodFile(
    val file: File,
    val kind: MusicoletPeriodKind,
    val year: Int,
    /** Month 1–12 or week number; null when the file name carries no bucket. */
    val bucket: Int?,
)

/** What a Musicolet backup (or a bare database file) yielded. */
internal data class MusicoletArchive(
    val songLog: File?,
    val periodFiles: List<MusicoletPeriodFile>,
    val entriesSeen: Int,
    val periodFilesSkipped: Int,
)

/**
 * Extracts the listening databases from a Musicolet backup.
 *
 * Musicolet writes a ZIP whose members are Blowfish-encrypted databases (see
 * [MusicoletCrypto]). A user may instead hand us a single member they already
 * pulled out of the ZIP — the `DB_SONGS_LOG` file on its own — so both shapes are
 * accepted and distinguished by content, not by extension.
 *
 * Entry names are never used to build a path. Only a strict allow-list of names is
 * written, and each is written to a name this class composes, so a hostile or
 * merely odd backup cannot escape the temporary directory.
 */
internal object MusicoletArchiveReader {
    /** `DB_SONGS_LOG` — the per-song totals, ratings and last-played times. */
    const val SONG_DB_NAME = "DB_SONGS_LOG"

    /** A backup carries one file per month of use; this is years beyond plausible. */
    private const val MAX_ENTRIES = 4_000

    /** Per-member cap: a large library's song database is a few MB. */
    private const val MAX_MEMBER_BYTES = 256L * 1024 * 1024

    /** Total decompressed cap, so a zip bomb cannot fill the cache directory. */
    private const val MAX_TOTAL_BYTES = 1024L * 1024 * 1024

    private val PERIOD_NAME = Regex("^PCs_([MWY])_(\\d{4})(?:\\.(\\d+))?$", RegexOption.IGNORE_CASE)

    /** `PCs_M_2026.6`, `PCs_W_2026.23`, `PCs_Y_2026`, `PCs_M_2019` (no bucket). */
    fun parsePeriodName(name: String): MusicoletPeriodFile? {
        val match = PERIOD_NAME.matchEntire(name.trim()) ?: return null
        val (code, year, bucket) = match.destructured
        return MusicoletPeriodFile(
            file = File(name),
            kind =
                when (code.uppercase(Locale.ROOT)) {
                    "M" -> MusicoletPeriodKind.MONTH
                    "W" -> MusicoletPeriodKind.WEEK
                    else -> MusicoletPeriodKind.YEAR
                },
            year = year.toIntOrNull() ?: return null,
            bucket = bucket.toIntOrNull(),
        )
    }

    /**
     * Reads [stream] into [workDir], decrypting every listening database it holds.
     *
     * [fileName] is only consulted to decide ZIP-versus-bare before the bytes are
     * sniffed; a `.zip` that is not a ZIP and a ZIP with no extension both work.
     */
    fun read(
        stream: InputStream,
        fileName: String,
        workDir: File,
    ): MusicoletArchive {
        workDir.mkdirs()
        val buffered = BufferedInputStream(stream, 64 * 1024)
        buffered.mark(4)
        val magic = ByteArray(4)
        val read = buffered.read(magic)
        buffered.reset()
        val isZip =
            (read >= 4 &&
                magic[0] == 0x50.toByte() && magic[1] == 0x4B.toByte() &&
                magic[2] == 0x03.toByte() && magic[3] == 0x04.toByte()) ||
                fileName.endsWith(".zip", ignoreCase = true) ||
                fileName.endsWith(".backup", ignoreCase = true)

        return if (isZip) readZip(buffered, workDir) else readBareDatabase(buffered, workDir)
    }

    private fun readBareDatabase(
        stream: InputStream,
        workDir: File,
    ): MusicoletArchive {
        val target = File(workDir, SONG_DB_NAME)
        val imported = try {
            decryptToFile(stream, target, MAX_MEMBER_BYTES)
        } catch (_: MemberLimitExceeded) {
            null
        }
        if (imported == null) {
            return MusicoletArchive(null, emptyList(), 1, 0)
        }
        return MusicoletArchive(
            songLog = target,
            periodFiles = emptyList(),
            entriesSeen = 1,
            periodFilesSkipped = 0,
        )
    }

    private fun readZip(
        stream: InputStream,
        workDir: File,
    ): MusicoletArchive {
        var songLog: File? = null
        val periods = mutableListOf<MusicoletPeriodFile>()
        var entriesSeen = 0
        var skipped = 0
        var totalBytes = 0L

        ZipInputStream(stream).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) continue
                entriesSeen++
                if (entriesSeen > MAX_ENTRIES) break

                // Only the base name matters: Musicolet writes at the ZIP root, but a
                // user-rezipped backup may nest the members.
                val baseName = entry.name.substringAfterLast('/').substringAfterLast('\\')
                val period = parsePeriodName(baseName)
                val isSongLog = baseName.equals(SONG_DB_NAME, ignoreCase = true)
                if (!isSongLog && period == null) {
                    // Not a listening database (settings, playlists, the `0.musicolet.backup`
                    // MD5 manifest). Skipped without reading, so a large unrelated member
                    // costs nothing.
                    continue
                }

                val remaining = MAX_TOTAL_BYTES - totalBytes
                if (remaining <= 0) break
                val memberBudget = minOf(MAX_MEMBER_BYTES, remaining)
                val safeName = if (isSongLog) SONG_DB_NAME else periodFileName(requireNotNull(period))
                val target = File(workDir, safeName)
                val bytesRead = try {
                    decryptToFile(zip, target, memberBudget)
                } catch (_: MemberLimitExceeded) {
                    // Do not ask ZipInputStream to drain the rest of an oversized
                    // entry merely to reach later members. Stop this archive safely.
                    skipped++
                    break
                }
                if (bytesRead == null) {
                    skipped++
                    continue
                }
                totalBytes += bytesRead
                if (isSongLog) songLog = target else if (period != null) periods.add(period.copy(file = target))
            }
        }

        return MusicoletArchive(
            songLog = songLog,
            periodFiles = periods,
            entriesSeen = entriesSeen,
            periodFilesSkipped = skipped,
        )
    }

    private fun periodFileName(period: MusicoletPeriodFile): String {
        val code =
            when (period.kind) {
                MusicoletPeriodKind.MONTH -> "M"
                MusicoletPeriodKind.WEEK -> "W"
                MusicoletPeriodKind.YEAR -> "Y"
            }
        val bucket = period.bucket?.let { ".$it" } ?: ""
        return "PCs_${code}_${period.year}$bucket"
    }

    /** Streams a bounded raw or Blowfish-encrypted SQLite member straight to disk. */
    private fun decryptToFile(stream: InputStream, target: File, limit: Long): Long? {
        val input = PushbackInputStream(BufferedInputStream(stream, 64 * 1024), 16)
        val prefix = ByteArray(16)
        val prefixSize = input.read(prefix)
        if (prefixSize <= 0) return null
        input.unread(prefix, 0, prefixSize)
        val rawSqlite = prefixSize == prefix.size && MusicoletCrypto.looksLikeSqlite(prefix)
        val cipher: Cipher? = if (rawSqlite) null else runCatching {
            Cipher.getInstance("Blowfish/ECB/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, SecretKeySpec(MusicoletCrypto.KEY.toByteArray(Charsets.UTF_8), "Blowfish"))
            }
        }.getOrNull()
        if (!rawSqlite && cipher == null) return null

        var total = 0L
        val buffer = ByteArray(64 * 1024)
        return try {
            target.outputStream().buffered().use { out ->
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > limit) throw MemberLimitExceeded()
                    if (cipher == null) {
                        out.write(buffer, 0, count)
                    } else {
                        val plain = cipher.update(buffer, 0, count)
                        if (plain != null && plain.isNotEmpty()) {
                            out.write(plain)
                        }
                    }
                }
                if (cipher != null) {
                    val final = cipher.doFinal()
                    out.write(final)
                }
            }

            if (cipher != null) {
                if (total == 0L || total % 8L != 0L) return null.also { target.delete() }
                // Musicolet's legacy tail padding is tolerated, but only strip a fully
                // consistent PKCS#5 block. Leave malformed tails unchanged as before.
                java.io.RandomAccessFile(target, "rw").use { file ->
                    if (file.length() >= 16) {
                        file.seek(file.length() - 1)
                        val pad = file.readUnsignedByte()
                        if (pad in 1..8 && pad <= file.length()) {
                            file.seek(file.length() - pad)
                            val tail = ByteArray(pad)
                            file.readFully(tail)
                            if (tail.all { (it.toInt() and 0xff) == pad }) file.setLength(file.length() - pad)
                        }
                    }
                }
            }
            val header = ByteArray(16)
            target.inputStream().use { if (it.read(header) != header.size || !MusicoletCrypto.looksLikeSqlite(header)) return null.also { target.delete() } }
            total
        } catch (limitExceeded: MemberLimitExceeded) {
            target.delete()
            throw limitExceeded
        } catch (_: Exception) {
            target.delete()
            null
        }
    }

    private class MemberLimitExceeded : Exception()
}
