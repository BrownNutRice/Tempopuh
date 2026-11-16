package me.avinas.tempo.data.spotify

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Spotify likes and rankings are not proof of a play at a particular time.
 * Imported statistics must not manufacture playback events from these hints.
 */
class SmartListeningEventGeneratorAccuracyTest {
    @Test
    fun `default generation does not invent plays from likes or yearly playlists`() {
        val entries = listOf(
            SmartListeningEventGenerator.TrackWithAffinity(
                trackId = 1L,
                durationMs = 210_000L,
                affinity = 1f,
                addedTimestamp = 1_700_000_000_000L,
            ),
            SmartListeningEventGenerator.TrackWithAffinity(
                trackId = 2L,
                durationMs = 220_000L,
                affinity = 0.7f,
                yearContext = 2024,
            ),
            SmartListeningEventGenerator.TrackWithAffinity(
                trackId = 3L,
                durationMs = 200_000L,
                affinity = 0.8f,
                affinityRank = 1,
                timeRangeHint = SmartListeningEventGenerator.TimeRangeHint.SHORT_TERM,
            )
        )
        val config = SmartListeningEventGenerator.GenerationConfig(
            startTime = 1_650_000_000_000L,
            endTime = 1_900_000_000_000L,
            totalEventsTarget = 20,
        )
        assertTrue(SmartListeningEventGenerator.generateEvents(entries, config).isEmpty())
    }
}
