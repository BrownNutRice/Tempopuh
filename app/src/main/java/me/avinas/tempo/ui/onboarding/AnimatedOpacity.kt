package me.avinas.tempo.ui.onboarding

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import me.avinas.tempo.ui.theme.rememberReducedMotion

@Composable
fun AnimatedOpacity(
    delay: Int,
    visible: Boolean,
    content: @Composable () -> Unit
) {
    val reducedMotion = rememberReducedMotion()
    val alphaTarget = if (visible) 1f else 0f
    val alpha by animateFloatAsState(
        targetValue = alphaTarget,
        animationSpec = if (reducedMotion) snap() else tween(500, delayMillis = delay),
        label = "alpha"
    )

    // ponytail: rise + fade reads as arrival, alpha-only reads as ghost.
    // One shared language: 24px drift, same as transitions + Welcome.
    Box(
        modifier = Modifier
            .alpha(if (reducedMotion) alphaTarget else alpha)
            .graphicsLayer { translationY = if (reducedMotion) 0f else (1f - alpha) * 24f }
    ) {
        content()
    }
}
