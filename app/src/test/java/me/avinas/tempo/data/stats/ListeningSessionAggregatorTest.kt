package me.avinas.tempo.data.stats

import org.junit.Assert.assertEquals
import org.junit.Test

class ListeningSessionAggregatorTest {
    @Test fun `empty history has no sessions`() {
        assertEquals(ListeningSessionAggregator.Summary(), ListeningSessionAggregator.summarize(emptyList()))
    }

    @Test fun `consecutive songs are one active-listening session`() {
        val items = listOf(
            SessionPoint(timestamp = 0L, playDuration = 120_000L, endTimestamp = 120_000L),
            SessionPoint(timestamp = 180_000L, playDuration = 90_000L, endTimestamp = 270_000L),
        )
        val result = ListeningSessionAggregator.summarize(items)
        assertEquals(1, result.count)
        assertEquals(210_000L, result.longestListeningMs)
        assertEquals(210_000L, result.averageListeningMs)
    }

    @Test fun `idle gaps split sessions and never count as music`() {
        val items = listOf(
            SessionPoint(0L, 60_000L, 60_000L),
            SessionPoint(90_000L, 40_000L, 130_000L),
            SessionPoint(3_600_000L, 30_000L, 3_630_000L),
        )
        val result = ListeningSessionAggregator.summarize(items)
        assertEquals(2, result.count)
        assertEquals(100_000L, result.longestListeningMs)
        assertEquals(65_000L, result.averageListeningMs)
    }

    @Test fun `real end timestamp defines the gap rather than duration math`() {
        val items = listOf(
            // 1 min of listening stretched over 4 min with pauses
            SessionPoint(timestamp = 0L, playDuration = 60_000L, endTimestamp = 240_000L),
            SessionPoint(timestamp = 1_300_000L, playDuration = 30_000L, endTimestamp = 1_330_000L),
        )
        val result = ListeningSessionAggregator.summarize(items)
        assertEquals(1, result.count) // actual gap 17m40s, not 20m40s
        assertEquals(90_000L, result.longestListeningMs)
    }

    @Test fun `overlapping events never move the previous end backwards`() {
        val items = listOf(
            SessionPoint(timestamp = 0L, playDuration = 100_000L, endTimestamp = 240_000L),
            SessionPoint(timestamp = 20_000L, playDuration = 10_000L, endTimestamp = 30_000L),
            SessionPoint(timestamp = 1_350_000L, playDuration = 30_000L, endTimestamp = 1_380_000L),
        )
        val result = ListeningSessionAggregator.summarize(items)
        assertEquals(1, result.count) // 18m30s from first real end
        assertEquals(140_000L, result.longestListeningMs)
    }
}
