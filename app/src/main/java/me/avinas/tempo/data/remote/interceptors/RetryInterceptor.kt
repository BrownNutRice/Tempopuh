package me.avinas.tempo.data.remote.interceptors

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlin.math.pow

/**
 * Small, bounded retries for idempotent HTTP requests. A server-directed
 * Retry-After beyond the short budget is returned to the caller so WorkManager
 * or the relevant service can back off without blocking a network thread.
 *
 * Never close the final response before returning it to Retrofit.
 */
@Singleton
class RetryInterceptor @Inject constructor() : Interceptor {
    companion object {
        private const val TAG = "RetryInterceptor"
        private const val MAX_RETRIES = 2
        private const val INITIAL_BACKOFF_MS = 1_000L
        private const val MAX_BACKOFF_MS = 10_000L
        private const val BACKOFF_MULTIPLIER = 2.0
        private val RETRYABLE_STATUS_CODES = setOf(429, 500, 502, 503, 504)
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        // POST/PUT/PATCH/DELETE may create duplicate side effects if retried.
        val retrySafe = request.method == "GET" || request.method == "HEAD"
        if (!retrySafe) return chain.proceed(request)

        var lastError: IOException? = null
        for (attempt in 0..MAX_RETRIES) {
            val response = try {
                chain.proceed(request)
            } catch (e: IOException) {
                lastError = e
                if (attempt == MAX_RETRIES) throw e
                Log.w(TAG, "Network error on attempt ${attempt + 1}; retrying", e)
                sleepForRetry(calculateBackoff(attempt))
                continue
            }

            if (response.isSuccessful || response.code !in RETRYABLE_STATUS_CODES) {
                return response
            }

            // A final retryable response still belongs to Retrofit; returning an
            // already-closed Response breaks error-body parsing and retry handling.
            if (attempt == MAX_RETRIES) return response

            val afterSeconds = response.header("Retry-After")?.toLongOrNull()
            val delayMs = if (afterSeconds != null && afterSeconds >= 0) {
                // Avoid long overflow and respect the server's requested delay.
                if (afterSeconds > MAX_BACKOFF_MS / 1_000L) return response
                afterSeconds * 1_000L
            } else calculateBackoff(attempt)

            response.close()
            Log.d(TAG, "Retrying GET after ${delayMs}ms (attempt ${attempt + 1})")
            sleepForRetry(delayMs)
        }
        throw lastError ?: IOException("Retry attempts exhausted")
    }

    private fun sleepForRetry(delayMs: Long) {
        try {
            Thread.sleep(delayMs.coerceIn(0L, MAX_BACKOFF_MS))
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException("HTTP retry interrupted", interrupted)
        }
    }

    private fun calculateBackoff(retryCount: Int): Long =
        min((INITIAL_BACKOFF_MS * BACKOFF_MULTIPLIER.pow(retryCount.toDouble())).toLong(),
            MAX_BACKOFF_MS)
}
