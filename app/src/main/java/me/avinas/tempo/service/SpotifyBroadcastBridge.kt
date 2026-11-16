package me.avinas.tempo.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
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
    private var track: Track? = null
    private var playing: Boolean? = null
    private var positionMs = 0L
    private var lastSentAt = 0L

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
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
            if (sentAt < now - 120_000L || sentAt > now + 30_000L || sentAt < lastSentAt) return
            lastSentAt = sentAt

            when (action) {
                METADATA -> {
                    val title = intent.getStringExtra("track")?.trim()?.takeIf { it.isNotEmpty() } ?: return
                    val artist = intent.getStringExtra("artist")?.trim()?.takeIf { it.isNotEmpty() } ?: return
                    track = Track(
                        title = title,
                        artist = artist,
                        album = intent.getStringExtra("album"),
                        durationMs = intent.getIntExtra("length", 0).coerceAtLeast(0) * 1000L,
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
            // Cross-app broadcasts must be exported on recent Android versions.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter)
            }
            registered = true
        } catch (e: Exception) {
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
        }
    }
}
