package me.avinas.tempo.data.local

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopObservedReplayPolicyTest {
    @Test fun `resending a play with identical timestamp is deduplicated`() {
        assertTrue(DesktopObservedReplayPolicy.isSamePlay(100_000L, 100_000L))
        assertTrue(DesktopObservedReplayPolicy.isSamePlay(100_000L, 100_500L))
    }

    @Test fun `back-to-back replay is not erased by five minute window`() {
        assertFalse(DesktopObservedReplayPolicy.isSamePlay(100_000L, 104_000L))
        assertFalse(DesktopObservedReplayPolicy.isSamePlay(100_000L, 280_000L))
    }
}
