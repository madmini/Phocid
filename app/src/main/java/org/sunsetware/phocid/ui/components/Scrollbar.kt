@file:OptIn(ExperimentalComposeUiApi::class)

package org.sunsetware.phocid.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.roundToInt
import org.sunsetware.phocid.ui.theme.EXIT_DURATION
import org.sunsetware.phocid.ui.theme.emphasized

val SCROLLBAR_DEFAULT_WIDTH = 4.dp
val SCROLLBAR_DEFAULT_COLOR
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.tertiary
val SCROLLBAR_MIN_SIZE = 48.dp
val SCROLLBAR_INTERACTIVE_WIDTH = 24.dp
const val SCROLLBAR_MIN_SIZE_MAX_FRACTION = 0.5f
val scrollbarEnter = emphasized<Float>(50)
val scrollbarExit = emphasized<Float>(EXIT_DURATION, 1000)

fun adjustThumbOffsets(
    thumbStart: Float,
    thumbEnd: Float,
    height: Float,
    density: Float,
): Pair<Float, Float> {
    val minSize =
        (SCROLLBAR_MIN_SIZE.value * density / height).coerceIn(0f, SCROLLBAR_MIN_SIZE_MAX_FRACTION)

    var start = thumbStart.coerceIn(0f, 1f)
    var end = thumbEnd.coerceIn(0f, 1f)
    if (end - start < minSize) {
        val progress = start / (1 - (end - start))
        start = progress * (1 - minSize)
        end = start + minSize
    }

    return start to end
}

fun DrawScope.drawThumb(
    width: Dp,
    color: Color,
    alpha: () -> Float,
    thumbStart: () -> Float,
    thumbEnd: () -> Float,
) {
    val widthPx = width.toPx()
    val cornerRadius = widthPx / 2
    val (start, end) = adjustThumbOffsets(thumbStart(), thumbEnd(), this.size.height, density)

    drawRoundRect(
        color = color,
        topLeft =
            Offset(
                if (this.layoutDirection == LayoutDirection.Ltr) this.size.width - widthPx else 0f,
                start * this.size.height,
            ),
        size = Size(width.toPx(), (end - start) * this.size.height),
        cornerRadius = CornerRadius(cornerRadius, cornerRadius),
        alpha = alpha(),
    )
}

@Composable
inline fun ScrollbarThumb(
    width: Dp,
    color: Color,
    crossinline alpha: () -> Float,
    crossinline thumbStart: () -> Float,
    crossinline thumbEnd: () -> Float,
    crossinline totalItemsCount: () -> Int,
    crossinline onRequestScrollToItem: (Int) -> Unit,
    crossinline onSetIsThumbDragging: (Boolean) -> Unit,
    crossinline content: @Composable () -> Unit,
) {
    val layoutDirection = LocalLayoutDirection.current

    Box(
        modifier =
            Modifier.drawWithContent {
                    drawContent()
                    drawThumb(width, color, { alpha() }, { thumbStart() }, { thumbEnd() })
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(pass = PointerEventPass.Initial)
                        val (start, end) =
                            adjustThumbOffsets(
                                thumbStart(),
                                thumbEnd(),
                                size.height.toFloat(),
                                density,
                            )
                        val isXInRange = {
                            if (layoutDirection == LayoutDirection.Ltr) {
                                down.position.x >= size.width - SCROLLBAR_INTERACTIVE_WIDTH.toPx()
                            } else {
                                down.position.x <= SCROLLBAR_INTERACTIVE_WIDTH.toPx()
                            }
                        }
                        val isYInRange = {
                            val relativeY = down.position.y / size.height
                            relativeY >= start && relativeY <= end
                        }
                        if (alpha() > 0 && isXInRange() && isYInRange()) {
                            onSetIsThumbDragging(true)
                            val yOffset = start * size.height - down.position.y
                            down.consume()

                            outer@ while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                for (change in event.changes) {
                                    change.consume()
                                    if (!change.pressed) break@outer

                                    val totalItemsCount = totalItemsCount()
                                    onRequestScrollToItem(
                                        ((change.position.y + yOffset) / size.height *
                                                totalItemsCount)
                                            .roundToInt()
                                            .coerceAtMost(totalItemsCount - 1)
                                            .coerceAtLeast(0)
                                    )
                                }
                            }
                        }
                        onSetIsThumbDragging(false)
                    }
                }
    ) {
        content()
    }
}

