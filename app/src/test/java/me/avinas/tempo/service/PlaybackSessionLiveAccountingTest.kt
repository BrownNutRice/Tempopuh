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

    @Test fun `pausing freezes monotonic listening time until resumed`() {
        val session = MusicTrackingService.PlaybackSession(
            packageName = "player", title = "Song", artist = "Artist",
            album = null, isPlaying = true,
        )
        Thread.sleep(25)
        session.pause()
        val pausedMs = session.calculateCurrentPlayDuration()
        Thread.sleep(25)
        assertEquals("Paused time cannot count as listening time", pausedMs,
            session.calculateCurrentPlayDuration())
        session.resume()
        Thread.sleep(25)
        session.pause()
        assertTrue(session.calculateCurrentPlayDuration() > pausedMs)
        assertEquals(2, session.pauseCount)
    }

    @Test fun `track coverage comes from position not elapsed listening time`() {
        val session = MusicTrackingService.PlaybackSession(
            packageName = "player", title = "Half-song", artist = "Artist",
            album = null, estimatedDurationMs = 100_000L, isPlaying = true,
            lastRecordedPosition = 0L, hasReceivedInitialPosition = true,
            lastPositionUpdateTime = System.currentTimeMillis() - 120_000L,
        )
        session.updatePosition(50_000L, true)
        assertEquals("Observed position covers 50% of this recording", 50,
            session.calculateCompletionPercent())
        assertTrue("Fifty seconds of media position must not fabricate fifty seconds of listening",
            session.calculateCurrentPlayDuration() < 10_000L)
        session.pause()
    }

    @Test fun `unknown completion remains unknown without position evidence`() {
        val session = MusicTrackingService.PlaybackSession(
            packageName = "player", title = "Unmeasured", artist = "Artist",
            album = null, estimatedDurationMs = 200_000L, isPlaying = true,
        )
        assertEquals(0, session.calculateCompletionPercent())
        session.pause()
    }
}
