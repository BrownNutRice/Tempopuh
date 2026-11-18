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

    fun summarize(events: List<SessionPoint>): Summary =
        Accumulator().apply { addPage(events) }.summary()

    class Accumulator {
        private var sessionCount = 0
        private var longest = 0L
        private var sessionListening = 0L
        private var totalListening = 0L
        private var previousEndMs: Long? = null

        fun addPage(events: List<SessionPoint>) {
            for (event in events) {
                val listenedMs = event.playDuration.coerceAtLeast(0L)
                val endMs = event.endTimestamp
                    ?.takeIf { it >= event.timestamp }
                    ?: (event.timestamp + listenedMs)
                val previousEnd = previousEndMs

                if (previousEnd == null || event.timestamp - previousEnd > MAX_SESSION_GAP_MS) {
                    longest = maxOf(longest, sessionListening)
                    sessionCount++
                    sessionListening = 0L
                }

                sessionListening += listenedMs
                totalListening += listenedMs
                // Overlapping plays must never move the boundary backwards.
                previousEndMs = maxOf(previousEnd ?: endMs, endMs)
            }
        }

        fun summary(): Summary = if (sessionCount == 0) Summary() else Summary(
            count = sessionCount,
            averageListeningMs = totalListening / sessionCount,
            longestListeningMs = maxOf(longest, sessionListening),
        )
    }
}