@Composable
fun Scrollbar(
    state: LazyListState,
    width: Dp = SCROLLBAR_DEFAULT_WIDTH,
    color: Color = SCROLLBAR_DEFAULT_COLOR,
    content: @Composable () -> Unit,
) {
    val thumbStart by
        remember(state) {
            derivedStateOf {
                state.layoutInfo.visibleItemsInfo.firstOrNull()?.let {
                    (it.index - it.offset.toFloat() / it.size) / state.layoutInfo.totalItemsCount
                } ?: 0f
            }
        }
    val thumbEnd by
        remember(state) {
            derivedStateOf {
                state.layoutInfo.visibleItemsInfo.lastOrNull()?.let {
                    (it.index +
                        (state.layoutInfo.viewportEndOffset - it.offset).toFloat() / it.size) /
                        state.layoutInfo.totalItemsCount
                } ?: 1f
            }
        }
    val totalItemsCount by remember(state) { derivedStateOf { state.layoutInfo.totalItemsCount } }
    var isScrollbarDragging by remember { mutableStateOf(false) }
    val isScrollInProgress by
        remember(state) {
            derivedStateOf {
                (thumbStart > 0 || thumbEnd < 1) &&
                    (state.isScrollInProgress || isScrollbarDragging)
            }
        }
    val alpha =
        animateFloatAsState(
            if (isScrollInProgress) 1f else 0f,
            if (isScrollInProgress) scrollbarEnter else scrollbarExit,
        )

    ScrollbarThumb(
        width,
        color,
        { alpha.value },
        { thumbStart },
        { thumbEnd },
        { totalItemsCount },
        { state.requestScrollToItem(it) },
        { isScrollbarDragging = it },
        content,
    )
}

@Composable
fun Scrollbar(
    state: LazyGridState,
    width: Dp = SCROLLBAR_DEFAULT_WIDTH,
    color: Color = SCROLLBAR_DEFAULT_COLOR,
    content: @Composable () -> Unit,
) {
    val itemsPerRow by
        remember(state) {
            derivedStateOf {
                state.layoutInfo.visibleItemsInfo
                    .firstOrNull()
                    ?.size
                    ?.width
                    ?.let { state.layoutInfo.viewportSize.width.toFloat() / it }
                    ?.roundToInt() ?: 1
            }
        }
    val thumbStart by
        remember(state) {
            derivedStateOf {
                state.layoutInfo.visibleItemsInfo.firstOrNull()?.let {
                    (ceil(it.index.toFloat() / itemsPerRow) -
                        it.offset.y.toFloat() / it.size.height) /
                        ceil(state.layoutInfo.totalItemsCount.toFloat() / itemsPerRow)
                } ?: 0f
            }
        }
    val thumbEnd by
        remember(state) {
            derivedStateOf {
                state.layoutInfo.visibleItemsInfo.lastOrNull()?.let {
                    (ceil(it.index.toFloat() / itemsPerRow) +
                        (state.layoutInfo.viewportEndOffset - it.offset.y).toFloat() /
                            it.size.height) /
                        ceil(state.layoutInfo.totalItemsCount.toFloat() / itemsPerRow)
                } ?: 1f
            }
        }
    val totalItemsCount by remember(state) { derivedStateOf { state.layoutInfo.totalItemsCount } }
    var isScrollbarDragging by remember { mutableStateOf(false) }
    val isScrollInProgress by
        remember(state) {
            derivedStateOf {
                (thumbStart > 0 || thumbEnd < 1) &&
                    (state.isScrollInProgress || isScrollbarDragging)
            }
        }
    val alpha =
        animateFloatAsState(
            if (isScrollInProgress) 1f else 0f,
            if (isScrollInProgress) scrollbarEnter else scrollbarExit,
        )

    ScrollbarThumb(
        width,
        color,
        { alpha.value },
        { thumbStart },
        { thumbEnd },
        { totalItemsCount },
        { state.requestScrollToItem(it) },
        { isScrollbarDragging = it },
        content,
    )
}
