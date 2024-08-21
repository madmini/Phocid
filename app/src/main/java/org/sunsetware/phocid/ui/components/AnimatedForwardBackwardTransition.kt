package org.sunsetware.phocid.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import org.sunsetware.phocid.ui.theme.emphasizedEnter
import org.sunsetware.phocid.ui.theme.emphasizedExit

val ForwardSpec =
    fadeIn(emphasizedEnter()) + slideInHorizontally(emphasizedEnter()) { it / 2 } togetherWith
        fadeOut(emphasizedEnter()) + slideOutHorizontally(emphasizedEnter()) { -it / 2 }
val BackwardSpec =
    fadeIn(emphasizedExit()) + slideInHorizontally(emphasizedExit()) { -it / 2 } togetherWith
        fadeOut(emphasizedExit()) + slideOutHorizontally(emphasizedExit()) { it / 2 }
val JumpCutSpec = (fadeIn(tween(0), 1f) togetherWith fadeOut(tween(0)))

val ForwardFadeSpec = fadeIn(emphasizedEnter()) togetherWith fadeOut(emphasizedEnter())
val BackwardFadeSpec = fadeIn(emphasizedExit()) togetherWith fadeOut(emphasizedExit())

@Composable
inline fun <T> AnimatedForwardBackwardTransition(
    stack: List<T>,
    modifier: Modifier = Modifier,
    forward: ContentTransform = ForwardSpec,
    backward: ContentTransform = BackwardSpec,
    crossinline content: @Composable (T?) -> Unit,
) {
    var lastSize by remember { mutableIntStateOf(0) }
    val transitionSpec =
        remember(stack) {
            val transitionSpec =
                when {
                    stack.size > lastSize -> forward
                    stack.size < lastSize -> backward
                    else -> JumpCutSpec
                }
            lastSize = stack.size
            transitionSpec
        }
    AnimatedContent(
        targetState = stack.lastOrNull(),
        transitionSpec = { transitionSpec },
        modifier = modifier,
    ) {
        content(it)
    }
}
