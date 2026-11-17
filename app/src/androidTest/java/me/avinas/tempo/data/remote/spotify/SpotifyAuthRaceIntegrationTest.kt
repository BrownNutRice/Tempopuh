package me.avinas.tempo.data.remote.spotify

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class SpotifyAuthRaceIntegrationTest {
    private lateinit var storage: SpotifyTokenStorage
    private lateinit var context: Context
    private lateinit var client: OkHttpClient
    private lateinit var entered: CountDownLatch
    private lateinit var release: CountDownLatch

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        storage = SpotifyTokenStorage(context)
        storage.clearTokens()
        storage.clearPendingAuth()
        storage.setCustomClientId("1".repeat(32))
        entered = CountDownLatch(1)
        release = CountDownLatch(1)
        client = OkHttpClient.Builder().addInterceptor { chain ->
            entered.countDown()
            check(release.await(5, TimeUnit.SECONDS)) { "Token response barrier timed out" }
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK")
                .body("""{"access_token":"late","refresh_token":"late-refresh","expires_in":3600}"""
                    .toResponseBody("application/json".toMediaType())).build()
        }.build()
    }

    @After fun tearDown() {
        release.countDown()
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
        storage.clearTokens()
        storage.clearPendingAuth()
        storage.setCustomClientId("")
    }

    @Test fun delayedRefreshCannotReconnectAfterDisconnect() = runBlocking {
        withTimeout(10_000) {
            storage.saveTokens("expired", "refresh", 1L, "scope")
            val manager = SpotifyAuthManager(context, storage, client)
            val pending = async(Dispatchers.IO) { manager.getValidAccessToken() }
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                manager.disconnect()
                release.countDown()
                assertNull(pending.await())
                assertNull(storage.getAccessToken())
                assertNull(storage.getRefreshToken())
                assertSame(SpotifyAuthManager.AuthState.NotConnected, manager.authState.value)
            } finally { release.countDown() }
        }
    }

    @Test fun delayedExchangeCannotReconnectOrConsumeNewLoginAfterDisconnect() = runBlocking {
        withTimeout(10_000) {
            val manager = SpotifyAuthManager(context, storage, client)
            val state = manager.startLoginFlow().data!!.getQueryParameter("state")!!
            val callback = Uri.parse("tempo://spotify-callback").buildUpon()
                .appendQueryParameter("state", state).appendQueryParameter("code", "test-code").build()
            val pending = async(Dispatchers.IO) { manager.handleCallback(callback) }
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                manager.disconnect()
                assertFalse(storage.hasPendingAuth())
                manager.startLoginFlow()
                release.countDown()
                assertFalse(pending.await())
                assertFalse(manager.isConnected())
                assertTrue(storage.hasPendingAuth())
                assertSame(SpotifyAuthManager.AuthState.Connecting, manager.authState.value)
            } finally { release.countDown() }
        }
    }

    @Test fun clientIdChangeInvalidatesAnExchangeAlreadyInFlight() = runBlocking {
        withTimeout(10_000) {
            val manager = SpotifyAuthManager(context, storage, client)
            val state = manager.startLoginFlow().data!!.getQueryParameter("state")!!
            val callback = Uri.parse("tempo://spotify-callback").buildUpon()
                .appendQueryParameter("state", state).appendQueryParameter("code", "test-code").build()
            val pending = async(Dispatchers.IO) { manager.handleCallback(callback) }
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                manager.setClientId("2".repeat(32))
                release.countDown()
                assertFalse(pending.await())
                assertNull(storage.getAccessToken())
                assertEquals("2".repeat(32), storage.getCustomClientId())
                assertSame(SpotifyAuthManager.AuthState.NotConnected, manager.authState.value)
            } finally { release.countDown() }
        }
    }

    @Test fun staleCallbackCannotOverwriteAnInFlightNewLogin() = runBlocking {
        withTimeout(10_000) {
            val manager = SpotifyAuthManager(context, storage, client)
            val oldState = manager.startLoginFlow().data!!.getQueryParameter("state")!!
            val newState = manager.startLoginFlow().data!!.getQueryParameter("state")!!
            fun callback(state: String) = Uri.parse("tempo://spotify-callback").buildUpon()
                .appendQueryParameter("state", state).appendQueryParameter("code", "test-code").build()
            val pending = async(Dispatchers.IO) { manager.handleCallback(callback(newState)) }
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                assertFalse(manager.handleCallback(callback(oldState)))
                assertSame(SpotifyAuthManager.AuthState.Connecting, manager.authState.value)
                release.countDown()
                assertTrue(pending.await())
                assertEquals("late", storage.getAccessToken())
            } finally { release.countDown() }
        }
    }
}
