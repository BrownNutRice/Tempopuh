package me.avinas.tempo.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import me.avinas.tempo.ui.appearance.AppearanceBackdrop
import me.avinas.tempo.ui.appearance.AppearancePalette
import me.avinas.tempo.ui.appearance.AppearancePaletteResolver
import me.avinas.tempo.ui.appearance.AppearancePreset
import me.avinas.tempo.ui.theme.LocalTempoAppearance
import me.avinas.tempo.ui.theme.LocalTempoPalette

/** The app's opaque backdrop. Cache the gradient for the current palette and bounds. */
@Composable
fun DeepOceanBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = LocalTempoPalette.current
    val backdrop = LocalTempoAppearance.current.backdrop
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawWithCache {
                val brush = appearanceBackdropBrush(colors, backdrop)
                onDrawBehind { drawRect(brush) }
            }
    ) {
        content()
    }
}

/** Used by the app and the studio preview so the chosen finish renders the same way. */
internal fun appearanceBackdropBrush(colors: AppearancePalette, backdrop: AppearanceBackdrop): Brush =
    when (backdrop) {
        AppearanceBackdrop.FLAT -> SolidColor(colors.background)
        AppearanceBackdrop.WASH -> Brush.verticalGradient(
            listOf(lerp(colors.topBackground, colors.accent, 0.13f), colors.background, colors.surfaceSunken),
        )
        AppearanceBackdrop.GRADIENT -> {
            val middle = if (colors.background == Color.Black) {
                Color.Black
            } else if (colors == AppearancePaletteResolver.forPreset(AppearancePreset.ABYSS)) {
                Color(0xFF0C1010)
            } else colors.surface
            Brush.verticalGradient(listOf(colors.topBackground, middle, colors.surfaceSunken))
        }
    }
