package me.avinas.tempo.service

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Local Spotify broadcast mode and ephemeral presence for the settings screen.
 * No song titles are stored in preferences and no data is sent to Discord.
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
        val updatedAtMs: Long
    )

    private val mutablePresence = MutableStateFlow<Presence?>(null)
    val presence = mutablePresence.asStateFlow()

    fun enabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(ENABLED, enabled).apply()
        if (!enabled) mutablePresence.value = null
    }

    internal fun publish(track: SpotifyBroadcastBridge.Track, playing: Boolean, positionMs: Long) {
        mutablePresence.value = Presence(
            track.title, track.artist, track.album, playing,
            positionMs, track.durationMs, System.currentTimeMillis()
        )
    }

    internal fun clear() {
        mutablePresence.value = null
    }
}
