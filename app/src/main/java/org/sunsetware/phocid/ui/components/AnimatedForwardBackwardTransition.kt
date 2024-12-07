package org.sunsetware.phocid.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.Layout
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import org.sunsetware.phocid.ui.theme.emphasizedEnter
import org.sunsetware.phocid.ui.theme.emphasizedExit

const val FORWARD_BACKWARD_SLIDE_FRACTION = 0.5f

@Stable
fun forwardBackwardTransitionAlpha(position: Float, index: Int): Float {
    return (1 - (position - index).absoluteValue * 2).coerceAtLeast(0f)
}

@Composable
inline fun <T> AnimatedForwardBackwardTransition(
    stack: List<T>,
    modifier: Modifier = Modifier,
    slide: Boolean = true,
    keepRoot: Boolean = true,
    crossinline content: @Composable (T?) -> Unit,
) {
    val position = remember { Animatable(0f) }
    var lastAndCurrentStack by remember { mutableStateOf(stack to stack) }
    var largerStack by remember { mutableStateOf(stack) }

    LaunchedEffect(stack) {
        lastAndCurrentStack = lastAndCurrentStack.second to stack
        largerStack =
            if (lastAndCurrentStack.first.size > lastAndCurrentStack.second.size)
                lastAndCurrentStack.first
            else lastAndCurrentStack.second
    }

    LaunchedEffect(lastAndCurrentStack.second.size) {
        val size = lastAndCurrentStack.second.size
        if (position.value < size) {
            position.animateTo(size.toFloat(), emphasizedEnter())
        } else {
            position.animateTo(size.toFloat(), emphasizedExit())
        }
    }

    Layout(
        modifier = modifier,
        content = {
            val rootAlpha = forwardBackwardTransitionAlpha(position.value, 0)
            if (keepRoot || rootAlpha > 0) {
                Box(modifier = Modifier.alpha(rootAlpha)) { content(null) }
            } else {
                Box {}
            }

            largerStack.forEachIndexed { index, item ->
                val alpha = forwardBackwardTransitionAlpha(position.value, index + 1)
                if (alpha > 0) {
                    Box(modifier = Modifier.alpha(alpha)) { content(item) }
                } else {
                    Box {}
                }
            }
        },
    ) { measurables, constraints ->
        val placeables = measurables.map { measurable -> measurable.measure(constraints) }

        layout(constraints.maxWidth, constraints.maxHeight) {
            placeables.forEachIndexed { index, placeable ->
                placeable.placeRelative(
                    if (slide)
                        ((index - position.value).coerceIn(-1f, 1f) *
                                constraints.maxWidth *
                                FORWARD_BACKWARD_SLIDE_FRACTION)
                            .roundToInt()
                    else 0,
                    0,
                )
            }
        }
    }
}
