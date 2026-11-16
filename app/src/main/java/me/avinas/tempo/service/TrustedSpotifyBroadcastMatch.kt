package me.avinas.tempo.service

/**
 * Android <= 13 has no trustworthy sender UID in BroadcastReceiver. Accept
 * external Spotify action strings as a playback hint only when they match an
 * independently obtained Spotify MediaSession from Android's session manager.
 * Any mismatch must fall back to MediaSession/notification tracking.
 */
internal object TrustedSpotifyBroadcastMatch {
    fun matches(
        sessionTitle: String?,
        sessionArtist: String?,
        broadcastTitle: String,
        broadcastArtist: String,
    ): Boolean {
        if (sessionTitle.isNullOrBlank() || sessionArtist.isNullOrBlank()) return false
        if (broadcastTitle.isBlank() || broadcastArtist.isBlank()) return false
        return sessionTitle.trim().equals(broadcastTitle.trim(), ignoreCase = true) &&
            sessionArtist.trim().equals(broadcastArtist.trim(), ignoreCase = true)
    }
}
