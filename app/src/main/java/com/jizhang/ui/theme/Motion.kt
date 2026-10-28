package com.jizhang.ui.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

/**
 * The project's only animation specs. Rule: interactions use [layout], never tween().
 *
 * Two exceptions, both because the rule can't apply: colours and alpha use [fade] (a bouncy
 * spring overshoots past the target colour and flickers), and endless ambient loops use
 * infiniteRepeatable(tween), since infiniteRepeatable only accepts duration-based specs.
 */
object Motion {
    fun <T> layout(): FiniteAnimationSpec<T> =
        spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)

    fun <T> fade(): FiniteAnimationSpec<T> =
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

    /** Slow, calm, no overshoot: the circle that reveals a new theme, and soft glows. */
    fun <T> theme(): FiniteAnimationSpec<T> =
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessVeryLow)

    val offset: FiniteAnimationSpec<IntOffset> =
        spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow, IntOffset.VisibilityThreshold)

    val size: FiniteAnimationSpec<IntSize> =
        spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow, IntSize.VisibilityThreshold)

    val bounds: FiniteAnimationSpec<Rect> =
        spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow, Rect.VisibilityThreshold)
}
