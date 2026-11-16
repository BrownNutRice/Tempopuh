package me.avinas.tempo.data.spotify

import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.avinas.tempo.data.remote.spotify.SpotifyApi
import me.avinas.tempo.data.remote.spotify.SpotifyAuthManager
import me.avinas.tempo.service.SpotifyBroadcastPresence
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A low-rate Spotify Connect snapshot to verify local broadcast metadata.
 * It never creates listening events or overrides a fresh Android playback signal.
 * The separately scheduled recently-played importer owns historical reconciliation.
 */
@Singleton
class SpotifyPlaybackVerifier @Inject constructor(
    private val spotifyApi: SpotifyApi,
    private val authManager: SpotifyAuthManager
) {
    companion object {
        private const val TAG = "SpotifyPlaybackVerifier"
    }

    @Volatile private var retryAfterElapsedMs = 0L
    @Volatile private var scopeUnavailable = false

    fun resetAfterSpotifyLogin() {
        scopeUnavailable = false
        retryAfterElapsedMs = 0L
    }

    suspend fun refresh() = withContext(Dispatchers.IO) {
        if (!authManager.isConnected() || scopeUnavailable) return@withContext
        if (SystemClock.elapsedRealtime() < retryAfterElapsedMs) return@withContext

        try {
            val token = authManager.getValidAccessToken() ?: return@withContext
            val response = spotifyApi.getPlaybackState("Bearer $token")
            when (response.code()) {
                204 -> SpotifyBroadcastPresence.clearApiIfInactive()
                200 -> {
                    val snapshot = response.body() ?: return@withContext
                    if (snapshot.currentlyPlayingType != "track") {
                        SpotifyBroadcastPresence.clearApiIfInactive()
                        return@withContext
                    }
                    val track = snapshot.item ?: return@withContext
                    val title = track.name?.takeIf { it.isNotBlank() } ?: return@withContext
                    val artist = track.artists.orEmpty()
                        .mapNotNull { it.name?.takeIf(String::isNotBlank) }
                        .joinToString(", ")
                    if (artist.isBlank()) return@withContext
                    SpotifyBroadcastPresence.publishFromApi(
                        title = title,
                        artist = artist,
                        album = track.album?.name,
                        playing = snapshot.isPlaying,
                        positionMs = snapshot.progressMs ?: 0L,
                        durationMs = track.durationMs ?: 0L,
                        deviceName = snapshot.device?.name,
                    )
                }
                401, 403 -> {
                    // Existing OAuth grants may predate the new playback-state scope.
                    // Do not retry this endpoint continuously until the app restarts
                    // after a deliberate Spotify reconnect.
                    scopeUnavailable = true
                    Log.w(TAG, "Spotify playback verification unavailable; reconnect Spotify to grant playback-state scope")
                }
                429 -> {
                    val retrySeconds = response.headers()["Retry-After"]?.toLongOrNull()
                        ?.coerceIn(30L, 3600L) ?: 60L
                    retryAfterElapsedMs = SystemClock.elapsedRealtime() + retrySeconds * 1000L
                    Log.w(TAG, "Spotify playback verification rate-limited")
                }
                else -> if (!response.isSuccessful) {
                    Log.d(TAG, "Spotify playback snapshot unavailable: HTTP ${response.code()}")
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Spotify snapshot check failed; local playback remains authoritative", e)
        }
    }
}
