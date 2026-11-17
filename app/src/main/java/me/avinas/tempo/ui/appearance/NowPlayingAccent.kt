package me.avinas.tempo.ui.appearance

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil3.BitmapImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.CachePolicy
import coil3.request.allowHardware
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import me.avinas.tempo.ui.theme.LocalTempoAppearance
import me.avinas.tempo.ui.theme.LocalTempoPalette

/** Cache-only for remote covers; local provider artwork is allowed. Never polls playback. */
@Composable
fun rememberNowPlayingAccent(identity: String?, artwork: String?): Color {
    val palette = LocalTempoPalette.current
    val enabled = LocalTempoAppearance.current.accentSource == AppearanceAccentSource.ARTWORK &&
        LocalAppearanceRuntimePolicy.current.allowArtworkAccent
    val context = LocalContext.current
    var accent by remember(identity, artwork, enabled, palette) { mutableStateOf(palette.accent) }
    LaunchedEffect(identity, artwork, enabled, palette) {
        if (!enabled || identity == null || artwork.isNullOrBlank()) return@LaunchedEffect
        try {
            val image = context.imageLoader.execute(ImageRequest.Builder(context)
                .data(artwork).size(64, 64).allowHardware(false)
                .networkCachePolicy(CachePolicy.DISABLED).build()).image
            val bitmap = (image as? BitmapImage)?.bitmap ?: return@LaunchedEffect
            val candidate = withContext(Dispatchers.Default) {
                val extracted = Palette.from(bitmap).generate()
                ensureActive()
                (extracted.vibrantSwatch ?: extracted.dominantSwatch)?.rgb?.let { Color(it) }
            }
            if (candidate != null) accent = AppearancePaletteResolver.withAccent(palette, candidate).accent
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Keep the preset accent when no cached, usable cover exists.
        }
    }
    return accent
}
