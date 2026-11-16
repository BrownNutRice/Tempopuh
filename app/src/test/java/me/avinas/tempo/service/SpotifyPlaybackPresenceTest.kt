package me.avinas.tempo.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Account-level Spotify snapshots must never overwrite fresh on-device playback. */
class SpotifyPlaybackPresenceTest {
    @Test
    fun apiConfirmationDoesNotOverwriteLocalPlayPauseOrPosition() {
        SpotifyBroadcastPresence.clearForPackage("com.spotify.music")
        SpotifyBroadcastPresence.publishFromLocal(
            packageName = "com.spotify.music",
            title = "First Song",
            artist = "An Artist",
            album = null,
            playing = true,
            positionMs = 24_000L,
            durationMs = 0L
        )
        SpotifyBroadcastPresence.publishFromApi(
            title = "First Song",
            artist = "An Artist",
            album = "A Record",
            playing = false, // stale API state must not pause local presence
            positionMs = 15_000L,
            durationMs = 220_000L,
            deviceName = "Laptop"
        )
        val status = SpotifyBroadcastPresence.presence.value
        assertNotNull(status)
        assertEquals("First Song", status?.title)
        assertEquals(true, status?.playing)
        assertEquals(24_000L, status?.positionMs)
        assertEquals(220_000L, status?.durationMs)
        assertNotNull(status?.verifiedByApiAtMs)
        SpotifyBroadcastPresence.clearForPackage("com.spotify.music")
    }

    @Test
    fun aConflictingApiTrackDoesNotOverrideRecentLocalMusic() {
        SpotifyBroadcastPresence.clearForPackage("com.spotify.music")
        SpotifyBroadcastPresence.publishFromLocal(
            packageName = "com.spotify.music",
            title = "Local Song",
            artist = "Local Artist",
            album = null,
            playing = true,
            positionMs = 14_000L,
            durationMs = 240_000L
        )
        SpotifyBroadcastPresence.publishFromApi(
            title = "Different Remote Song",
            artist = "Remote Artist",
            album = "Remote Album",
            playing = true,
            positionMs = 2_000L,
            durationMs = 100_000L,
            deviceName = "Office Speaker"
        )
        assertEquals("Local Song", SpotifyBroadcastPresence.presence.value?.title)
        SpotifyBroadcastPresence.clearForPackage("com.spotify.music")
    }

    @Test
    fun apiCanShowRemoteDeviceWhenNoLocalSessionExists() {
        SpotifyBroadcastPresence.clearForPackage("com.spotify.music")
        SpotifyBroadcastPresence.publishFromApi(
            title = "Remote Song",
            artist = "Remote Artist",
            album = null,
            playing = true,
            positionMs = 5_000L,
            durationMs = 200_000L,
            deviceName = "Desktop"
        )
        assertTrue(SpotifyBroadcastPresence.presence.value?.source?.contains("Desktop") == true)
        SpotifyBroadcastPresence.clearApiIfInactive()
        assertEquals(null, SpotifyBroadcastPresence.presence.value)
    }
}
