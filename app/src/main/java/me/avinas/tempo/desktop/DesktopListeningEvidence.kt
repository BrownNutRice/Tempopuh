package me.avinas.tempo.desktop

/** Distinguishes catalog length from observed playback for browser/legacy desktop clients. */
internal object DesktopListeningEvidence {
    private const val MIN_OBSERVED_PLAY_MS = 5_000L

    fun observedMs(reported: Boolean, durationMs: Long): Long? =
        durationMs.takeIf { reported && it >= 0L }

    fun shouldRecord(observedMs: Long?): Boolean =
        observedMs == null || observedMs >= MIN_OBSERVED_PLAY_MS

    fun wasSkipped(observedMs: Long?, catalogDurationMs: Long): Boolean =
        observedMs != null && catalogDurationMs > 0L &&
            observedMs.toDouble() / catalogDurationMs.toDouble() < 0.3
}
