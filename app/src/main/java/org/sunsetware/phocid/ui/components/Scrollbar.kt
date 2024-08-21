package org.sunsetware.phocid.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.roundToInt
import org.sunsetware.phocid.ui.theme.emphasized

val SCROLLBAR_DEFAULT_WIDTH = 4.dp
val SCROLLBAR_DEFAULT_PADDING = 4.dp
val SCROLLBAR_DEFAULT_COLOR
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.tertiary
val SCROLLBAR_MIN_SIZE = 48.dp
const val SCROLLBAR_MIN_SIZE_MAX_FRACTION = 0.5f

@Composable
inline fun Modifier.scrollbar(
    crossinline thumbStart: () -> Float,
    crossinline thumbEnd: () -> Float,
    isScrollInProgress: Boolean,
    width: Dp = SCROLLBAR_DEFAULT_WIDTH,
    padding: Dp = SCROLLBAR_DEFAULT_PADDING,
    color: Color = SCROLLBAR_DEFAULT_COLOR,
): Modifier {
    val alpha by
        animateFloatAsState(
            targetValue = if (isScrollInProgress) 1f else 0f,
            animationSpec =
                if (isScrollInProgress) emphasized(durationMillis = 100)
                else emphasized(durationMillis = 500, delayMillis = 1000),
        )

    return drawWithContent {
        val widthPx = width.toPx()
        val paddingPx = padding.toPx()
        val innerHeight = (this.size.height - 2 * paddingPx)
        val cornerRadius = widthPx / 2
        val minSize =
            (SCROLLBAR_MIN_SIZE.toPx() / innerHeight).coerceIn(0f, SCROLLBAR_MIN_SIZE_MAX_FRACTION)

        var start = thumbStart().coerceIn(0f, 1f)
        var end = thumbEnd().coerceIn(0f, 1f)
        if (end - start < minSize) {
            val progress = start / (1 - (end - start))
            start = progress * (1 - minSize)
            end = start + minSize
        }

        drawContent()
        if (start != 0f || end != 1f) {
            drawRoundRect(
                color = color,
                topLeft = Offset(this.size.width - widthPx, start * innerHeight + paddingPx),
                size = Size(width.toPx(), (end - start) * innerHeight),
                cornerRadius = CornerRadius(cornerRadius, cornerRadius),
                alpha = alpha,
            )
        }
    }
}

@Composable
fun Modifier.scrollbar(
    state: LazyListState,
    width: Dp = SCROLLBAR_DEFAULT_WIDTH,
    padding: Dp = SCROLLBAR_DEFAULT_PADDING,
    color: Color = SCROLLBAR_DEFAULT_COLOR,
): Modifier {
    val isScrollInProgress by remember { derivedStateOf { state.isScrollInProgress } }
    return scrollbar(
        {
            state.layoutInfo.visibleItemsInfo.firstOrNull()?.let {
                (it.index - it.offset.toFloat() / it.size) / state.layoutInfo.totalItemsCount
            } ?: 0f
        },
        {
            state.layoutInfo.visibleItemsInfo.lastOrNull()?.let {
                (it.index + (state.layoutInfo.viewportEndOffset - it.offset).toFloat() / it.size) /
                    state.layoutInfo.totalItemsCount
            } ?: 1f
        },
        isScrollInProgress,
        width,
        padding,
        color,
    )
}

@Composable
fun Modifier.scrollbar(
    state: LazyGridState,
    width: Dp = SCROLLBAR_DEFAULT_WIDTH,
    padding: Dp = SCROLLBAR_DEFAULT_PADDING,
    color: Color = SCROLLBAR_DEFAULT_COLOR,
): Modifier {
    val isScrollInProgress by remember { derivedStateOf { state.isScrollInProgress } }
    val itemsPerRow by remember {
        derivedStateOf {
            state.layoutInfo.visibleItemsInfo
                .firstOrNull()
                ?.size
                ?.width
                ?.let { state.layoutInfo.viewportSize.width.toFloat() / it }
                ?.roundToInt() ?: 1
        }
    }
    return scrollbar(
        {
            state.layoutInfo.visibleItemsInfo.firstOrNull()?.let {
                (ceil(it.index.toFloat() / itemsPerRow) - it.offset.y.toFloat() / it.size.height) /
                    ceil(state.layoutInfo.totalItemsCount.toFloat() / itemsPerRow)
            } ?: 0f
        },
        {
            state.layoutInfo.visibleItemsInfo.lastOrNull()?.let {
                (ceil(it.index.toFloat() / itemsPerRow) +
                    (state.layoutInfo.viewportEndOffset - it.offset.y).toFloat() / it.size.height) /
                    ceil(state.layoutInfo.totalItemsCount.toFloat() / itemsPerRow)
            } ?: 1f
        },
        isScrollInProgress,
        width,
        padding,
        color,
    )
}
