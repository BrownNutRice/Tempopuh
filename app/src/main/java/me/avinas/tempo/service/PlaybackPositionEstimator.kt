package me.avinas.tempo.service

/**
 * Extrapolate a MediaSession playback position using Android's monotonic clock.
 *
 * PlaybackState.position is normally a snapshot taken at lastPositionUpdateTime,
 * not a continuously advancing counter. Reading only the snapshot every few
 * seconds can report zero listening time for an otherwise playing track.
 *
 * No extrapolation takes place for paused playback, invalid anchors or seeks.
 */
internal object PlaybackPositionEstimator {
    fun estimate(
        positionMs: Long,
        lastPositionUpdateElapsedMs: Long,
        observedElapsedMs: Long,
        speed: Float,
        playing: Boolean,
        durationMs: Long? = null,
    ): Long {
        if (positionMs < 0L) return -1L
        val elapsedMs = (observedElapsedMs - lastPositionUpdateElapsedMs)
            .coerceAtLeast(0L)
        val canExtrapolate = playing &&
            lastPositionUpdateElapsedMs > 0L &&
            observedElapsedMs >= lastPositionUpdateElapsedMs &&
            speed.isFinite() && speed > 0f
        val projected = if (canExtrapolate) {
            val advance = (elapsedMs.toDouble() * speed.toDouble())
                .coerceIn(0.0, Long.MAX_VALUE.toDouble()).toLong()
            if (positionMs > Long.MAX_VALUE - advance) Long.MAX_VALUE
            else positionMs + advance
        } else {
            positionMs
        }
        return durationMs?.takeIf { it > 0L }?.let { projected.coerceAtMost(it) }
            ?: projected
    }
}
