package me.avinas.tempo.service

import me.avinas.tempo.data.local.entities.ListeningEvent
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class PendingEventIdentityTest {
    private fun pending(sessionId: String) = PendingEvent(
        event = ListeningEvent(
            track_id = 1L,
            timestamp = 1_700_000_000_000L,
            playDuration = 60_000L,
            completionPercentage = 60,
            source = "com.spotify.music",
            wasSkipped = false,
            isReplay = false,
            sessionId = sessionId,
        ),
        sessionId = sessionId,
        queuedAt = 0L,
        retryCount = 0,
    )

    @Test fun `different sessions do not overwrite one another in offline queue`() {
        assertNotEquals(pending("session-a").key(), pending("session-b").key())
    }

    @Test fun `same session retains identity after retry`() {
        assertEquals(pending("session-a").key(), pending("session-a").copy(retryCount = 7).key())
    }
}
