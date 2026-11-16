package me.avinas.tempo.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.google.common.util.concurrent.ListenableFuture
import me.avinas.tempo.data.local.dao.UserPreferencesDao
import me.avinas.tempo.data.spotify.SpotifyImportService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import java.util.concurrent.TimeUnit

/**
 * SpotifyPollingWorker periodically fetches recently played tracks from Spotify
 * when Spotify history sync is enabled.
 * 
 * This worker runs every 20 minutes (minimum for WorkManager) and uses cursor-based
 * pagination to only fetch new plays since the last poll.
 */
@HiltWorker
class SpotifyPollingWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val spotifyImportService: SpotifyImportService,
    private val userPreferencesDao: UserPreferencesDao
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "SpotifyPollingWorker"
        private const val WORK_NAME = "spotify_polling"
        private const val SYNC_NOW_NAME = "spotify_sync_now"
        
        /**
         * Schedule periodic polling when Spotify history sync is enabled.
         * Runs every 20 minutes (minimum for WorkManager periodic work).
         */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED) // Need network for API calls
                .setRequiresBatteryNotLow(true) // Be nice to battery
                .build()

            val workRequest = PeriodicWorkRequestBuilder<SpotifyPollingWorker>(
                20, TimeUnit.MINUTES,
                5, TimeUnit.MINUTES // Flex interval
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE, // Replace if already scheduled
                workRequest
            )

            Log.i(TAG, "Spotify polling scheduled (20-minute intervals)")
        }

        /**
         * Queue an immediate sync after a user enables Spotify history sync.
         * Periodic WorkManager tasks can otherwise wait up to 15 minutes.
         */
        fun syncNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<SpotifyPollingWorker>()
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                SYNC_NOW_NAME,
                ExistingWorkPolicy.KEEP,
                request
            )
        }

        /**
         * Cancel the periodic polling when mode is disabled.
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            WorkManager.getInstance(context).cancelUniqueWork(SYNC_NOW_NAME)
            Log.i(TAG, "Spotify polling cancelled")
        }
        
        /**
         * Check if polling is currently scheduled.
         */
        suspend fun isScheduled(context: Context): Boolean {
            val workInfos = WorkManager.getInstance(context)
                .getWorkInfosForUniqueWork(WORK_NAME)
                .awaitResult()
            return workInfos.any { 
                it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING 
            }
        }
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Running Spotify polling")

        // Check if Spotify history sync is still enabled
        val prefs = userPreferencesDao.getSync()
        if (prefs == null || !prefs.spotifyApiOnlyMode) {
            Log.i(TAG, "Spotify history sync disabled, skipping poll and cancelling future runs")
            // Cancel self since mode is disabled
            WorkManager.getInstance(applicationContext).cancelUniqueWork(WORK_NAME)
            return Result.success()
        }

        // Get cursor for incremental polling
        val cursor = prefs.spotifyImportCursor?.toLongOrNull()
        
        return try {
            Log.i(TAG, "Polling Spotify for new plays (cursor=$cursor)")
            
            val result = spotifyImportService.importRecentlyPlayed(
                limit = 50,
                afterCursor = cursor
            )
            
            if (result.isSuccess) {
                Log.i(TAG, "Polling complete: ${result.eventsCreated} new events, ${result.duplicatesSkipped} duplicates")
                Result.success()
            } else {
                val error = result.errors.firstOrNull() ?: "Unknown error"
                Log.e(TAG, "Polling failed: $error")
                
                // Retry on transient errors, fail on auth errors
                if (error.contains("session expired", ignoreCase = true) ||
                    error.contains("not connected", ignoreCase = true)) {
                    Result.failure() // Don't retry auth issues
                } else {
                    Result.retry() // Retry network issues, etc.
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Polling failed with exception", e)
            Result.retry()
        }
    }
}

private suspend fun <T> ListenableFuture<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
    addListener(
        {
            try {
                continuation.resume(get())
            } catch (error: Exception) {
                continuation.resumeWithException(error)
            }
        },
        java.util.concurrent.Executor { command -> command.run() }
    )
    continuation.invokeOnCancellation { cancel(true) }
}
