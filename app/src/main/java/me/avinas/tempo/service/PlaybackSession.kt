package me.avinas.tempo.service

import android.util.Log

private const val MAX_POSITION_JUMP_MS = 30_000L
// Position updates can arrive late after device sleep or service suspension.
private const val MAX_REASONABLE_DELTA_MS = 5 * 60 * 1000L

/**
 * Represents an active playback session with POSITION-BASED tracking.
 *
 * KEY CHANGE: We now track actual playback position from MediaSession
 * instead of wall-clock time. This fixes the issue where:
 * - Play at 9:00 AM, pause after 1 minute
 * - Resume at 10:00 AM
 * - Old logic: would calculate 61 minutes played (wrong!)
 * - New logic: tracks actual position progress = 1 minute + continued progress (correct!)
 */
internal data class PlaybackSession(
    val packageName: String,
    var title: String,
    var artist: String,
    var album: String?,
    var trackId: Long? = null,
    var startTimestamp: Long = System.currentTimeMillis(),
    // POSITION-BASED TRACKING: accumulated actual playback time from MediaSession positions
    var accumulatedPositionMs: Long = 0,
    // Last position we recorded (to calculate delta on next update)
    var lastRecordedPosition: Long = 0,
    // Timestamp when we last recorded position (to detect stale data)
    var lastPositionUpdateTime: Long = System.currentTimeMillis(),
    var isPlaying: Boolean = false,
    var estimatedDurationMs: Long? = null,
    // Enhanced tracking fields
    var pauseCount: Int = 0,
    var lastPauseTimestamp: Long? = null,
    // NEW: Track total pause duration for analytics
    var totalPauseDurationMs: Long = 0,
    // NEW: Track seek operations
    var seekCount: Int = 0,
    var lastSeekTimestamp: Long? = null,
    // NEW: Track position update count for validation
    var positionUpdatesCount: Int = 0,
    // Session tracking
    val sessionId: String =
        java.util.UUID
            .randomUUID()
            .toString()
            .take(8),
    var playbackStartPosition: Long = 0, // Position when this session started
    var lastKnownPosition: Long = 0, // Last known playback position from MediaSession
    // Delayed metadata retry tracking
    var delayedMetadataRetryScheduled: Boolean = false,
    // Track if this session looks like music (for filtering)
    var isLikelyMusic: Boolean = true,
    // NEW: Flag for interrupted sessions (app kill, crash, etc.)
    var wasInterrupted: Boolean = false,
    // FIX: Flag to track if we've received the first valid position update
    // This prevents the initial position from being counted as played time
    var hasReceivedInitialPosition: Boolean = false,
) {
    @Synchronized
    fun recoverySnapshot() = SessionState(
        sessionId = sessionId,
        packageName = packageName,
        trackId = trackId,
        trackTitle = title,
        trackArtist = artist,
        trackAlbum = album,
        startTimestamp = startTimestamp,
        lastResumeTimestamp = lastPositionUpdateTime,
        totalPlayedMs = calculateCurrentPlayDuration(),
        isPlaying = isPlaying,
        pauseCount = pauseCount,
        estimatedDurationMs = estimatedDurationMs,
    )

    /**
     * Ensures a session produces at most one listening event.
     * Claimed atomically in saveListeningEventSuspend before queueing.
     */
    val eventSaveClaimed =
        java.util.concurrent.atomic
            .AtomicBoolean(false)

    // PlaybackState.position can remain frozen for the whole song, and some
    // notification-only players never expose it. Keep a monotonic stopwatch
    // as a fallback, excluding observed pauses. This is *not* added to the
    // position-based total, so mixed sources cannot double-count playback.
    // Recovered or explicitly supplied playtime; do not treat later media
    // position advances as wall-clock minutes (seeks and 2x speed differ).
    private val recoveredListeningMs: Long = accumulatedPositionMs.coerceAtLeast(0L)
    private var wallClockPlayedMs: Long = 0L

    // elapsedRealtime includes Android device suspend. An audio-offloaded
    // player may continue playing while the CPU sleeps; System.nanoTime
    // does NOT necessarily cover that interval and would undercount music.
    // Framework stubs in plain JVM tests return zero, so use a JVM clock
    // fallback only when Android's elapsed-time clock is unavailable.
    private fun playbackClockMs(): Long {
        val androidMs = try {
            android.os.SystemClock.elapsedRealtime()
        } catch (_: RuntimeException) {
            0L
        }
        return if (androidMs > 0L) androidMs else System.nanoTime() / 1_000_000L
    }

    private var lastResumeMonotonicMs: Long = playbackClockMs()
    private var observedPositionProgress: Boolean = false

    /**
     * Actual listening time measured with a monotonic clock while the
     * player reports PLAYING, excluding pauses. Media-position deltas are
     * used separately for *track coverage/completion*, not for minutes.
     * This stays correct for seeks and 0.5x/2x playback speed.
     */
    @Synchronized fun calculateCurrentPlayDuration(): Long {
        val elapsedSinceResume = if (isPlaying) {
            ((playbackClockMs()) - lastResumeMonotonicMs)
                .coerceAtLeast(0L)
        } else 0L
        return (recoveredListeningMs + wallClockPlayedMs + elapsedSinceResume)
            .coerceAtLeast(0L)
    }

    /** Applies a MediaSession position update; `newPosition` is in ms. */
    @Synchronized fun updatePosition(
        newPosition: Long,
        isCurrentlyPlaying: Boolean,
        playbackSpeed: Float = 1f,
    ) {
        val now = System.currentTimeMillis()
        positionUpdatesCount++

        // Validate position is reasonable
        // -1 is commonly sent during pause/stop - ignore silently (no warning needed)
        if (newPosition < 0) {
            return
        }

        // On the first position update, establish the baseline without accumulating:
        // starting a song at 1:30 must not count that 1:30 as played time.
        if (!hasReceivedInitialPosition) {
            lastRecordedPosition = newPosition
            lastKnownPosition = newPosition
            lastPositionUpdateTime = now
            // First baseline is not a state transition: the caller must
            // still run pause()/resume() and close the monotonic timer.
            hasReceivedInitialPosition = true
            Log.d("PlaybackSession", "Established initial position baseline: ${newPosition}ms for '$title'")
            return
        }

        // Check if position exceeds estimated duration by too much (likely error)
        estimatedDurationMs?.let { estimated ->
            if (newPosition > estimated * 1.5) {
                Log.w("PlaybackSession", "Position $newPosition exceeds duration $estimated by 50%, capping")
                // Continue but cap the accumulation
            }
        }

        // Final pause callbacks still carry the position played *before* pause.
        // Resuming must not count seek distance or a previously paused interval.
        if (isPlaying && newPosition > lastRecordedPosition) {
            // Position advanced - accumulate the delta
            val delta = newPosition - lastRecordedPosition

            // Enhanced sanity check for seek detection
            val timeElapsed = now - lastPositionUpdateTime
            val maxReasonableDelta = PlaybackPositionDeltaPolicy.maximumExpectedProgressMs(
                timeElapsed, playbackSpeed
            )

            // Detect different types of position changes
            if (delta <= maxReasonableDelta) {
                // Normal playback progression - but cap individual deltas as sanity check
                val safeDelta = delta.coerceAtMost(MAX_REASONABLE_DELTA_MS)
                if (delta > MAX_REASONABLE_DELTA_MS) {
                    Log.w("PlaybackSession", "ANOMALY: Large delta ${delta}ms capped to ${safeDelta}ms for '$title'")
                }
                accumulatedPositionMs += safeDelta
                if (safeDelta > 0L) observedPositionProgress = true

                // Cap total accumulation to prevent runaway values. Use the same
                // 3x bound as the save-time sanity cap (insertListeningEvent) so the
                // two limits agree. The old 1.5x cap truncated looped playback early:
                // a track that loops without triggering replay-splitting was saved at
                // 1.5x instead of its true (up to 3x) play time, under-counting
                // heavily-repeated listens. 3x still bounds runaway accumulation but
                // matches what the save path would permit anyway.
                estimatedDurationMs?.let { estimated ->
                    val maxAccumulation = estimated * 3
                    if (accumulatedPositionMs > maxAccumulation && estimated > 60_000) { // Only for >1min tracks
                        Log.w(
                            "PlaybackSession",
                            "ANOMALY: Capping accumulated ${accumulatedPositionMs}ms to $maxAccumulation (3x duration) for '$title'",
                        )
                        accumulatedPositionMs = maxAccumulation
                    }
                }
            } else if (delta < MAX_POSITION_JUMP_MS) {
                // Small seek forward (still count as seek, not playback)
                seekCount++
                lastSeekTimestamp = now
                Log.d("PlaybackSession", "Forward seek detected: +${delta}ms for '$title'")
                // Don't accumulate seek distance
            } else {
                // Large position jump - likely app error or major seek
                Log.w(
                    "PlaybackSession",
                    "ANOMALY: Large position jump: $lastRecordedPosition -> $newPosition (delta: ${delta}ms, time: ${timeElapsed}ms)",
                )
                // Don't accumulate to avoid inflating play time
            }
        } else if (newPosition < lastRecordedPosition) {
            // Position went backward - either seek backward or restart
            val delta = lastRecordedPosition - newPosition

            if (newPosition < 5000) {
                // Reset to beginning - track restarted or looped
                // Use verbose logging since multiple restarts in quick succession are common
                Log.v("PlaybackSession", "Track restarted/looped for '$title'")

                // CRITICAL FIX: Do NOT reset accumulatedPositionMs here.
                // If the track finished and restarted, we want to KEEP the accumulated time
                // so that when the session eventually ends (track change), we save the FULL duration.
                // For example, if a 3min song plays twice on loop, we should record 6mins.

                // We simply act as if nothing happened to the accumulation.
                // The lastRecordedPosition update below handles the new baseline.
            } else if (delta > 100) {
                // Seek backward - only count/log if delta > 100ms (ignore jitter)
                seekCount++
                lastSeekTimestamp = now
                Log.d("PlaybackSession", "Backward seek detected: -${delta}ms for '$title'")
            }
            // Tiny backward movements (< 100ms) are position jitter, ignore silently
        }

        lastRecordedPosition = newPosition
        lastKnownPosition = newPosition
        lastPositionUpdateTime = now
        // Do not change isPlaying here. Callers must invoke resume()/pause()
        // so pause counts, pause duration, and state transitions are retained.
    }

    /**
     * Pause playback and track pause count.
     */
    @Synchronized fun pause() {
        if (isPlaying) {
            wallClockPlayedMs += ((playbackClockMs()) -
                lastResumeMonotonicMs).coerceAtLeast(0L)
            isPlaying = false
            pauseCount++
            lastPauseTimestamp = System.currentTimeMillis()
            Log.d("PlaybackSession", "Paused '$title' (pause #$pauseCount)")
        }
    }

    /**
     * Resume playback - just update state, position tracking handles the rest.
     */
    @Synchronized fun resume() {
        if (!isPlaying) {
            val now = System.currentTimeMillis()

            // Calculate pause duration
            lastPauseTimestamp?.let { pauseStart ->
                val pauseDuration = now - pauseStart
                totalPauseDurationMs += pauseDuration
                Log.d("PlaybackSession", "Resumed '$title' after ${pauseDuration}ms pause (total pause: ${totalPauseDurationMs}ms)")
            }

            isPlaying = true
            lastResumeMonotonicMs = playbackClockMs()
            // Reset position update time so we don't accumulate the pause gap
            lastPositionUpdateTime = now
            lastPauseTimestamp = null
        }
    }

    /**
     * Check if this was a quick skip (< 30 seconds or < 30% completion).
     */
    @Synchronized fun wasSkipped(): Boolean {
        if (calculateCurrentPlayDuration() < 30_000L) return true
        val duration = estimatedDurationMs?.takeIf { it > 0L } ?: return false
        // Only claim a percentage-based skip if we observed actual media
        // progression. Otherwise playback length cannot establish coverage.
        return observedPositionProgress &&
            accumulatedPositionMs.toDouble() / duration.toDouble() < 0.3
    }

    /** Track completion, NOT total elapsed listening time. */
    @Synchronized fun calculateCompletionPercent(): Int {
        val duration = estimatedDurationMs?.takeIf { it > 0L } ?: return 0
        if (!observedPositionProgress) return 0 // unavailable, not zero coverage
        return ((accumulatedPositionMs.toDouble() / duration) * 100.0)
            .toInt().coerceIn(0, 100)
    }

    /**
     * Validate that play duration is reasonable.
     * Returns true if valid, false if suspicious.
     */
    fun validatePlayDuration(): Boolean {
        val duration = calculateCurrentPlayDuration()

        // Check if duration exceeds estimated duration by unreasonable amount
        estimatedDurationMs?.let { estimated ->
            if (estimated > 0 && duration > estimated * 3) {
                Log.w("PlaybackSession", "Validation failed: duration ${duration}ms is 3x estimated ${estimated}ms for '$title'")
                return false
            }
        }

        // Check for minimum duration (at least 1 second of playback)
        if (duration < 1000 && positionUpdatesCount > 5) {
            Log.w("PlaybackSession", "Validation warning: only ${duration}ms recorded despite $positionUpdatesCount updates")
            // Still return true - might be very short track or quick skip
        }

        return true
    }

    /**
     * Validate completion percentage is accurate.
     */
    fun validateCompletionPercentage(): Boolean {
        val percent = calculateCompletionPercent()

        // Completion should be 0-100
        if (percent < 0 || percent > 100) {
            Log.w("PlaybackSession", "Invalid completion percentage: $percent for '$title'")
            return false
        }

        return true
    }

    /**
     * Detect and report anomalies in the session data.
     * Returns a list of anomaly descriptions.
     */
    fun detectAnomalies(): List<String> {
        val anomalies = mutableListOf<String>()

        val duration = calculateCurrentPlayDuration()

        // Anomaly 1: Very high pause count relative to duration
        if (pauseCount > 0 && duration > 0) {
            val avgTimeBetweenPauses = duration / pauseCount
            if (avgTimeBetweenPauses < 5000) { // Pause every 5 seconds
                anomalies.add("High pause frequency: $pauseCount pauses in ${duration}ms")
            }
        }

        // Anomaly 2: Pause duration exceeds play duration significantly
        if (totalPauseDurationMs > duration * 5) {
            anomalies.add("Pause time (${totalPauseDurationMs}ms) >> play time (${duration}ms)")
        }

        // Anomaly 3: Many seeks in short time
        if (seekCount > 10 && duration < 60000) {
            anomalies.add("Excessive seeking: $seekCount seeks in ${duration}ms")
        }

        // Anomaly 4: Very few position updates for long duration
        if (duration > 60000 && positionUpdatesCount < 10) {
            anomalies.add("Few position updates: $positionUpdatesCount updates for ${duration}ms")
        }

        // Anomaly 5: Duration far exceeds estimated
        estimatedDurationMs?.let { estimated ->
            if (estimated > 0 && duration > estimated * 2) {
                anomalies.add("Duration ${duration}ms is 2x+ estimated ${estimated}ms")
            }
        }

        return anomalies
    }
}
