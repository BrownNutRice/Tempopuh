package me.avinas.tempo.worker

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.await
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import me.avinas.tempo.data.local.dao.TrackDao
import me.avinas.tempo.data.repository.ArtistLinkingService
import java.util.concurrent.TimeUnit

@HiltWorker
class ArtistBackfillWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted parameters: WorkerParameters,
    private val trackDao: TrackDao,
    private val artistLinkingService: ArtistLinkingService,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        if (LastFmImportWorker.isImportRunning(applicationContext)) return Result.retry()
        val startingId = inputData.getLong(AFTER_ID, 0)
        val trackIds = trackDao.getUnlinkedArtistTrackIds(startingId, ROW_LIMIT + 1)
        val deadline = SystemClock.elapsedRealtime() + PASS_BUDGET_MS
        var cursor = startingId

        for (trackId in trackIds.take(ROW_LIMIT)) {
            if (cursor != startingId && SystemClock.elapsedRealtime() >= deadline) break
            try {
                artistLinkingService.linkUnlinkedTrack(trackId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                // Leave this row eligible for the next sweep without blocking later IDs.
                Log.w(WORK_NAME, "Could not backfill artist for track $trackId", failure)
            }
            cursor = trackId
            delay(WRITE_PAUSE_MS)
        }

        if (trackIds.lastOrNull()?.let { it > cursor } == true) {
            WorkManager.getInstance(applicationContext)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request(cursor))
                .await()
        }
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "artist_link_backfill"
        private const val AFTER_ID = "after_id"
        private const val ROW_LIMIT = 50
        private const val PASS_BUDGET_MS = 1_500L
        private const val WRITE_PAUSE_MS = 20L

        fun schedule(context: Context) {
            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request(0))
        }

        private fun request(afterId: Long) = OneTimeWorkRequestBuilder<ArtistBackfillWorker>()
            .setInputData(workDataOf(AFTER_ID to afterId))
            .setInitialDelay(5, TimeUnit.SECONDS)
            .build()
    }
}
