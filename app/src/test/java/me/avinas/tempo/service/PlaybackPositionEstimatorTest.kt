package me.avinas.tempo.service

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackPositionEstimatorTest {
    @Test fun `playing position advances from Android's elapsed realtime anchor`() {
        assertEquals(
            17_000L,
            PlaybackPositionEstimator.estimate(
                positionMs = 15_000L,
                lastPositionUpdateElapsedMs = 100_000L,
                observedElapsedMs = 102_000L,
                speed = 1f,
                playing = true,
            )
        )
    }

    @Test fun `paused position never advances on its own`() {
        assertEquals(
            15_000L,
            PlaybackPositionEstimator.estimate(15_000L, 100_000L, 120_000L, 1f, false)
        )
    }

    @Test fun `playback speed affects elapsed progress`() {
        assertEquals(
            19_000L,
            PlaybackPositionEstimator.estimate(15_000L, 100_000L, 102_000L, 2f, true)
        )
    }

    @Test fun `position cannot exceed known catalog duration`() {
        assertEquals(
            180_000L,
            PlaybackPositionEstimator.estimate(175_000L, 1_000L, 20_000L, 1f, true, 180_000L)
        )
    }

    @Test fun `stale or invalid anchor does not invent listening progress`() {
        assertEquals(
            45_000L,
            PlaybackPositionEstimator.estimate(45_000L, 0L, 10_000L, 1f, true)
        )
        assertEquals(
            45_000L,
            PlaybackPositionEstimator.estimate(45_000L, 20_000L, 10_000L, 1f, true)
        )
        assertEquals(
            45_000L,
            PlaybackPositionEstimator.estimate(45_000L, 10_000L, 15_000L, -1f, true)
        )
        assertEquals(
            -1L,
            PlaybackPositionEstimator.estimate(-1L, 10_000L, 15_000L, 1f, true)
        )
    }
}
