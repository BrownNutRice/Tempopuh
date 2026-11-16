package me.avinas.tempo.ui.spotify

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import me.avinas.tempo.MainActivity
import me.avinas.tempo.data.remote.spotify.SpotifyApi
import me.avinas.tempo.data.remote.spotify.SpotifyAuthManager
import me.avinas.tempo.data.repository.EnrichedMetadataRepository
import me.avinas.tempo.data.local.dao.UserPreferencesDao
import me.avinas.tempo.data.local.entities.UserPreferences
import me.avinas.tempo.worker.SpotifyPollingWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * OAuth redirect target, registered in AndroidManifest.xml for `tempo://spotify-callback`.
 */
@AndroidEntryPoint
class SpotifyCallbackActivity : ComponentActivity() {

    companion object {
        private const val TAG = "SpotifyCallback"
        const val EXTRA_SPOTIFY_AUTH_SUCCESS = "spotify_auth_success"
    }

    @Inject
    lateinit var authManager: SpotifyAuthManager
    
    @Inject
    lateinit var enrichedMetadataRepository: EnrichedMetadataRepository

    @Inject
    lateinit var userPreferencesDao: UserPreferencesDao

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Log.d(TAG, "SpotifyCallbackActivity created")
        
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val uri = intent?.data
        
        if (uri != null && isSpotifyCallback(uri)) {
            Log.d(TAG, "Received Spotify callback")
            
            // Process the callback using lifecycleScope to prevent memory leaks
            lifecycleScope.launch {
                val success = authManager.handleCallback(uri)
                Log.d(TAG, "Callback handled, success: $success")
                
                if (success) {
                    enrichedMetadataRepository.queueAllForSpotifyEnrichment()

                    // Opt in to automatic API reconciliation after an explicit
                    // successful OAuth connection. Keep local broadcasts and
                    // MediaSession tracking enabled as independent live sources.
                    val preferences = userPreferencesDao.getSync() ?: UserPreferences()
                    if (!preferences.spotifyApiOnlyMode) {
                        userPreferencesDao.upsert(preferences.copy(spotifyApiOnlyMode = true))
                    }
                    SpotifyPollingWorker.schedule(applicationContext)
                    SpotifyPollingWorker.syncNow(applicationContext)

                    // History reconstruction is triggered from MainActivity, whose
                    // viewModelScope outlives this activity and won't get cancelled. It uses
                    // the "honest data only" path: liked tracks + yearly playlists.
                    Log.i(TAG, "Auth successful, history reconstruction will be triggered from MainActivity")
                }
                
                navigateToMain(success)
            }
        } else {
            Log.w(TAG, "Invalid Spotify callback URI")
            navigateToMain(false)
        }
    }

    private fun isSpotifyCallback(uri: Uri): Boolean {
        val expectedUri = Uri.parse(SpotifyApi.REDIRECT_URI)
        return uri.scheme == expectedUri.scheme && uri.host == expectedUri.host
    }

    private fun navigateToMain(authSuccess: Boolean) {
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_SPOTIFY_AUTH_SUCCESS, authSuccess)
        }
        startActivity(mainIntent)
        finish()
    }
}
