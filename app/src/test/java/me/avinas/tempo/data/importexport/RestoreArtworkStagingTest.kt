package me.avinas.tempo.data.importexport

import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class RestoreArtworkStagingTest {
    private fun scenario(block: (File, File) -> Unit) {
        val root = Files.createTempDirectory("restore-artwork-test").toFile()
        try { block(File(root, "cache"), File(root, "album_art")) }
        finally { root.deleteRecursively() }
    }

    @Test fun `rejected archive cannot truncate live colliding artwork`() = scenario { cache, art ->
        art.mkdirs()
        val live = File(art, "cover.jpg").apply { writeText("original") }
        RestoreArtworkStaging(cache, art).use { staging ->
            staging.stage("cover.jpg", "replacement".byteInputStream(), 100)
            // Simulate invalid JSON or a newer version: promotion is never reached.
        }
        assertEquals("original", live.readText())
        assertTrue(cache.listFiles().isNullOrEmpty())
        assertEquals(listOf("cover.jpg"), art.list()!!.toList())
    }

    @Test fun `transaction failure cleans promoted files and preserves existing paths`() = scenario { cache, art ->
        art.mkdirs()
        val live = File(art, "cover.jpg").apply { writeText("original") }
        lateinit var promoted: File
        RestoreArtworkStaging(cache, art).use { staging ->
            staging.stage("cover.jpg", "replacement".byteInputStream(), 100)
            promoted = File(staging.promote().getValue("cover.jpg").removePrefix("file://"))
            assertEquals("replacement", promoted.readText())
            // Simulate failure before DB transaction reaches retainForCommit().
        }
        assertFalse(promoted.exists())
        assertEquals("original", live.readText())
    }

    @Test fun `committed artwork survives cleanup and repeated restores use unique paths`() = scenario { cache, art ->
        fun restore(text: String): File = RestoreArtworkStaging(cache, art).use { staging ->
            staging.stage("cover.jpg", text.byteInputStream(), 100)
            val file = File(staging.promote().getValue("cover.jpg").removePrefix("file://"))
            staging.retainForCommit()
            file
        }
        val first = restore("first")
        val second = restore("second")
        assertNotEquals(first, second)
        assertEquals("first", first.readText())
        assertEquals("second", second.readText())
        assertTrue(cache.listFiles().isNullOrEmpty())
    }

    @Test fun `oversized entry and duplicate entries fail without touching live files`() = scenario { cache, art ->
        RestoreArtworkStaging(cache, art).use { staging ->
            assertThrows(SecurityException::class.java) {
                staging.stage("big.jpg", "too large".byteInputStream(), 1)
            }
        }
        RestoreArtworkStaging(cache, art).use { staging ->
            staging.stage("cover.jpg", "first".byteInputStream(), 100)
            assertThrows(java.io.IOException::class.java) {
                staging.stage("cover.jpg", "duplicate".byteInputStream(), 100)
            }
        }
        assertFalse(art.exists())
        assertTrue(cache.listFiles().isNullOrEmpty())
    }
}
