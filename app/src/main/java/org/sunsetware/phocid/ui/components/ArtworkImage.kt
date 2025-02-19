package org.sunsetware.phocid.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.sunsetware.phocid.data.ArtworkColorPreference
import org.sunsetware.phocid.data.getArtworkColor
import org.sunsetware.phocid.data.loadArtwork
import org.sunsetware.phocid.ui.theme.contentColor

@Immutable
sealed class Artwork {
    abstract fun getColor(artworkColorPreference: ArtworkColorPreference): Color

    @Immutable
    data class Track(val track: org.sunsetware.phocid.data.Track) : Artwork() {
        override fun getColor(artworkColorPreference: ArtworkColorPreference): Color {
            return track.getArtworkColor(artworkColorPreference)
        }
    }

    @Immutable
    data class Icon(val icon: ImageVector, val color: Color) : Artwork() {
        override fun getColor(artworkColorPreference: ArtworkColorPreference): Color {
            return color
        }
    }
}

@Composable
fun ArtworkImage(
    artwork: Artwork,
    artworkColorPreference: ArtworkColorPreference,
    shape: Shape,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    async: Boolean = true,
) {
    val context = LocalContext.current
    var image by
        remember(artwork) {
            mutableStateOf(
                if (!async && artwork is Artwork.Track) {
                    loadArtwork(context, artwork.track.id)?.asImageBitmap()
                } else {
                    null as ImageBitmap?
                }
            )
        }
    val icon =
        when (artwork) {
            is Artwork.Track -> Icons.Outlined.MusicNote
            is Artwork.Icon -> artwork.icon
        }
    var isIcon by
        remember(artwork) {
            mutableStateOf(
                if (async) {
                    artwork is Artwork.Icon
                } else {
                    image == null
                }
            )
        }

    LaunchedEffect(artwork) {
        if (artwork is Artwork.Track && async) {
            withContext(Dispatchers.IO) {
                image = loadArtwork(context, artwork.track.id)?.asImageBitmap()
                if (image == null) isIcon = true
            }
        }
    }

    if (!isIcon) {
        if (image != null) {
            Image(
                painter = BitmapPainter(image!!),
                contentDescription = null,
                modifier = modifier.clip(shape),
                contentScale = contentScale,
            )
        } else {
            Box(modifier = modifier)
        }
    } else {
        val color =
            remember(artwork, artworkColorPreference) { artwork.getColor(artworkColorPreference) }
        Box(
            modifier = modifier.clip(shape).background(color.contentColor()),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.fillMaxSize(0.5f))
        }
    }
}
