package org.sunsetware.phocid.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.sunsetware.phocid.ui.theme.emphasized
import org.sunsetware.phocid.ui.theme.emphasizedExit

val SCROLLBAR_DEFAULT_WIDTH = 4.dp
val SCROLLBAR_DEFAULT_PADDING = 4.dp
val SCROLLBAR_DEFAULT_COLOR
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.tertiary
val SCROLLBAR_MIN_SIZE = 48.dp
val SCROLLBAR_MIN_INTERACTIVE_WIDTH = 24.dp
const val SCROLLBAR_MIN_SIZE_MAX_FRACTION = 0.5f
const val SCROLLBAR_FADE_TIMEOUT_MILLISECONDS = 1000
val scrollbarEnter = emphasized<Float>(50)
val scrollbarExit = emphasizedExit<Float>()

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
inline fun BoxScope.ScrollbarThumb(
    width: Dp,
    padding: Dp,
    color: Color,
    enabled: Boolean,
    crossinline alpha: () -> Float,
    crossinline thumbStart: () -> Float,
    crossinline thumbEnd: () -> Float,
    crossinline firstVisibleItem: () -> Float,
    crossinline totalItemsCount: () -> Int,
    crossinline onRequestScrollToItem: (Int) -> Unit,
) {
    var dragInitialFirstVisibleItem by remember { mutableStateOf(null as Float?) }
    var dragTotal by remember { mutableFloatStateOf(0f) }

    if (enabled) {
        Box(
            modifier =
                Modifier.align(Alignment.TopEnd)
                    .padding(vertical = padding)
                    .width(max(width, SCROLLBAR_MIN_INTERACTIVE_WIDTH))
                    .fillMaxHeight()
                    .drawBehind {
                        drawThumb(width, color, { alpha() }, { thumbStart() }, { thumbEnd() })
                    }
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragStart = { offset ->
                                val (start, end) =
                                    adjustThumbOffsets(
                                        thumbStart(),
                                        thumbEnd(),
                                        size.height.toFloat(),
                                        density,
                                    )
                                val relativeY = offset.y / size.height
                                if (relativeY >= start && relativeY <= end)
                                    dragInitialFirstVisibleItem = firstVisibleItem()
                                dragTotal = 0f
                            },
                            onDragEnd = { dragInitialFirstVisibleItem = null },
                            onDragCancel = { dragInitialFirstVisibleItem = null },
                            onVerticalDrag = { _, delta ->
                                if (dragInitialFirstVisibleItem != null) {
                                    val totalItemsCount = totalItemsCount()
                                    dragTotal += delta
                                    onRequestScrollToItem(
                                        (dragInitialFirstVisibleItem!! +
                                                dragTotal / size.height * totalItemsCount)
                                            .roundToInt()
                                            .coerceAtMost(totalItemsCount - 1)
                                            .coerceAtLeast(0)
                                    )
                                }
                            },
                        )
                    }
        )
    }
}

@Composable
inline fun Scrollbar(
    state: LazyListState,
    width: Dp = SCROLLBAR_DEFAULT_WIDTH,
    padding: Dp = SCROLLBAR_DEFAULT_PADDING,
    color: Color = SCROLLBAR_DEFAULT_COLOR,
    crossinline content: @Composable () -> Unit,
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
    val firstVisibleItem by
        remember(state) {
            derivedStateOf {
                state.layoutInfo.visibleItemsInfo.firstOrNull()?.let {
                    it.index - it.offset.toFloat() / it.size
                } ?: 0f
            }
        }
    val totalItemsCount by remember(state) { derivedStateOf { state.layoutInfo.totalItemsCount } }

    var fadeTimeout by remember { mutableIntStateOf(0) }
    var lastAnimatedEnter by remember { mutableStateOf(false) }
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(state) {
        snapshotFlow { firstVisibleItem }
            .collect { fadeTimeout = SCROLLBAR_FADE_TIMEOUT_MILLISECONDS }
    }
    LaunchedEffect(Unit) {
        while (isActive) {
            fadeTimeout = (fadeTimeout - 42).coerceAtLeast(0)
            if (fadeTimeout > 0 && !lastAnimatedEnter) {
                launch { alpha.animateTo(1f, scrollbarEnter) }
                lastAnimatedEnter = true
            } else if (fadeTimeout == 0 && lastAnimatedEnter) {
                launch { alpha.animateTo(0f, scrollbarExit) }
                lastAnimatedEnter = false
            }
            delay(42.milliseconds) // 24 fps
        }
    }

    val enabled by remember {
        derivedStateOf { alpha.value > 0 && (thumbStart > 0 || thumbEnd < 1) }
    }

    Box {
        content()
        ScrollbarThumb(
            width,
            padding,
            color,
            enabled,
            { alpha.value },
            { thumbStart },
            { thumbEnd },
            { firstVisibleItem },
            { totalItemsCount },
            {
                fadeTimeout = SCROLLBAR_FADE_TIMEOUT_MILLISECONDS
                state.requestScrollToItem(it)
            },
        )
    }
}

@Composable
inline fun Scrollbar(
    state: LazyGridState,
    width: Dp = SCROLLBAR_DEFAULT_WIDTH,
    padding: Dp = SCROLLBAR_DEFAULT_PADDING,
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
    val firstVisibleItem by
        remember(state) {
            derivedStateOf {
                state.layoutInfo.visibleItemsInfo.firstOrNull()?.let {
                    it.index - it.offset.y.toFloat() / it.size.height
                } ?: 0f
            }
        }
    val totalItemsCount by remember(state) { derivedStateOf { state.layoutInfo.totalItemsCount } }

    var fadeTimeout by remember { mutableIntStateOf(0) }
    var lastAnimatedEnter by remember { mutableStateOf(false) }
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(state) {
        snapshotFlow { firstVisibleItem }
            .collect { fadeTimeout = SCROLLBAR_FADE_TIMEOUT_MILLISECONDS }
    }
    LaunchedEffect(Unit) {
        while (isActive) {
            fadeTimeout = (fadeTimeout - 42).coerceAtLeast(0)
            if (fadeTimeout > 0 && !lastAnimatedEnter) {
                launch { alpha.animateTo(1f, scrollbarEnter) }
                lastAnimatedEnter = true
            } else if (fadeTimeout == 0 && lastAnimatedEnter) {
                launch { alpha.animateTo(0f, scrollbarExit) }
                lastAnimatedEnter = false
            }
            delay(42.milliseconds) // 24 fps
        }
    }
    val enabled by remember {
        derivedStateOf { alpha.value > 0 && (thumbStart > 0 || thumbEnd < 1) }
    }

    Box {
        content()
        ScrollbarThumb(
            width,
            padding,
            color,
            enabled,
            { alpha.value },
            { thumbStart },
            { thumbEnd },
            { firstVisibleItem },
            { totalItemsCount },
            {
                fadeTimeout = SCROLLBAR_FADE_TIMEOUT_MILLISECONDS
                state.requestScrollToItem(it)
            },
        )
    }
}
