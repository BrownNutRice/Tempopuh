package me.avinas.tempo.data.applemusic

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipInputStream

internal class AppleMusicImportFiles(
    private val directory: File,
    private val checkActive: () -> Unit,
) {
    private var bytesRead = 0L
    private var zipEntries = 0
    val activity = mutableListOf<File>()
    val metadata = mutableListOf<File>()

    fun add(resolver: ContentResolver, uri: Uri) {
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }
        val stream = resolver.openInputStream(uri) ?: throw IOException("Missing Apple Music file")
        stream.buffered().use { input ->
            input.mark(4)
            val isZip = input.read() == 'P'.code && input.read() == 'K'.code
            input.reset()
            if (isZip) {
                readZip(input, depth = 0)
            } else {
                stage(input, if (isMetadata(name.orEmpty())) metadata else activity)
            }
        }
    }

    private fun readZip(input: InputStream, depth: Int) {
        ZipInputStream(BufferedInputStream(input)).use { zip ->
            while (true) {
                checkActive()
                val entry = zip.nextEntry ?: break
                if (++zipEntries > MAX_ZIP_ENTRIES) throw TooLarge()

                val name = entry.name.substringAfterLast('/')
                when {
                    entry.isDirectory -> copy(zip, null)
                    isActivity(name) -> stage(zip, activity)
                    isMetadata(name) -> stage(zip, metadata)
                    depth < 2 && isMediaServicesZip(name) -> {
                        val nested = File.createTempFile("media-", ".zip", directory)
                        try {
                            copy(zip, nested)
                            nested.inputStream().use { readZip(it, depth + 1) }
                        } finally {
                            nested.delete()
                        }
                    }
                    else -> copy(zip, null)
                }
                zip.closeEntry()
            }
        }
    }

    private fun stage(input: InputStream, destination: MutableList<File>) {
        if (activity.size + metadata.size >= MAX_CSV_FILES) throw TooLarge()
        val file = File.createTempFile("activity-", ".csv", directory)
        copy(input, file)
        destination += file
    }

    private fun copy(input: InputStream, file: File?) {
        file?.outputStream().use { output ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                checkActive()
                val size = input.read(buffer)
                if (size < 0) break
                bytesRead += size
                if (bytesRead > MAX_EXPANDED_BYTES) throw TooLarge()
                output?.write(buffer, 0, size)
            }
        }
    }

    internal class TooLarge : IOException()

    private companion object {
        const val MAX_EXPANDED_BYTES = 512L * 1024 * 1024
        const val MAX_ZIP_ENTRIES = 10_000
        const val MAX_CSV_FILES = 64

        fun isActivity(name: String): Boolean =
            name.equals("Apple Music Play Activity.csv", ignoreCase = true) ||
                name.equals("Apple Music - Play Activity.csv", ignoreCase = true)

        fun isMetadata(name: String): Boolean =
            name.equals("Apple Music - Play History Daily Tracks.csv", ignoreCase = true) ||
                name.equals("Apple Music - Track Play History.csv", ignoreCase = true) ||
                name.equals("Apple Music - Recently Played Tracks.csv", ignoreCase = true)

        fun isMediaServicesZip(name: String): Boolean =
            name.endsWith(".zip", ignoreCase = true) &&
                name.replace('_', ' ').startsWith("Apple Media Services", ignoreCase = true)
    }
}
