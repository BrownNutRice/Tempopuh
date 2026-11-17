package me.avinas.tempo.ui.theme

import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.InfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

/** Repeating decoration has no animation clock when effective reduced motion is active. */
class MotionAwareInfiniteTransition internal constructor(
    private val transition: InfiniteTransition?,
) {
    @Composable
    fun animateFloat(
        initialValue: Float,
        targetValue: Float,
        animationSpec: InfiniteRepeatableSpec<Float>,
        label: String = "FloatAnimation",
    ): State<Float> = if (transition == null) {
        rememberUpdatedState(initialValue)
    } else {
        transition.animateFloat(initialValue, targetValue, animationSpec, label)
    }
}

@Composable
fun rememberMotionAwareInfiniteTransition(label: String = "InfiniteTransition"): MotionAwareInfiniteTransition {
    val reducedMotion = rememberReducedMotion()
    // Removing this branch also disposes an existing loop when the policy changes.
    val transition = if (reducedMotion) null else rememberInfiniteTransition(label = label)
    return remember(transition) { MotionAwareInfiniteTransition(transition) }
}
