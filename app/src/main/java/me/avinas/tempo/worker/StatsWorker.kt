package me.avinas.tempo.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.*
import me.avinas.tempo.R
import me.avinas.tempo.data.repository.RoomStatsRepository
import me.avinas.tempo.utils.ForegroundNotificationGuard
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/** Retained so pending work from older installs can finish without computing unused stats. */
@HiltWorker
class StatsPrecomputeWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = Result.success()

    companion object {
        fun cancelLegacyWork(context: Context) {
            WorkManager.getInstance(context).cancelAllWorkByTag(StatsPrecomputeWorker::class.java.name)
        }
    }
}

/**
 * Worker that invalidates stats cache when new listening events are detected.
 * Triggered by the MusicTrackingService when new tracks are logged.
 */
@HiltWorker
class StatsCacheInvalidationWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted private val workerParams: WorkerParameters,
    private val statsRepository: RoomStatsRepository
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "StatsCacheInvalidation"
        const val WORK_NAME = "stats_cache_invalidation"
        const val KEY_EVENT_TIMESTAMP = "event_timestamp"
        private const val NOTIFICATION_CHANNEL_ID = "stats_cache_worker"
        // 3010, not 3007: ServiceHealthWorker owns 3007. (This worker is not currently scheduled
        // from anywhere — see docs/PERFORMANCE_AUDIT.md #9 — so the clash was latent rather than
        // live, but leaving a duplicate id in the tree invites a real one later.)
        private const val NOTIFICATION_ID = 3010

        /**
         * Trigger cache invalidation after a new listening event.
         */
        fun trigger(context: Context, eventTimestamp: Long) {
            val workRequest = OneTimeWorkRequestBuilder<StatsCacheInvalidationWorker>()
                .setInputData(workDataOf(KEY_EVENT_TIMESTAMP to eventTimestamp))
                .setInitialDelay(1, TimeUnit.SECONDS) // Small delay to batch events
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
        }
    }

    override suspend fun doWork(): Result {
        val eventTimestamp = inputData.getLong(KEY_EVENT_TIMESTAMP, System.currentTimeMillis())
        
        Log.d(TAG, "Invalidating stats cache for event at $eventTimestamp")
        
        // Notify repository of new event (triggers smart cache invalidation)
        statsRepository.onNewListeningEvent(eventTimestamp)
        
        return Result.success()
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Cache Update",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }
        
        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Updating Cache")
            .setContentText("Refreshing stats...")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
        
        return ForegroundNotificationGuard.postableForegroundInfo(
            context = context,
            notificationId = NOTIFICATION_ID,
            requestedChannelId = NOTIFICATION_CHANNEL_ID,
            notification = notification,
            logTag = TAG,
        )
    }
}
