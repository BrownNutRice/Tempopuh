package me.avinas.tempo.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NowPlayingArtworkChoiceTest {
    private val id = NowPlayingArtworkIdentity("com.spotify.music", "Song", "Artist")

    @Test fun `enriched artwork preferred and local file kept as fallback`() {
        val chosen = selectNowPlayingArtwork(id, "file:///data/cover.jpg", "https://example.org/cover.jpg")
        assertEquals("https://example.org/cover.jpg", chosen.remoteUrl)
        assertEquals("file:///data/cover.jpg", chosen.localUrl)
    }

    @Test fun `media content uri can be chosen independently from repository data`() {
        val chosen = selectNowPlayingArtwork(id, "content://album/art", null)
        assertNull(chosen.remoteUrl)
        assertEquals("content://album/art", chosen.localUrl)
    }

    @Test fun `stored remote cover is used when enrichment is absent`() {
        val chosen = selectNowPlayingArtwork(id, "https://cdn.example/cover.jpg", null)
        assertEquals("https://cdn.example/cover.jpg", chosen.remoteUrl)
        assertNull(chosen.localUrl)
    }

    @Test fun `identity remains attached to artwork to prevent old-cover flash`() {
        val chosen = selectNowPlayingArtwork(id, null, "https://cdn.example/art.jpg")
        assertEquals(id, chosen.identity)
    }
}
