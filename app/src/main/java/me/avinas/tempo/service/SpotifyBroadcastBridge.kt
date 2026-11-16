package me.avinas.tempo.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.util.Log

/**
 * Spotify's opt-in Device Broadcast Status receiver.
 * It is dynamically registered while Tempo's tracking service is alive.
 * A playback event alone does not contain track metadata: wait until both are known.
 */
internal class SpotifyBroadcastBridge(
    private val context: Context,
    private val onPlayback: (Track, Boolean, Long) -> Unit,
    private val onQueueChanged: () -> Unit,
) {
    data class Track(
        val title: String,
        val artist: String,
        val album: String?,
        val durationMs: Long,
    )

    companion object {
        private const val TAG = "SpotifyBroadcastBridge"
        private const val PACKAGE = "com.spotify.music"
        private const val METADATA = "$PACKAGE.metadatachanged"
        private const val PLAYBACK = "$PACKAGE.playbackstatechanged"
        private const val QUEUE = "$PACKAGE.queuechanged"
    }

    private var registered = false
    private var receiverThread: HandlerThread? = null
    private var track: Track? = null
    private var playing: Boolean? = null
    private var positionMs = 0L
    private val lastSentAtByAction = mutableMapOf<String, Long>()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (!SpotifyBroadcastPresence.enabled(context)) return
            val action = intent.action ?: return
            if (action != METADATA && action != PLAYBACK && action != QUEUE) return

            // On Android 14+ reject broadcasts from packages other than Spotify.
            if (Build.VERSION.SDK_INT >= 34) {
                val senderUid = sentFromUid
                if (senderUid != Process.INVALID_UID &&
                    context.packageManager.getPackagesForUid(senderUid)?.contains(PACKAGE) != true
                ) return
            }

            val now = System.currentTimeMillis()
            val sentAt = intent.getLongExtra("timeSent", now)
            // Metadata and playback broadcasts can be delivered out of order.
            // Only discard stale events within the SAME action type.
            if (sentAt < now - 120_000L || sentAt > now + 30_000L) return
            if (sentAt < (lastSentAtByAction[action] ?: 0L)) return
            lastSentAtByAction[action] = sentAt

            when (action) {
                METADATA -> {
                    val title = intent.getStringExtra("track")?.trim()?.takeIf { it.isNotEmpty() } ?: return
                    val artist = intent.getStringExtra("artist")?.trim()?.takeIf { it.isNotEmpty() } ?: return
                    track = Track(
                        title = title,
                        artist = artist,
                        album = intent.getStringExtra("album"),
                        // Spotify's documentation says seconds, but recent versions
                        // are reported to send milliseconds. Accept both forms.
                        durationMs = intent.getIntExtra("length", 0).coerceAtLeast(0).toLong()
                            .let { if (it in 1L..10_000L) it * 1000L else it },
                    )
                    positionMs = 0L
                }
                PLAYBACK -> {
                    playing = intent.getBooleanExtra("playing", false)
                    positionMs = intent.getIntExtra("playbackPosition", 0).coerceAtLeast(0).toLong()
                }
                QUEUE -> {
                    onQueueChanged()
                    return
                }
            }

            val currentTrack = track ?: return
            val isPlaying = playing ?: return
            SpotifyBroadcastPresence.publish(currentTrack, isPlaying, positionMs)
            onPlayback(currentTrack, isPlaying, positionMs)
        }
    }

    fun start() {
        if (registered) return
        val filter = IntentFilter().apply {
            addAction(METADATA)
            addAction(PLAYBACK)
            addAction(QUEUE)
        }
        try {
            // Parse events and update the session pipeline off the main UI thread.
            val thread = HandlerThread("TempoSpotifyBroadcast").also { it.start() }
            receiverThread = thread
            val scheduler = Handler(thread.looper)
            // Cross-app broadcasts must be exported on recent Android versions.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, null, scheduler, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter, null, scheduler)
            }
            registered = true
        } catch (e: Exception) {
            receiverThread?.quitSafely()
            receiverThread = null
            Log.w(TAG, "Spotify broadcasts unavailable; MediaSession tracking remains active", e)
        }
    }

    fun stop() {
        if (!registered) return
        try {
            context.unregisterReceiver(receiver)
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering Spotify receiver", e)
        } finally {
            registered = false
            receiverThread?.quitSafely()
            receiverThread = null
            SpotifyBroadcastPresence.clear()
        }
    }
}
