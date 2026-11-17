package me.avinas.tempo.ui.details

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil3.BitmapImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import me.avinas.tempo.data.enrichment.MusicBrainzEnrichmentService
import me.avinas.tempo.ui.theme.LocalTempoPalette

/** Artwork keeps its identity; absent or failed artwork uses the active app accent. */
@Composable
internal fun rememberArtworkAccent(primaryUrl: String?, backupUrl: String? = null): Color {
    val fallback = LocalTempoPalette.current.accent
    val context = LocalContext.current
    // A new item or palette gets its own state immediately, including while loading.
    var accent by remember(primaryUrl, backupUrl, fallback) { mutableStateOf(fallback) }
    LaunchedEffect(primaryUrl, backupUrl, fallback) {
        val urls = listOfNotNull(primaryUrl, backupUrl)
            .filter { it.isNotBlank() }
            .mapNotNull { MusicBrainzEnrichmentService.fixHttpUrl(it) }
            .distinct()
        for (url in urls) {
            try {
                val result = context.imageLoader.execute(
                    ImageRequest.Builder(context)
                        .data(url)
                        .size(64, 64)
                        .allowHardware(false)
                        .build(),
                )
                val bitmap = (result.image as? BitmapImage)?.bitmap ?: continue
                val color = withContext(Dispatchers.Default) {
                    val palette = Palette.from(bitmap).generate()
                    val rgb = palette.vibrantSwatch?.rgb
                        ?: palette.mutedSwatch?.rgb
                        ?: palette.dominantSwatch?.rgb
                    ensureActive()
                    rgb?.let { conditionedAccent(Color(it)) }
                }
                if (color != null) {
                    accent = color
                    break
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Try the local backup, then retain the preset fallback.
            }
        }
    }
    return accent
}
