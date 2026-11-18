package me.avinas.tempo.ui.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import me.avinas.tempo.ui.theme.LocalTempoPalette

internal fun profileContrast(first: Color, second: Color): Float {
    val a = first.luminance()
    val b = second.luminance()
    return (maxOf(a, b) + 0.05f) / (minOf(a, b) + 0.05f)
}

internal fun readableProfileColor(candidate: Color, surface: Color, minimumRatio: Float): Color {
    val opaque = candidate.compositeOver(surface)
    if (profileContrast(opaque, surface) >= minimumRatio) return opaque
    val target = if (profileContrast(Color.Black, surface) >= profileContrast(Color.White, surface)) {
        Color.Black
    } else {
        Color.White
    }
    for (step in 1..20) {
        val adjusted = lerp(opaque, target, step / 20f)
        if (profileContrast(adjusted, surface) >= minimumRatio) return adjusted
    }
    return target
}

@Composable
internal fun profileTextColor(candidate: Color, surface: Color? = null): Color =
    profileForeground(candidate, surface, minimumRatio = 4.5f)

@Composable
internal fun profileIndicatorColor(candidate: Color, surface: Color? = null): Color =
    profileForeground(candidate, surface, minimumRatio = 3f)

@Composable
private fun profileForeground(candidate: Color, surface: Color?, minimumRatio: Float): Color {
    val palette = LocalTempoPalette.current
    val background = surface ?: if (palette.isDark) palette.elevatedSurface else palette.surfaceSunken
    return remember(candidate, background, minimumRatio) {
        readableProfileColor(candidate, background, minimumRatio)
    }
}
