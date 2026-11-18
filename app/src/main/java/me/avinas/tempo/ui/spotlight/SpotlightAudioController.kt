package me.avinas.tempo.ui.spotlight

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

/**
 * Controller for handling audio playback in Spotlight Stories.
 * Manages 30-second song previews with specific volume ramps and global mute state.
 */
class SpotlightAudioController(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private var player: ExoPlayer? = null
    private var volumeRampJob: Job? = null
    
    // Global mute state
    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()
    
    // Current track being played
    private var currentUrl: String? = null
    
    private var paused = true
    private var targetVolume = 0.3f

    // State to track if we should be playing (independent of mute)
    private var isPlayingState = false
    
    // Error callback for notifying UI of playback failures
    var onPlaybackError: ((Exception) -> Unit)? = null

    init {
        initializePlayer()
    }

    private fun initializePlayer() {
        if (player == null) {
            player = ExoPlayer.Builder(context).build().apply {
                setAudioAttributes(
                    AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                    true
                )
                repeatMode = Player.REPEAT_MODE_OFF
                volume = if (_isMuted.value) 0f else 0.3f // Start low for fade-in
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        when (playbackState) {
                            Player.STATE_BUFFERING -> android.util.Log.d("SpotlightAudio", "Player: BUFFERING")
                            Player.STATE_READY -> {
                                android.util.Log.d("SpotlightAudio", "Player: READY (PlayWhenReady=${player?.playWhenReady})")
                            }
                            Player.STATE_ENDED -> {
                                android.util.Log.d("SpotlightAudio", "Player: ENDED")
                                isPlayingState = false
                            }
                            Player.STATE_IDLE -> android.util.Log.d("SpotlightAudio", "Player: IDLE")
                        }
                    }
                    
                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        android.util.Log.e("SpotlightAudio", "Player Error: ${error.message}", error)
                        onPlaybackError?.invoke(error)
                        // Keep a failed preview silent instead of playing a different page's track.
                        stop()
                    }
                })
            }
        }
    }

    /**
     * Start playing a preview URL with a gentle fade-in (Slide 1 behavior).
     * Used for the "Setup" slide.
     */
    fun playSetup(url: String) {
        if (currentUrl == url && isPlayingState) return // Already playing
        
        playUrl(url, startVolume = 0.1f, targetVolume = 0.3f, fadeDurationMs = 500)
    }

    /**
     * Ramp up volume for the "Highlight" slide (Slide 2 behavior).
     * Continues playing existing track but increases volume.
     */
    fun playHighlight(url: String) {
        if (currentUrl != url || !isPlayingState) {
            // Start a new or finished preview from the beginning.
            playUrl(url, startVolume = 0.1f, targetVolume = 0.7f, fadeDurationMs = 500)
        } else {
            targetVolume = 0.7f
            if (paused || _isMuted.value) return
            
            // Already playing, just ramp up volume
            rampVolume(currentVolume = player?.volume ?: 0f, targetVolume = 0.7f, durationMs = 1000)
        }
    }

    /**
     * Stop playback immediately (on slide exit).
     */
    fun stop() {
        isPlayingState = false
        volumeRampJob?.cancel()
        player?.stop()
        currentUrl = null
    }

    /**
     * Fade out and stop (gentler exit).
     */
    fun fadeOutAndStop() {
        isPlayingState = false
        volumeRampJob?.cancel()
        
        if (paused || _isMuted.value || player == null) {
            stop()
            return
        }
        
        volumeRampJob = scope.launch {
            val startVol = player?.volume ?: 0f
            val steps = 10
            for (i in 1..steps) {
                val progress = i.toFloat() / steps
                player?.volume = max(0f, startVol * (1 - progress))
                delay(50)
            }
            stop()
        }
    }

    /**
     * Toggle mute state.
     */
    fun toggleMute() {
        _isMuted.value = !_isMuted.value
        if (_isMuted.value) {
            // Mute immediately
            volumeRampJob?.cancel()
            player?.volume = 0f
        } else {
            // Unmute - restore volume
            if (isPlayingState && !paused) {
                rampVolume(0f, targetVolume, 500)
            }
        }
    }

    fun setPaused(value: Boolean) {
        if (paused == value) return
        paused = value
        if (paused) {
            volumeRampJob?.cancel()
            player?.pause()
            if (!isPlayingState) stop()
        } else if (isPlayingState) {
            player?.play()
            rampVolume(player?.volume ?: 0f, targetVolume, 500)
        }
    }

    private fun playUrl(url: String, startVolume: Float, targetVolume: Float, fadeDurationMs: Long) {
        // If we are already prepared for this URL, don't reset the player
        val needsPrepare = currentUrl != url
        
        volumeRampJob?.cancel()
        currentUrl = url
        this.targetVolume = targetVolume
        isPlayingState = true
        
        if (needsPrepare) {
             val mediaItem = MediaItem.fromUri(url)
             player?.setMediaItem(mediaItem)
             player?.prepare()
        } else {
             // Same URL but player may have ended or stopped — restart from beginning
             player?.seekTo(0)
        }
        
        if (_isMuted.value) {
            player?.volume = 0f
            android.util.Log.d("SpotlightAudio", "Play requested but MUTED")
        } else {
            player?.volume = startVolume
            android.util.Log.d("SpotlightAudio", "Starting playback: $url (Vol: $startVolume -> $targetVolume)")
            rampVolume(startVolume, targetVolume, fadeDurationMs)
        }
        
        if (!paused) player?.play()
    }

    private fun rampVolume(currentVolume: Float, targetVolume: Float, durationMs: Long) {
        if (paused || _isMuted.value) return
        volumeRampJob?.cancel()
        
        volumeRampJob = scope.launch {
            try {
                val steps = 20
                val delayStep = max(10L, durationMs / steps)
                val volStep = (targetVolume - currentVolume) / steps
                
                android.util.Log.d("SpotlightAudio", "Ramping volume: $currentVolume -> $targetVolume in ${steps} steps")
                
                var vol = currentVolume
                for (i in 1..steps) {
                    if (_isMuted.value) break // Stop ramping if muted mid-way
                    
                    vol += volStep
                    val clampedVol = min(1f, max(0f, vol))
                    player?.volume = clampedVol
                    delay(delayStep)
                }
                // Snap to target volume at end of ramp
                if (!_isMuted.value) {
                    player?.volume = targetVolume
                    android.util.Log.d("SpotlightAudio", "Volume ramp complete: $targetVolume")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                 android.util.Log.e("SpotlightAudio", "Volume ramp failed", e)
                 // Safety fallback
                 if (!_isMuted.value) player?.volume = targetVolume
            }
        }
    }

    fun release() {
        volumeRampJob?.cancel()
        player?.release()
        player = null
    }
}
