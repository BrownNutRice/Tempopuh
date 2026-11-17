package me.avinas.tempo.data.importexport

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.UUID

/** Owns only this restore's files. Existing library artwork is never opened for writing. */
internal class RestoreArtworkStaging(cacheDir: File, artworkDir: File) : AutoCloseable {
    private val operationId = UUID.randomUUID().toString()
    private val stagingDir = File(cacheDir, "restore_artwork_$operationId")
    private val destinationDir = File(artworkDir, "restore_$operationId")
    private val names = mutableSetOf<String>()
    private var promoted = false
    private var retained = false

    fun stage(name: String, input: InputStream, limit: Long): Long {
        check(!promoted)
        require(name.isNotBlank() && name.length <= 128 && !name.contains("..") &&
            !name.contains('/') && !name.contains('\\')) { "Unsafe backup image name" }
        if (!names.add(name)) throw IOException("Duplicate backup image entry")
        if (!stagingDir.isDirectory && !stagingDir.mkdirs()) throw IOException("Cannot stage backup artwork")
        val file = File(stagingDir, name)
        var size = 0L
        try {
            file.outputStream().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    size += read
                    if (size > limit) throw SecurityException("Image entry exceeds import size limit")
                    output.write(buffer, 0, read)
                }
            }
        } catch (error: Exception) {
            file.delete()
            throw error
        }
        return size
    }

    /** All archive entries/version checks must succeed before this is called. */
    fun promote(): Map<String, String> {
        check(!promoted)
        if (names.isEmpty()) return emptyMap()
        val parent = requireNotNull(destinationDir.parentFile)
        if (!parent.isDirectory && !parent.mkdirs()) throw IOException("Cannot create artwork directory")
        // A unique operation directory is renamed within app-private storage. Never replace a
        // live filename, even when restoring the same archive repeatedly.
        if (destinationDir.exists() || !stagingDir.renameTo(destinationDir)) {
            throw IOException("Cannot promote restored artwork")
        }
        promoted = true
        return names.associateWith { "file://${File(destinationDir, it).absolutePath}" }
    }

    /** Call as the final DB-transaction statement, before commit may make paths observable. */
    fun retainForCommit() {
        retained = true
    }

    override fun close() {
        stagingDir.deleteRecursively()
        // If commit outcome is uncertain (cancellation during Room's return), favor an orphan
        // over deleting a file a committed row may reference. A failed DB body never retains.
        if (promoted && !retained) destinationDir.deleteRecursively()
    }
}
