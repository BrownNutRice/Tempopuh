package me.avinas.tempo.data.stats

/**
 * Session statistics represent observed *listening* time, not elapsed wall time
 * between the first and last record. Pauses and idle gaps must not create minutes.
 *
 * Events are already sorted by timestamp at the DAO boundary. The exact end time
 * is preferable to start + playDuration, since seeks/pauses change that relation.
 */
internal object ListeningSessionAggregator {
    private const val MAX_SESSION_GAP_MS = 20 * 60_000L

    data class Summary(
        val count: Int = 0,
        val averageListeningMs: Long = 0L,
        val longestListeningMs: Long = 0L,
    )

    fun summarize(events: List<SessionPoint>): Summary {
        if (events.isEmpty()) return Summary()
        var sessionCount = 0
        var longest = 0L
        var sessionListening = 0L
        var totalListening = 0L
        var previousEndMs: Long? = null

        for (event in events) {
            val listenedMs = event.playDuration.coerceAtLeast(0L)
            val endMs = event.endTimestamp
                ?.takeIf { it >= event.timestamp }
                ?: (event.timestamp + listenedMs)

            if (previousEndMs == null || event.timestamp - previousEndMs > MAX_SESSION_GAP_MS) {
                if (previousEndMs != null) {
                    longest = maxOf(longest, sessionListening)
                }
                sessionCount++
                sessionListening = 0L
            }

            sessionListening += listenedMs
            totalListening += listenedMs
            // Overlapping sessions must never move the boundary backwards.
            previousEndMs = maxOf(previousEndMs ?: endMs, endMs)
        }
        longest = maxOf(longest, sessionListening)
        return Summary(
            count = sessionCount,
            averageListeningMs = totalListening / sessionCount,
            longestListeningMs = longest,
        )
    }
}
