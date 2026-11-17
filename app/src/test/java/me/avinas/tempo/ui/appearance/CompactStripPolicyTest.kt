package me.avinas.tempo.ui.appearance

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompactStripPolicyTest {
    private val now = 1_000_000L

    @Test fun `compact strip shows recent playback without requiring a network sync`() {
        assertTrue(shouldShowCompactStrip(AppearanceNowListening.COMPACT, "Track", now - 2_000L, now))
        assertTrue(shouldShowCompactStrip(AppearanceNowListening.COMPACT, "Track", now - 14 * 60_000L, now))
    }

    @Test fun `stale empty and future snapshots never occupy navigation space`() {
        val mode = AppearanceNowListening.COMPACT
        assertFalse(shouldShowCompactStrip(mode, "Track", now - 15 * 60_000L, now))
        assertFalse(shouldShowCompactStrip(mode, "", now - 10_000L, now))
        assertFalse(shouldShowCompactStrip(mode, "Track", now + 1L, now))
        assertFalse(shouldShowCompactStrip(mode, "Track", 0L, now))
    }

    @Test fun `hidden and inline layouts do not show extra strip`() {
        for (mode in listOf(AppearanceNowListening.INLINE, AppearanceNowListening.HIDDEN)) {
            assertFalse(shouldShowCompactStrip(mode, "Track", now - 1_000L, now))
        }
    }
}
