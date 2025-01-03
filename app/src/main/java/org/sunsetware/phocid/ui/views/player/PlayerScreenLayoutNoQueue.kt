package org.sunsetware.phocid.ui.views.player

import androidx.compose.runtime.Immutable
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import org.sunsetware.phocid.utils.BinaryDragState

private val queueHeaderHeight = 56.dp

@Immutable
object PlayerScreenLayoutNoQueue : PlayerScreenLayout() {
    override fun Placeable.PlacementScope.place(
        topBarStandalone: Measurable,
        topBarOverlay: Measurable,
        artwork: Measurable,
        lyricsOverlay: Measurable,
        controls: Measurable,
        queue: Measurable,
        scrim: Measurable,
        width: Int,
        height: Int,
        density: Density,
        queueDragState: BinaryDragState,
    ) {
        with(
            when (aspectRatio(width, height, 1.5f)) {
                AspectRatio.LANDSCAPE -> PlayerScreenLayoutNoQueueLandscape
                AspectRatio.SQUARE -> PlayerScreenLayoutNoQueueSquare
                AspectRatio.PORTRAIT -> PlayerScreenLayoutNoQueuePortrait
            }
        ) {
            place(
                topBarStandalone,
                topBarOverlay,
                artwork,
                lyricsOverlay,
                controls,
                queue,
                scrim,
                width,
                height,
                density,
                queueDragState,
            )
        }
    }
}

@Immutable
object PlayerScreenLayoutNoQueuePortrait : PlayerScreenLayout() {
    override fun Placeable.PlacementScope.place(
        topBarStandalone: Measurable,
        topBarOverlay: Measurable,
        artwork: Measurable,
        lyricsOverlay: Measurable,
        controls: Measurable,
        queue: Measurable,
        scrim: Measurable,
        width: Int,
        height: Int,
        density: Density,
        queueDragState: BinaryDragState,
    ) {
        val offset = (queueDragState.length * queueDragState.position).roundToInt()

        val artworkHeight = width
        artwork
            .measure(Constraints(maxWidth = width, maxHeight = artworkHeight))
            .placeRelative(0, 0)

        lyricsOverlay
            .measure(Constraints(maxWidth = width, maxHeight = artworkHeight))
            .placeRelative(0, 0)
        topBarOverlay
            .measure(Constraints(maxWidth = width, maxHeight = artworkHeight))
            .placeRelative(0, 0)

        val queueHeaderHeightPx = with(density) { queueHeaderHeight.roundToPx() }
        controls
            .measure(
                Constraints(
                    maxWidth = width,
                    maxHeight = (height - artworkHeight - queueHeaderHeightPx).coerceAtLeast(0),
                )
            )
            .placeRelative(0, artworkHeight)

        scrim.measure(Constraints(maxWidth = width, maxHeight = height)).placeRelative(0, 0)

        queue
            .measure(Constraints(maxWidth = width, maxHeight = queueHeaderHeightPx + offset))
            .placeRelative(0, height - queueHeaderHeightPx - offset)

        queueDragState.length = (height - queueHeaderHeightPx).coerceAtLeast(0).toFloat()
    }
}

@Immutable
object PlayerScreenLayoutNoQueueLandscape : PlayerScreenLayout() {
    override fun Placeable.PlacementScope.place(
        topBarStandalone: Measurable,
        topBarOverlay: Measurable,
        artwork: Measurable,
        lyricsOverlay: Measurable,
        controls: Measurable,
        queue: Measurable,
        scrim: Measurable,
        width: Int,
        height: Int,
        density: Density,
        queueDragState: BinaryDragState,
    ) {
        val offset = (queueDragState.length * queueDragState.position).roundToInt()

        val artworkWidth = height
        artwork
            .measure(Constraints(maxWidth = artworkWidth, maxHeight = height))
            .placeRelative(0, 0)

        lyricsOverlay
            .measure(Constraints(maxWidth = artworkWidth, maxHeight = height))
            .placeRelative(0, 0)
        topBarOverlay
            .measure(Constraints(maxWidth = artworkWidth, maxHeight = height))
            .placeRelative(0, 0)

        val queueHeaderHeightPx = with(density) { queueHeaderHeight.roundToPx() }
        controls
            .measure(
                Constraints(
                    maxWidth = width - artworkWidth,
                    maxHeight = (height - queueHeaderHeightPx).coerceAtLeast(0),
                )
            )
            .placeRelative(artworkWidth, 0)
        scrim
            .measure(Constraints(maxWidth = width - artworkWidth, maxHeight = height))
            .placeRelative(artworkWidth, 0)

        queue
            .measure(
                Constraints(
                    maxWidth = width - artworkWidth,
                    maxHeight = queueHeaderHeightPx + offset,
                )
            )
            .placeRelative(artworkWidth, height - queueHeaderHeightPx - offset)

        queueDragState.length = (height - queueHeaderHeightPx).coerceAtLeast(0).toFloat()
    }
}

@Immutable
object PlayerScreenLayoutNoQueueSquare : PlayerScreenLayout() {
    override fun Placeable.PlacementScope.place(
        topBarStandalone: Measurable,
        topBarOverlay: Measurable,
        artwork: Measurable,
        lyricsOverlay: Measurable,
        controls: Measurable,
        queue: Measurable,
        scrim: Measurable,
        width: Int,
        height: Int,
        density: Density,
        queueDragState: BinaryDragState,
    ) {
        val offset = (queueDragState.length * queueDragState.position).roundToInt()

        val topBarPlaceable =
            topBarStandalone.measure(Constraints(maxWidth = width, maxHeight = height))
        topBarPlaceable.placeRelative(0, 0)

        val queueHeaderHeightPx = with(density) { queueHeaderHeight.roundToPx() }
        controls
            .measure(
                Constraints(
                    maxWidth = width,
                    maxHeight =
                        (height - topBarPlaceable.height - queueHeaderHeightPx).coerceAtLeast(0),
                )
            )
            .placeRelative(0, topBarPlaceable.height)

        scrim.measure(Constraints(maxWidth = width, maxHeight = height)).placeRelative(0, 0)

        queue
            .measure(Constraints(maxWidth = width, maxHeight = queueHeaderHeightPx + offset))
            .placeRelative(0, height - queueHeaderHeightPx - offset)

        queueDragState.length = (height - queueHeaderHeightPx).coerceAtLeast(0).toFloat()
    }
}
