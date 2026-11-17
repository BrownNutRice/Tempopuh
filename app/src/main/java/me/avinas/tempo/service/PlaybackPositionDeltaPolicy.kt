package me.avinas.tempo.service

/** A media-position delta scales with player speed; wall-clock listening time does not. */
internal object PlaybackPositionDeltaPolicy {
    fun maximumExpectedProgressMs(elapsedWallClockMs: Long, playbackSpeed: Float): Long {
        val speed = if (playbackSpeed.isFinite() && playbackSpeed > 0f) {
            playbackSpeed.coerceAtMost(8f)
        } else 1f
        return (elapsedWallClockMs.coerceAtLeast(0L).toDouble() * speed.toDouble() + 2_000.0).toLong()
    }
}
