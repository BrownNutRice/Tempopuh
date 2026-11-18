package me.avinas.tempo.ui.stats

import me.avinas.tempo.data.stats.TopAlbum
import org.junit.Assert.assertEquals
import org.junit.Test

class StatsAlbumNavigationTest {
    @Test fun albumAndArtistReachLookupAsSeparateUnmodifiedFields() {
        val albums = listOf(
            "Alpha | Beta" to "Artist",
            "नमस्ते" to "A | B",
            "Same Name" to "Artist A",
            "Same Name" to "Artist B",
            "" to "",
            "Album" to "Artist",
        )
        val opened = mutableListOf<Pair<String, String>>()
        for ((title, artist) in albums) {
            val item = TopAlbum(
                album = title,
                artist = artist,
                albumArtUrl = null,
                playCount = 2,
                totalTimeMs = 1000,
                uniqueTracks = 2,
            )
            resolveNavigation(
                item,
                onTrack = { error("Album was routed to a track") },
                onArtist = { error("Album was routed to an artist") },
                onAlbum = { selectedTitle, selectedArtist -> opened.add(selectedTitle to selectedArtist) },
            )
        }
        assertEquals(albums, opened)
    }
}
