package me.avinas.tempo.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSessionLiveAccountingTest {
    @Test fun `position update does not consume the play pause transition`() {
        val session = MusicTrackingService.PlaybackSession(
            packageName = "com.spotify.music",
            title = "Song",
            artist = "Artist",
            album = null,
            isPlaying = true,
            lastRecordedPosition = 1_000L,
            hasReceivedInitialPosition = true,
            lastPositionUpdateTime = System.currentTimeMillis(),
        )
        session.updatePosition(2_000L, false)
        assertTrue("Transition is handled by pause(), not silently by updatePosition()", session.isPlaying)
        session.pause()
        assertFalse(session.isPlaying)
        assertEquals(1, session.pauseCount)

        session.updatePosition(2_000L, true)
        assertFalse("Transition is handled by resume()", session.isPlaying)
        session.resume()
        assertTrue(session.isPlaying)
    }

    @Test fun `notification only playback accumulates without a MediaSession position`() {
        val session = MusicTrackingService.PlaybackSession(
            packageName = "com.example.player",
            title = "Notification-only song",
            artist = "Artist",
            album = null,
            isPlaying = true,
        )
        // No MediaSession callbacks: playback position remains unknown.
        Thread.sleep(30)
        session.pause()
        assertTrue(
            "A playable notification must not save zero seconds solely because the app did not expose positions",
            session.calculateCurrentPlayDuration() > 0L,
        )
    }

    @Test fun `position based duration still preserves preexisting precise samples`() {
        val session = MusicTrackingService.PlaybackSession(
            packageName = "com.spotify.music",
            title = "Long Song",
            artist = "Artist",
            album = null,
            accumulatedPositionMs = 90_000L,
            isPlaying = false,
        )
        assertEquals(90_000L, session.calculateCurrentPlayDuration())
    }
}
