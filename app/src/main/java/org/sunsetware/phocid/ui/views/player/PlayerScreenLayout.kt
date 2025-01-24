package org.sunsetware.phocid.ui.views.player

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.unit.Density
import org.sunsetware.phocid.ui.components.BinaryDragState

@Immutable
sealed class PlayerScreenLayout {
    /** [queueDragState]'s [BinaryDragState.length] must be updated here. */
    abstract fun Placeable.PlacementScope.place(
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
    )
}

enum class AspectRatio {
    LANDSCAPE,
    SQUARE,
    PORTRAIT,
}

@Stable
fun aspectRatio(width: Int, height: Int, threshold: Float): AspectRatio {
    return when {
        width.toFloat() / height >= threshold -> AspectRatio.LANDSCAPE
        height.toFloat() / width >= threshold -> AspectRatio.PORTRAIT
        else -> AspectRatio.SQUARE
    }
}
