package me.avinas.tempo.data.remote.spotify

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores Spotify OAuth tokens in EncryptedSharedPreferences backed by Android Keystore.
 */
@Singleton
class SpotifyTokenStorage @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "SpotifyTokenStorage"
        private const val PREFS_NAME = "spotify_auth_prefs"
        
        private const val KEY_CUSTOM_CLIENT_ID = "custom_client_id"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_EXPIRES_AT = "expires_at"
        private const val KEY_SCOPE = "scope"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_DISPLAY_NAME = "user_display_name"
        private const val KEY_USER_COUNTRY = "user_country"
        
        // PKCE flow temporary storage
        private const val KEY_CODE_VERIFIER = "code_verifier"
        private const val KEY_AUTH_STATE = "auth_state"
        private const val KEY_AUTH_TIMESTAMP = "auth_timestamp"
        
        // Auth attempt timeout (10 minutes)
        private const val AUTH_TIMEOUT_MS = 10 * 60 * 1000L
    }

    @Volatile private var writeFailed = false
    private var _encryptedPrefs: SharedPreferences? = null

    private val encryptedPrefs: SharedPreferences
        get() = synchronized(this) {
            _encryptedPrefs ?: createEncryptedPrefs().also { _encryptedPrefs = it }
        }

    /** A temporarily unavailable Keystore must never erase stores or downgrade encryption. */
    @Synchronized
    private fun createEncryptedPrefs(): SharedPreferences {
        context.deleteSharedPreferences("${PREFS_NAME}_fallback")
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context, PREFS_NAME, masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private fun readString(key: String): String? = try {
        if (writeFailed) null else encryptedPrefs.getString(key, null)
    } catch (failure: Exception) {
        Log.w(TAG, "Secure Spotify storage is unavailable", failure)
        null
    }

    private fun readLong(key: String): Long = try {
        if (writeFailed) -1L else encryptedPrefs.getLong(key, -1L)
    } catch (failure: Exception) {
        Log.w(TAG, "Secure Spotify storage is unavailable", failure)
        -1L
    }

    private fun commitSecurely(editor: SharedPreferences.Editor) {
        // A failed commit can leave a new token in the preferences memory cache.
        // Do not expose that token as a successfully saved connection.
        writeFailed = true
        check(editor.commit()) { "Unable to persist secure Spotify settings" }
        writeFailed = false
    }

    /**
     * Save OAuth tokens and metadata.
     */
    fun saveTokens(
        accessToken: String,
        refreshToken: String?,
        expiresAt: Long,
        scope: String?
    ) {
        encryptedPrefs.edit().apply {
            putString(KEY_ACCESS_TOKEN, accessToken)
            refreshToken?.let { putString(KEY_REFRESH_TOKEN, it) }
            putLong(KEY_EXPIRES_AT, expiresAt)
            scope?.let { putString(KEY_SCOPE, it) }
            commitSecurely(this)
        }
        Log.d(TAG, "Tokens saved, expires at: $expiresAt")
    }

    /**
     * Save user profile information.
     */
    fun saveUserInfo(userId: String, displayName: String?, country: String?) {
        encryptedPrefs.edit().apply {
            putString(KEY_USER_ID, userId)
            displayName?.let { putString(KEY_USER_DISPLAY_NAME, it) }
            country?.let { putString(KEY_USER_COUNTRY, it) }
            commitSecurely(this)
        }
    }

    /**
     * Get the stored access token.
     */
    fun getAccessToken(): String? {
        return readString(KEY_ACCESS_TOKEN)
    }

    /**
     * Get the stored refresh token.
     */
    fun getRefreshToken(): String? {
        return readString(KEY_REFRESH_TOKEN)
    }

    /**
     * Get the token expiry timestamp.
     */
    fun getTokenExpiresAt(): Long? {
        val expiresAt = readLong(KEY_EXPIRES_AT)
        return if (expiresAt > 0) expiresAt else null
    }

    /**
     * Get the stored scope.
     */
    fun getScope(): String? {
        return readString(KEY_SCOPE)
    }

    /**
     * Get stored user ID.
     */
    fun getUserId(): String? {
        return readString(KEY_USER_ID)
    }

    /**
     * Get stored user display name.
     */
    fun getUserDisplayName(): String? {
        return readString(KEY_USER_DISPLAY_NAME)
    }

    /**
     * Get stored user country.
     */
    fun getUserCountry(): String? {
        return readString(KEY_USER_COUNTRY)
    }

    /**
     * Check if tokens are currently stored.
     */
    fun hasTokens(): Boolean {
        return getAccessToken() != null
    }

    /**
     * Check if the access token is expired.
     */
    fun isTokenExpired(): Boolean {
        val expiresAt = getTokenExpiresAt() ?: return true
        return System.currentTimeMillis() >= expiresAt
    }

    /**
     * Clear all stored tokens and user data.
     */
    /** Client ID is public OAuth configuration; store alongside the user's encrypted tokens. */
    fun getCustomClientId(): String? = readString(KEY_CUSTOM_CLIENT_ID)
        ?.takeIf { it.matches(Regex("[0-9a-fA-F]{32}")) }

    fun setCustomClientId(id: String) {
        require(id.isEmpty() || id.matches(Regex("[0-9a-fA-F]{32}")))
        check(getAccessToken() == null) { "Disconnect Spotify before changing the Client ID" }
        encryptedPrefs.edit().apply {
            if (id.isEmpty()) remove(KEY_CUSTOM_CLIENT_ID)
            else putString(KEY_CUSTOM_CLIENT_ID, id)
            commitSecurely(this)
        }
    }

    fun clearTokens() {
        val prefs = try { encryptedPrefs } catch (failure: Exception) {
            Log.w(TAG, "Secure Spotify storage unavailable during cleanup", failure)
            return
        }
        prefs.edit().apply {
            remove(KEY_ACCESS_TOKEN)
            remove(KEY_REFRESH_TOKEN)
            remove(KEY_EXPIRES_AT)
            remove(KEY_SCOPE)
            remove(KEY_USER_ID)
            remove(KEY_USER_DISPLAY_NAME)
            remove(KEY_USER_COUNTRY)
            commitSecurely(this)
        }
        Log.d(TAG, "All tokens cleared")
    }
    
    /**
     * Save PKCE code verifier and state for pending auth.
     * Automatically expires after 10 minutes.
     */
    fun savePendingAuth(codeVerifier: String, state: String) {
        encryptedPrefs.edit().apply {
            putString(KEY_CODE_VERIFIER, codeVerifier)
            putString(KEY_AUTH_STATE, state)
            putLong(KEY_AUTH_TIMESTAMP, System.currentTimeMillis())
            commitSecurely(this)
        }
        Log.d(TAG, "Pending auth saved")
    }
    
    /**
     * Get stored code verifier if state matches and not expired.
     * Returns null if state doesn't match, expired, or not found.
     */
    fun getPendingAuth(state: String): PendingAuth? {
        val storedState = readString(KEY_AUTH_STATE)
        val codeVerifier = readString(KEY_CODE_VERIFIER)
        val timestamp = readLong(KEY_AUTH_TIMESTAMP)
        
        // Check if state matches
        if (storedState != state) {
            Log.w(TAG, "State mismatch for pending auth")
            return null
        }
        
        // Check if expired (older than 10 minutes)
        if (timestamp <= 0 || System.currentTimeMillis() < timestamp ||
            System.currentTimeMillis() - timestamp > AUTH_TIMEOUT_MS) {
            Log.w(TAG, "Pending auth expired")
            clearPendingAuth()
            return null
        }
        
        return if (codeVerifier != null) {
            PendingAuth(codeVerifier, storedState)
        } else {
            null
        }
    }
    
    /**
     * Clear pending auth data.
     */
    fun clearPendingAuth() {
        val prefs = try { encryptedPrefs } catch (failure: Exception) {
            Log.w(TAG, "Secure Spotify storage unavailable during cleanup", failure)
            return
        }
        prefs.edit().apply {
            remove(KEY_CODE_VERIFIER)
            remove(KEY_AUTH_STATE)
            remove(KEY_AUTH_TIMESTAMP)
            commitSecurely(this)
        }
        Log.d(TAG, "Pending auth cleared")
    }
    
    /**
     * Check if there's a pending auth flow (user went to browser but hasn't completed).
     * Returns true if there's valid non-expired pending auth data.
     */
    fun hasPendingAuth(): Boolean {
        val state = readString(KEY_AUTH_STATE) ?: return false
        val codeVerifier = readString(KEY_CODE_VERIFIER) ?: return false
        val timestamp = readLong(KEY_AUTH_TIMESTAMP)
        
        // Check if expired (older than 10 minutes)
        if (timestamp <= 0 || System.currentTimeMillis() < timestamp ||
            System.currentTimeMillis() - timestamp > AUTH_TIMEOUT_MS) {
            Log.d(TAG, "Pending auth expired, clearing")
            clearPendingAuth()
            return false
        }
        
        return state.isNotEmpty() && codeVerifier.isNotEmpty()
    }

    /**
     * Get token status for debugging.
     */
    fun getTokenStatus(): TokenStatus {
        val accessToken = getAccessToken()
        val refreshToken = getRefreshToken()
        val expiresAt = getTokenExpiresAt()
        
        return TokenStatus(
            hasAccessToken = accessToken != null,
            hasRefreshToken = refreshToken != null,
            isExpired = isTokenExpired(),
            expiresAt = expiresAt,
            userId = getUserId()
        )
    }

    data class TokenStatus(
        val hasAccessToken: Boolean,
        val hasRefreshToken: Boolean,
        val isExpired: Boolean,
        val expiresAt: Long?,
        val userId: String?
    )
    
    /**
     * Pending auth data from PKCE flow.
     */
    data class PendingAuth(
        val codeVerifier: String,
        val state: String
    )
}
