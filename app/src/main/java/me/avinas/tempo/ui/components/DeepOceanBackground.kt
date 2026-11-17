package me.avinas.tempo.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import me.avinas.tempo.ui.theme.LocalTempoPalette

/* DeepOcean — neutral depth.
 * Near-black matte with a single soft value vignette + top sheen.
 * No teal/mint blob. Teal lives in cards as accent, not in the sky.
 *
 * All four gradients paint in ONE drawBehind pass — one node, no stacked
 * full-screen children (it sits under every screen, including the art
 * atmosphere, so overdraw here is paid app-wide).
 */
@Composable
fun DeepOceanBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = LocalTempoPalette.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                val height = size.height
                drawRect(colors.background)
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            colors.topBackground,
                            if (colors == me.avinas.tempo.ui.appearance.AppearancePaletteResolver.forPreset(me.avinas.tempo.ui.appearance.AppearancePreset.ABYSS))
                                Color(0xFF0C1010) else colors.surface,
                            colors.surfaceSunken,
                        ),
                        startY = 0f,
                        endY = height,
                    )
                )
            }
    ) {
        content()
    }
}
