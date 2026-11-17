package me.avinas.tempo.data.local

import me.avinas.tempo.data.local.entities.ListeningEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class EventFingerprintSessionIdentityTest {
    private fun event(sessionId: String?) = ListeningEvent(
        track_id = 12L,
        timestamp = 1_700_000_000_000L,
        playDuration = 60_000L,
        completionPercentage = 50,
        source = "com.spotify.music",
        wasSkipped = false,
        isReplay = false,
        sessionId = sessionId,
    )

    @Test fun `separate observed sessions never share a fingerprint`() {
        assertNotEquals(
            EventFingerprint.compute(event("session-1")),
            EventFingerprint.compute(event("session-2")),
        )
    }

    @Test fun `same session is idempotent across process restart`() {
        assertEquals(
            EventFingerprint.compute(event("session-1")),
            EventFingerprint.compute(event("session-1").copy()),
        )
    }

    @Test fun `imported events preserve legacy fingerprint semantics`() {
        val imported = event(null)
        assertEquals(
            EventFingerprint.compute(
                imported.source, imported.track_id, imported.timestamp,
                imported.playDuration, imported.endTimestamp,
            ),
            EventFingerprint.compute(imported),
        )
    }
}
