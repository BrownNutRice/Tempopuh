package me.avinas.tempo.data.local

import kotlin.math.abs

/**
 * Millisecond jitter from a resent desktop payload is allowed, but independent
 * back-to-back replays must never be hidden by a five-minute fuzzy match.
 * Database reconciliation performs the check atomically with insertion.
 */
internal object DesktopObservedReplayPolicy {
    private const val DUPLICATE_TIMESTAMP_TOLERANCE_MS = 1_000L

    fun isSamePlay(firstTimestampMs: Long, secondTimestampMs: Long): Boolean =
        abs(firstTimestampMs - secondTimestampMs) <= DUPLICATE_TIMESTAMP_TOLERANCE_MS
}
