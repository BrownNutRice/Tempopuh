package me.avinas.tempo.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrustedSpotifyBroadcastMatchTest {
    @Test fun `matching genuine session accepts benign case and whitespace changes`() {
        assertTrue(TrustedSpotifyBroadcastMatch.matches(" Song ", "Artist", "song", " ARTIST "))
    }

    @Test fun `untrusted track name cannot create a ghost scrobble`() {
        assertFalse(TrustedSpotifyBroadcastMatch.matches("Trusted", "Artist", "Forged", "Artist"))
    }

    @Test fun `different artist on same song is not a verified session`() {
        assertFalse(TrustedSpotifyBroadcastMatch.matches("Song", "Real Artist", "Song", "Imposter"))
    }

    @Test fun `missing media session metadata rejects a spoofed broadcast`() {
        assertFalse(TrustedSpotifyBroadcastMatch.matches(null, null, "Song", "Artist"))
        assertFalse(TrustedSpotifyBroadcastMatch.matches("", "Artist", "Song", "Artist"))
    }
}
