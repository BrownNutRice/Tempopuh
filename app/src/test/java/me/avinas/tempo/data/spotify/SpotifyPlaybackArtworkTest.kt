package me.avinas.tempo.data.spotify

import me.avinas.tempo.data.remote.spotify.SpotifyImage
import me.avinas.tempo.data.remote.spotify.SpotifyPlaybackAlbum
import me.avinas.tempo.service.SpotifyBroadcastPresence
import org.junit.Assert.assertEquals
import org.junit.Test

class SpotifyPlaybackArtworkTest {
    @Test fun `Spotify snapshot chooses a thumbnail sized HTTPS album cover`() {
        val album = SpotifyPlaybackAlbum(
            name = "Album",
            images = listOf(
                SpotifyImage("https://cdn.example/large.jpg", 1000, 1000),
                SpotifyImage("https://cdn.example/medium.jpg", 300, 300),
                SpotifyImage("https://cdn.example/small.jpg", 64, 64),
            ),
        )
        assertEquals("https://cdn.example/medium.jpg", album.nowPlayingArtworkUrl)
    }

    @Test fun `remote Spotify Connect album art reaches the card presence`() {
        SpotifyBroadcastPresence.clearForPackage("com.spotify.music")
        SpotifyBroadcastPresence.publishFromApi(
            title = "Remote Test",
            artist = "Example",
            album = "Album",
            playing = true,
            positionMs = 5000L,
            durationMs = 200000L,
            deviceName = "Office Speaker",
            albumArtUri = "https://cdn.example/remote.jpg",
        )
        assertEquals(
            "https://cdn.example/remote.jpg",
            SpotifyBroadcastPresence.presence.value?.albumArtUri,
        )
        SpotifyBroadcastPresence.clearForPackage("com.spotify.music")
    }

    @Test fun `trusted local artwork overrides stale API cover`() {
        SpotifyBroadcastPresence.clearForPackage("com.spotify.music")
        SpotifyBroadcastPresence.publishFromApi(
            title = "Local Test",
            artist = "Artist",
            album = null,
            playing = true,
            positionMs = 100L,
            durationMs = 120000L,
            deviceName = null,
            albumArtUri = "https://cdn.example/remote.jpg",
        )
        SpotifyBroadcastPresence.publishFromLocal(
            packageName = "com.spotify.music",
            title = "Local Test",
            artist = "Artist",
            album = null,
            playing = true,
            positionMs = 200L,
            durationMs = 120000L,
            albumArtUri = "content://trusted-player/cover",
        )
        assertEquals(
            "content://trusted-player/cover",
            SpotifyBroadcastPresence.presence.value?.albumArtUri,
        )
        SpotifyBroadcastPresence.clearForPackage("com.spotify.music")
    }
}
