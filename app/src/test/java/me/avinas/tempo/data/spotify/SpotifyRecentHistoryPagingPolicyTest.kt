package me.avinas.tempo.data.spotify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotifyRecentHistoryPagingPolicyTest {
    private val policy = SpotifyRecentHistoryPagingPolicy

    @Test fun `full page moves backward rather than losing older plays`() {
        val decision = policy.afterPage(
            count = 50, oldestValidPlayedAt = 500_000L,
            requestedBefore = null, lowerBound = 100_000L, pagesFetched = 1,
        )
        assertEquals(500_000L, decision.nextBefore)
        assertFalse(decision.complete)
        assertFalse(decision.incomplete)
    }

    @Test fun `reaching prior sync overlap ends historical scan`() {
        val decision = policy.afterPage(
            count = 50, oldestValidPlayedAt = 99_999L,
            requestedBefore = 500_000L, lowerBound = 100_000L, pagesFetched = 2,
        )
        assertTrue(decision.complete)
    }

    @Test fun `short result means the accessible window is exhausted`() {
        assertTrue(policy.afterPage(12, 300_000L, null, null, 1).complete)
    }

    @Test fun `nonprogressing cursor fails safely without infinite requests`() {
        assertTrue(policy.afterPage(50, 500_000L, 500_000L, null, 2).incomplete)
    }

    @Test fun `bounded page count cannot silently advance cursor past missing history`() {
        assertTrue(policy.afterPage(50, 300_000L, 500_000L, 100L, 6).incomplete)
    }
}
