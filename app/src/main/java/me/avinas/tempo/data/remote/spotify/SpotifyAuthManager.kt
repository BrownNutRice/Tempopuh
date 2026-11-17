package me.avinas.tempo.data.remote.spotify

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Spotify OAuth 2.0 PKCE authentication flow manager.
 */
@Singleton
class SpotifyAuthManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val tokenStorage: SpotifyTokenStorage,
    @param:Named("spotify") private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val TAG = "SpotifyAuthManager"
        
        // Set in local.properties
        val CLIENT_ID: String = me.avinas.tempo.BuildConfig.SPOTIFY_CLIENT_ID

        
        // PKCE constants
        private const val CODE_VERIFIER_LENGTH = 64
        private const val CODE_CHALLENGE_METHOD = "S256"
        
        // Buffer time before token expiry to refresh (5 minutes)
        private const val TOKEN_REFRESH_BUFFER_MS = 5 * 60 * 1000L
    }

    sealed class AuthState {
        object NotConnected : AuthState()
        object Connecting : AuthState()
        data class Connected(val user: SpotifyUser?) : AuthState()
        data class Error(val message: String) : AuthState()
    }

    private val _authState = MutableStateFlow<AuthState>(AuthState.NotConnected)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()
    
    // Mutex to prevent race condition during token refresh
    private val tokenRefreshMutex = Mutex()
    private val sessions = AccountSessionGate<Call> { it.cancel() }

    init {
        checkStoredTokens()
    }

    private fun checkStoredTokens() {
        val accessToken = tokenStorage.getAccessToken()
        if (accessToken != null) {
            _authState.value = AuthState.Connected(null)
        }
    }

    fun effectiveClientId(): String = tokenStorage.getCustomClientId() ?: CLIENT_ID

    fun isConfigured(): Boolean = effectiveClientId().matches(Regex("[0-9a-fA-F]{32}"))

    fun isConnected(): Boolean {
        return tokenStorage.getAccessToken() != null
    }

    /** Null if not authenticated, invalidated while waiting, or if refresh fails. */
    suspend fun getValidAccessToken(): String? {
        val generation = sessions.read { it }
        return tokenRefreshMutex.withLock {
            val session = sessions.ifCurrent(generation) {
                TokenSession(generation, effectiveClientId(), tokenStorage.getAccessToken(),
                    tokenStorage.getRefreshToken(), tokenStorage.getTokenExpiresAt(), tokenStorage.getScope())
            } ?: return@withLock null
            if (session.accessToken == null) return@withLock null
            if (session.expiresAt != null && System.currentTimeMillis() > session.expiresAt - TOKEN_REFRESH_BUFFER_MS) {
                refreshAccessToken(session)
            } else sessions.ifCurrent(generation) { session.accessToken }
        }
    }

    /** Route client configuration through the same invalidation boundary as auth requests. */
    fun setClientId(clientId: String) = sessions.replace {
        tokenStorage.setCustomClientId(clientId)
        tokenStorage.clearPendingAuth()
        _authState.value = AuthState.NotConnected
    }

    /** Returns the authorization page; each new attempt invalidates older responses. */
    fun startLoginFlow(): Intent = sessions.replace {
        require(isConfigured()) { "Enter a valid Spotify Client ID before connecting" }
        tokenStorage.clearTokens()
        tokenStorage.clearPendingAuth()
        val verifier = generateCodeVerifier()
        val state = generateCodeVerifier()
        tokenStorage.savePendingAuth(verifier, state)
        _authState.value = AuthState.Connecting
        Intent(Intent.ACTION_VIEW, Uri.parse(buildAuthorizationUrl(generateCodeChallenge(verifier), state)))
    }

    /** Only a callback matching the current, single-use persisted state can alter auth state. */
    suspend fun handleCallback(uri: Uri): Boolean {
        val state = uri.getQueryParameter("state") ?: return false
        val code = uri.getQueryParameter("code")
        val error = uri.getQueryParameter("error")
        val attempt = sessions.read { generation ->
            // Missing, expired and superseded callbacks cannot change a newer attempt's UI.
            val pending = tokenStorage.getPendingAuth(state) ?: return@read null
            tokenStorage.clearPendingAuth()
            if (error != null || code == null) {
                _authState.value = AuthState.Error(
                    if (error == "access_denied") "You cancelled the Spotify login"
                    else "Authentication failed. Please try again."
                )
                return@read null
            }
            ExchangeAttempt(generation, effectiveClientId(), pending.codeVerifier)
        } ?: return false
        return exchangeCodeForTokens(requireNotNull(code), attempt)
    }

    private suspend fun exchangeCodeForTokens(code: String, attempt: ExchangeAttempt): Boolean = withContext(Dispatchers.IO) {
        try {
            val body = FormBody.Builder()
                .add("client_id", attempt.clientId)
                .add("grant_type", "authorization_code")
                .add("code", code)
                .add("redirect_uri", SpotifyApi.REDIRECT_URI)
                .add("code_verifier", attempt.verifier).build()
            val response = requestTokens(body, attempt.generation) ?: return@withContext false
            currentCoroutineContext().ensureActive()
            if (response.code in 200..299 && response.body != null) {
                val json = JSONObject(response.body)
                sessions.ifCurrent(attempt.generation) {
                    tokenStorage.saveTokens(
                        json.getString("access_token"), json.optString("refresh_token"),
                        System.currentTimeMillis() + json.getInt("expires_in") * 1000L,
                        json.optString("scope")
                    )
                    _authState.value = AuthState.Connected(null)
                    true
                } ?: false
            } else {
                sessions.ifCurrent(attempt.generation) { _authState.value = AuthState.Error("Failed to get access token") }
                false
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            // A cancelled/obsolete call must not overwrite a newer login or disconnected state.
            sessions.ifCurrent(attempt.generation) { _authState.value = AuthState.Error("Spotify authentication failed") }
            Log.w(TAG, "Spotify token exchange failed")
            false
        }
    }

    private suspend fun refreshAccessToken(session: TokenSession): String? = withContext(Dispatchers.IO) {
        try {
            if (session.refreshToken == null) {
                sessions.ifCurrent(session.generation) { disconnect() }
                return@withContext null
            }
            val body = FormBody.Builder().add("client_id", session.clientId)
                .add("grant_type", "refresh_token").add("refresh_token", session.refreshToken).build()
            val response = requestTokens(body, session.generation) ?: return@withContext null
            currentCoroutineContext().ensureActive()
            if (response.code in 200..299 && response.body != null) {
                val json = JSONObject(response.body)
                sessions.ifCurrent(session.generation) {
                    val token = json.getString("access_token")
                    tokenStorage.saveTokens(token, json.optString("refresh_token", session.refreshToken),
                        System.currentTimeMillis() + json.getInt("expires_in") * 1000L, session.scope)
                    token
                }
            } else {
                if (response.code == 400 || response.code == 401) {
                    sessions.ifCurrent(session.generation) { disconnect() }
                }
                null
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Log.w(TAG, "Spotify token refresh failed")
            null
        }
    }

    /** Async OkHttp allows coroutine cancellation and disconnect to cancel outstanding calls. */
    private suspend fun requestTokens(body: FormBody, generation: Long): TokenResponse? {
        val call = okHttpClient.newCall(Request.Builder().url(SpotifyApi.TOKEN_URL)
            .post(body).header("Content-Type", "application/x-www-form-urlencoded").build())
        if (!sessions.register(generation, call)) return null
        try {
            return suspendCancellableCoroutine { continuation ->
                continuation.invokeOnCancellation { call.cancel() }
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        continuation.resumeWithException(e)
                    }
                    override fun onResponse(call: Call, response: Response) {
                        try {
                            val result = response.use { TokenResponse(it.code, it.body?.string()) }
                            continuation.resume(result)
                        } catch (failure: Exception) {
                            continuation.resumeWithException(failure)
                        }
                    }
                })
            }
        } finally {
            sessions.finish(call)
        }
    }

    fun disconnect() = sessions.replace {
        try {
            tokenStorage.clearTokens()
        } finally {
            try { tokenStorage.clearPendingAuth() }
            finally { _authState.value = AuthState.NotConnected }
        }
    }

    private data class ExchangeAttempt(val generation: Long, val clientId: String, val verifier: String)
    private data class TokenSession(
        val generation: Long, val clientId: String, val accessToken: String?,
        val refreshToken: String?, val expiresAt: Long?, val scope: String?
    )
    private data class TokenResponse(val code: Int, val body: String?)

    private fun buildAuthorizationUrl(codeChallenge: String, state: String): String {
        return Uri.parse(SpotifyApi.AUTH_URL).buildUpon()
            .appendQueryParameter("client_id", effectiveClientId())
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", SpotifyApi.REDIRECT_URI)
            .appendQueryParameter("code_challenge_method", CODE_CHALLENGE_METHOD)
            .appendQueryParameter("code_challenge", codeChallenge)
            .appendQueryParameter("state", state)
            .appendQueryParameter("scope", SpotifyApi.SCOPES)
            .build()
            .toString()
    }

    /**
     * PKCE allows 43-128 characters from the unreserved URI set.
     */
    private fun generateCodeVerifier(): String {
        val random = SecureRandom()
        val bytes = ByteArray(CODE_VERIFIER_LENGTH)
        random.nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    /**
     * PKCE S256: `BASE64URL(SHA256(verifier))`.
     */
    private fun generateCodeChallenge(codeVerifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(codeVerifier.toByteArray(Charsets.US_ASCII))
        return Base64.encodeToString(hash, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }
}
