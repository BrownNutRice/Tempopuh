package me.avinas.tempo.service

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

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
        val source: String = "Spotify broadcast"
    )

    private val mutablePresence = MutableStateFlow<Presence?>(null)
    val presence = mutablePresence.asStateFlow()

    fun enabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(ENABLED, enabled).apply()
        if (!enabled && mutablePresence.value?.source == "Spotify broadcast") {
            mutablePresence.value = null
        }
    }

    internal fun publish(track: SpotifyBroadcastBridge.Track, playing: Boolean, positionMs: Long) {
        mutablePresence.value = Presence(
            track.title, track.artist, track.album, playing,
            positionMs, track.durationMs, System.currentTimeMillis()
        )
    }

    /**
     * The native MediaSession and notification callback is the fallback when
     * broadcasts are disabled, delayed, or unavailable. Let recent broadcasts
     * win briefly so stale MediaSession snapshots cannot flicker the card.
     */
    internal fun publishFromLocal(
        packageName: String,
        title: String,
        artist: String,
        album: String?,
        playing: Boolean,
        positionMs: Long,
        durationMs: Long,
    ) {
        if (title.isBlank() || artist.isBlank()) return
        val current = mutablePresence.value
        val now = System.currentTimeMillis()
        if (packageName == "com.spotify.music" &&
            current?.source == "Spotify broadcast" &&
            now - current.updatedAtMs < 2_500L
        ) return
        mutablePresence.value = Presence(
            title = title,
            artist = artist,
            album = album,
            playing = playing,
            positionMs = positionMs.coerceAtLeast(0L),
            durationMs = durationMs.coerceAtLeast(0L),
            updatedAtMs = now,
            packageName = packageName,
            source = "Android media tracking"
        )
    }

    internal fun clearForPackage(packageName: String) {
        if (mutablePresence.value?.packageName == packageName) {
            mutablePresence.value = null
        }
    }

    internal fun clear() {
        if (mutablePresence.value?.source == "Spotify broadcast") {
            mutablePresence.value = null
        }
    }
}
