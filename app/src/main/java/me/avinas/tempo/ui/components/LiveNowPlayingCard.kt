package me.avinas.tempo.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import me.avinas.tempo.service.SpotifyBroadcastPresence

/**
 * Local, privacy-preserving Rich Presence style card, shared by Home/Profile/Settings.
 * MediaSession and Spotify broadcasts update one source of truth. Spotify's
 * recently-played Web API only reconciles *completed* history in the background.
 */
@Composable
fun LiveNowPlayingCard(modifier: Modifier = Modifier) {
    val presence by SpotifyBroadcastPresence.presence.collectAsState()
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Estimate visual progress from the last observed position, never write this
    // estimate back into the listening database.
    LaunchedEffect(presence?.updatedAtMs, presence?.playing) {
        nowMs = System.currentTimeMillis()
        if (presence?.playing == true) {
            while (true) {
                delay(1000L)
                nowMs = System.currentTimeMillis()
            }
        }
    }

    val current = presence
    val ageMs = if (current == null) Long.MAX_VALUE
        else (nowMs - current.updatedAtMs).coerceAtLeast(0L)
    val isRecent = current != null && ageMs < 15 * 60_000L

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.66f)
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "NOW PLAYING",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            if (!isRecent || current == null) {
                Text(
                    text = "Nothing playing on this device",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "Play music with Tempo's tracking service running.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = current.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOfNotNull(current.artist, current.album)
                        .filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = if (current.playing) "▶ Playing" else "Ⅱ Paused",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = if (current.verifiedByApiAtMs != null &&
                            nowMs - current.verifiedByApiAtMs < 3 * 60_000L
                        ) "${current.source} · API verified" else current.source,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (current.durationMs > 0L) {
                    val elapsed = if (current.playing) ageMs else 0L
                    val position = (current.positionMs + elapsed)
                        .coerceIn(0L, current.durationMs)
                    LinearProgressIndicator(
                        progress = { position.toFloat() / current.durationMs.toFloat() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = "${position / 60_000}:${(position / 1000 % 60).toString().padStart(2, '0')} / " +
                            "${current.durationMs / 60_000}:${(current.durationMs / 1000 % 60).toString().padStart(2, '0')}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (ageMs > 2 * 60_000L) {
                    Text(
                        text = "Last status may be stale; waiting for another playback update",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
