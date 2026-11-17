package me.avinas.tempo.service

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Shared, ephemeral playback presence from device broadcasts or Android media tracking.
 * The API sync is a separate source of completed historical plays; do not mistake it
 * for real-time playing status. Never store titles or send them to Discord.
 */
object SpotifyBroadcastPresence {
    private const val PREFS = "spotify_broadcast_tracking"
    private const val ENABLED = "enabled"

    data class Presence(
        val title: String,
        val artist: String,
        val album: String?,
        val playing: Boolean,
        val positionMs: Long,
        val durationMs: Long,
        val updatedAtMs: Long,
        val packageName: String = "com.spotify.music",
        val source: String = "Spotify broadcast",
        val verifiedByApiAtMs: Long? = null,
        // Live, *uncommitted* listening time for this local session. Null for
        // API-only/remote playback; never mix it into historical DB totals.
        val listenedMs: Long? = null,
        // MediaSession supplies this without a network request; URI may be transient.
        val albumArtUri: String? = null,
    )

    private val mutablePresence = MutableStateFlow<Presence?>(null)
    val presence = mutablePresence.asStateFlow()

    fun enabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(ENABLED, enabled).apply()
        if (!enabled) {
            mutablePresence.update { current ->
                current?.takeUnless { it.source == "Spotify broadcast" }
            }
        }
    }

    internal fun publish(track: SpotifyBroadcastBridge.Track, playing: Boolean, positionMs: Long) {
        mutablePresence.update { current ->
            val match = current != null && current.title == track.title &&
                current.artist == track.artist &&
                current.packageName == "com.spotify.music"
            Presence(
                track.title, track.artist, track.album, playing,
                positionMs, track.durationMs, System.currentTimeMillis(),
                verifiedByApiAtMs = if (match) current?.verifiedByApiAtMs else null,
                listenedMs = if (match) current?.listenedMs else null,
                albumArtUri = if (match) current?.albumArtUri else null,
            )
        }
    }

    /**
     * The native MediaSession and notification callback is the fallback when
     * broadcasts are disabled, delayed, or unavailable. Let recent broadcasts
     * win briefly so stale MediaSession snapshots cannot flicker the card.
     */
    /**
     * A Spotify Web API snapshot can verify local metadata or display remote
     * Spotify Connect playback. Never replace a recent local play/pause event
     * with a possibly delayed Web API response.
     */
    internal fun publishFromApi(
        title: String,
        artist: String,
        album: String?,
        playing: Boolean,
        positionMs: Long,
        durationMs: Long,
        deviceName: String?,
    ) {
        if (title.isBlank() || artist.isBlank()) return
        val now = System.currentTimeMillis()
        // Atomic compare-and-set: a delayed HTTP callback must not overwrite a
        // new Spotify broadcast that arrived while the API response parsed.
        mutablePresence.update { current ->
            val isLocal = current != null &&
                current.source != "Spotify Web API" &&
                !current.source.startsWith("Spotify Connect ·")
            val matchingLocal = isLocal && current != null &&
                current.packageName == "com.spotify.music" &&
                current.title.equals(title, ignoreCase = true) &&
                current.artist.contains(artist, ignoreCase = true)
            when {
                matchingLocal && current != null && now - current.updatedAtMs < 90_000L ->
                    current.copy(
                        album = current.album ?: album,
                        durationMs = current.durationMs.takeIf { it > 0L } ?: durationMs,
                        verifiedByApiAtMs = now,
                    )
                isLocal && current != null && now - current.updatedAtMs < 90_000L ->
                    current
                else -> Presence(
                    title = title,
                    artist = artist,
                    album = album,
                    playing = playing,
                    positionMs = positionMs.coerceAtLeast(0L),
                    durationMs = durationMs.coerceAtLeast(0L),
                    updatedAtMs = now,
                    source = deviceName?.takeIf { it.isNotBlank() }
                        ?.let { "Spotify Connect · $it" } ?: "Spotify Web API",
                )
            }
        }
    }

    internal fun clearApiIfInactive() {
        mutablePresence.update { current ->
            current?.takeUnless {
                it.source == "Spotify Web API" || it.source.startsWith("Spotify Connect ·")
            }
        }
    }

    internal fun publishFromLocal(
        packageName: String,
        title: String,
        artist: String,
        album: String?,
        playing: Boolean,
        positionMs: Long,
        durationMs: Long,
        listenedMs: Long? = null,
        albumArtUri: String? = null,
    ) {
        if (title.isBlank() || artist.isBlank()) return
        val now = System.currentTimeMillis()
        // Broadcast, MediaSession and notification callbacks may come from
        // different threads; preserve source priority and elapsed time atomically.
        mutablePresence.update { current ->
            val sameTrack = current?.packageName == packageName &&
                current.title == title && current.artist == artist
            if (packageName == "com.spotify.music" &&
                current?.source == "Spotify broadcast" &&
                now - current.updatedAtMs < 2_500L
            ) {
                if (sameTrack && listenedMs != null) {
                    current.copy(
                        listenedMs = listenedMs.coerceAtLeast(0L),
                        albumArtUri = albumArtUri ?: current.albumArtUri,
                    )
                } else current
            } else {
                Presence(
                    title = title,
                    artist = artist,
                    album = album,
                    playing = playing,
                    positionMs = positionMs.coerceAtLeast(0L),
                    durationMs = durationMs.coerceAtLeast(0L),
                    updatedAtMs = now,
                    packageName = packageName,
                    source = "Android media tracking",
                    verifiedByApiAtMs = if (sameTrack) current?.verifiedByApiAtMs else null,
                    listenedMs = listenedMs?.coerceAtLeast(0L)
                        ?: if (sameTrack) current?.listenedMs else null,
                    albumArtUri = albumArtUri ?: if (sameTrack) current?.albumArtUri else null,
                )
            }
        }
    }

    internal fun clearForStoppedTrackingService() {
        mutablePresence.update { current ->
            current?.takeUnless {
                it.source == "Android media tracking" || it.source == "Spotify broadcast"
            }
        }
    }

    internal fun clearLocalIfNoSession() {
        mutablePresence.update { current ->
            current?.takeUnless { it.source == "Android media tracking" }
        }
    }

    internal fun clearForPackage(packageName: String) {
        mutablePresence.update { current ->
            current?.takeUnless { it.packageName == packageName }
        }
    }

    internal fun clear() {
        mutablePresence.update { current ->
            current?.takeUnless { it.source == "Spotify broadcast" }
        }
    }

}
