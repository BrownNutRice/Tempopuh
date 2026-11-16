package me.avinas.tempo.data.spotify

/**
 * Cursor decision for Spotify's timestamp-based recently-played endpoint.
 * The API's limit is 50 and before/after cannot be set simultaneously.
 * Avoid infinite loops when Spotify only exposes the same latest 50 records.
 */
internal object SpotifyRecentHistoryPagingPolicy {
    const val PAGE_LIMIT = 50
    const val MAX_PAGES = 6

    data class Decision(
        val nextBefore: Long? = null,
        val complete: Boolean = false,
        val incomplete: Boolean = false,
    )

    fun afterPage(
        count: Int,
        oldestValidPlayedAt: Long,
        requestedBefore: Long?,
        lowerBound: Long?,
        pagesFetched: Int,
    ): Decision {
        if (count == 0 || oldestValidPlayedAt <= 0L) return Decision(complete = count == 0,
            incomplete = count > 0)
        if (lowerBound != null && oldestValidPlayedAt <= lowerBound) return Decision(complete = true)
        if (count < PAGE_LIMIT) return Decision(complete = true)
        if (requestedBefore != null && oldestValidPlayedAt >= requestedBefore)
            return Decision(incomplete = true)
        if (pagesFetched >= MAX_PAGES) return Decision(incomplete = true)
        return Decision(nextBefore = oldestValidPlayedAt)
    }
}
