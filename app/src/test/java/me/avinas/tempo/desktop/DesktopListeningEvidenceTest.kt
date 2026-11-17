package me.avinas.tempo.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopListeningEvidenceTest {
    @Test fun `catalog duration without listened field does not invent minutes`() {
        val observed = DesktopListeningEvidence.observedMs(false, 180_000L)
        assertNull(observed)
        assertTrue(DesktopListeningEvidence.shouldRecord(observed))
        assertFalse(DesktopListeningEvidence.wasSkipped(observed, 180_000L))
        assertEquals(0L, observed ?: 0L)
    }

    @Test fun `explicit zero seconds cannot become full track duration`() {
        val observed = DesktopListeningEvidence.observedMs(true, 0L)
        assertEquals(0L, observed)
        assertFalse(DesktopListeningEvidence.shouldRecord(observed))
    }

    @Test fun `real five-second listen is not replaced by catalog minutes`() {
        val observed = DesktopListeningEvidence.observedMs(true, 5_000L)
        assertEquals(5_000L, observed)
        assertTrue(DesktopListeningEvidence.shouldRecord(observed))
        assertTrue(DesktopListeningEvidence.wasSkipped(observed, 180_000L))
    }
}
