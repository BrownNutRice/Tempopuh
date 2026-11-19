package me.avinas.tempo.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import me.avinas.tempo.ui.appearance.AppearancePaletteResolver
import me.avinas.tempo.ui.appearance.AppearancePreset
import me.avinas.tempo.ui.theme.LocalTempoPalette

/** The app's opaque backdrop. Cache the gradient for the current palette and bounds. */
@Composable
fun DeepOceanBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = LocalTempoPalette.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawWithCache {
                val middle = if (colors == AppearancePaletteResolver.forPreset(AppearancePreset.ABYSS)) {
                    Color(0xFF0C1010)
                } else colors.surface
                val brush = Brush.verticalGradient(listOf(colors.topBackground, middle, colors.surfaceSunken))
                onDrawBehind { drawRect(brush) }
            }
    ) {
        content()
    }
}
