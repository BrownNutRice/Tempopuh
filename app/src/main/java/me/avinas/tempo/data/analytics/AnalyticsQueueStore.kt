package me.avinas.tempo.data.analytics

import android.content.Context
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * File-backed queue of events waiting to be sent.
 *
 * Events are persisted rather than held in memory so a flush survives the process being
 * killed — but only within the retention window in [AnalyticsQueue], which bounds how much
 * sits on disk and how stale a report may be. [purge] is what makes opting out immediate: it
 * deletes the file outright rather than leaving undelivered events on disk.
 */
@Singleton
class AnalyticsQueueStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
    moshi: Moshi
) {

    private val adapter: JsonAdapter<List<QueuedAnalyticsEvent>> = moshi.adapter(
        Types.newParameterizedType(List::class.java, QueuedAnalyticsEvent::class.java)
    )

    private val mutex = Mutex()

    private val atomicQueue by lazy { android.util.AtomicFile(queueFile) }

    private val queueFile: File
        get() = File(context.filesDir, FILE_NAME)

    suspend fun enqueue(event: QueuedAnalyticsEvent) = mutex.withLock {
        withContext(Dispatchers.IO) {
            write(AnalyticsQueue.prune(read() + event, System.currentTimeMillis()))
        }
    }

    suspend fun peekBatch(): List<QueuedAnalyticsEvent> = mutex.withLock {
        withContext(Dispatchers.IO) {
            AnalyticsQueue.nextBatch(read(), System.currentTimeMillis())
        }
    }

    /** Removes exactly the events that were successfully sent. */
    suspend fun dropBatch(sent: List<QueuedAnalyticsEvent>) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val remaining = read().toMutableList()
            sent.forEach { delivered -> remaining.remove(delivered) }
            write(AnalyticsQueue.prune(remaining, System.currentTimeMillis()))
        }
    }

    suspend fun purge() = mutex.withLock {
        withContext(Dispatchers.IO) { atomicQueue.delete() }
        Unit
    }

    suspend fun size(): Int = mutex.withLock {
        withContext(Dispatchers.IO) { read().size }
    }

    private fun read(): List<QueuedAnalyticsEvent> {
        if (!queueFile.exists() && !File(queueFile.path + ".bak").exists()) return emptyList()
        return runCatching { atomicQueue.openRead().bufferedReader().use { adapter.fromJson(it.readText()) } }
            .getOrNull()
            // A corrupt or partially written file is discarded rather than retried forever,
            // and never allowed to take the process down.
            ?: emptyList()
    }

    private fun write(events: List<QueuedAnalyticsEvent>) {
        val stream = atomicQueue.startWrite()
        try {
            stream.write(adapter.toJson(events).toByteArray(Charsets.UTF_8))
            atomicQueue.finishWrite(stream)
        } catch (failure: Exception) {
            atomicQueue.failWrite(stream)
            throw failure
        }
    }

    private companion object {
        const val FILE_NAME = "analytics_queue.json"
    }
}
