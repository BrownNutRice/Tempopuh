package me.avinas.tempo.service

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackPositionDeltaPolicyTest {
    @Test fun `normal rate includes jitter tolerance`() {
        assertEquals(12_000L, PlaybackPositionDeltaPolicy.maximumExpectedProgressMs(10_000L, 1f))
    }

    @Test fun `double speed is not mistaken for a seek`() {
        assertEquals(22_000L, PlaybackPositionDeltaPolicy.maximumExpectedProgressMs(10_000L, 2f))
    }

    @Test fun `half speed allows proportionate media progress`() {
        assertEquals(7_000L, PlaybackPositionDeltaPolicy.maximumExpectedProgressMs(10_000L, 0.5f))
    }

    @Test fun `invalid playback speed falls back safely`() {
        assertEquals(12_000L, PlaybackPositionDeltaPolicy.maximumExpectedProgressMs(10_000L, Float.NaN))
    }
}
