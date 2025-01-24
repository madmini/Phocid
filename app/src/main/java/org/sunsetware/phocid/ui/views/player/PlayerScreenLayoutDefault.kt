package org.sunsetware.phocid.ui.views.player

import androidx.compose.runtime.Immutable
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import kotlin.math.roundToInt
import org.sunsetware.phocid.ui.components.BinaryDragState

@Immutable
object PlayerScreenLayoutDefault : PlayerScreenLayout() {
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
                AspectRatio.LANDSCAPE -> PlayerScreenLayoutDefaultLandscape
                AspectRatio.SQUARE -> PlayerScreenLayoutDefaultSquare
                AspectRatio.PORTRAIT -> PlayerScreenLayoutDefaultPortrait
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
object PlayerScreenLayoutDefaultPortrait : PlayerScreenLayout() {
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
        scrim.measure(Constraints(maxWidth = width, maxHeight = artworkHeight)).placeRelative(0, 0)

        val controlsPlaceable =
            controls.measure(
                Constraints(maxWidth = width, maxHeight = height - artworkHeight + offset)
            )
        controlsPlaceable.placeRelative(0, artworkHeight - offset)

        queue
            .measure(
                Constraints(
                    maxWidth = width,
                    maxHeight =
                        (height - artworkHeight + offset - controlsPlaceable.height).coerceAtLeast(
                            0
                        ),
                )
            )
            .placeRelative(0, artworkHeight - offset + controlsPlaceable.height)

        queueDragState.length = artworkHeight.toFloat()
    }
}

@Immutable
object PlayerScreenLayoutDefaultLandscape : PlayerScreenLayout() {
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

        val controlsPlaceable =
            controls.measure(Constraints(maxWidth = width - artworkWidth, maxHeight = height))
        controlsPlaceable.placeRelative(artworkWidth, 0)
        scrim
            .measure(Constraints(maxWidth = width - artworkWidth, maxHeight = height))
            .placeRelative(artworkWidth, 0)

        queue
            .measure(
                Constraints(
                    maxWidth = width - artworkWidth,
                    maxHeight = (height - controlsPlaceable.height + offset).coerceAtLeast(0),
                )
            )
            .placeRelative(artworkWidth, controlsPlaceable.height - offset)

        queueDragState.length = controlsPlaceable.height.toFloat()
    }
}

@Immutable
object PlayerScreenLayoutDefaultSquare : PlayerScreenLayout() {
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

        val controlsPlaceable =
            controls.measure(
                Constraints(
                    maxWidth = width,
                    maxHeight = (height - topBarPlaceable.height).coerceAtLeast(0),
                )
            )
        controlsPlaceable.placeRelative(0, topBarPlaceable.height)

        scrim.measure(Constraints(maxWidth = width, maxHeight = height)).placeRelative(0, 0)

        queue
            .measure(
                Constraints(
                    maxWidth = width,
                    maxHeight =
                        (height - topBarPlaceable.height - controlsPlaceable.height + offset)
                            .coerceAtLeast(0),
                )
            )
            .placeRelative(0, topBarPlaceable.height - offset + controlsPlaceable.height)

        queueDragState.length = (topBarPlaceable.height + controlsPlaceable.height).toFloat()
    }
}
