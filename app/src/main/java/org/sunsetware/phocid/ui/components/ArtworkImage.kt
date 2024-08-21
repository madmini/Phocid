package org.sunsetware.phocid.ui.components

import android.graphics.Bitmap
import androidx.collection.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import org.sunsetware.phocid.data.ArtworkColorPreference
import org.sunsetware.phocid.data.getArtworkColor
import org.sunsetware.phocid.ui.theme.contentColor
import org.sunsetware.phocid.utils.Nullable

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
    cache: LruCache<Long, Nullable<Bitmap>>,
    artwork: Artwork,
    artworkColorPreference: ArtworkColorPreference,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val (image, icon) =
        remember(artwork) {
            when (artwork) {
                is Artwork.Track -> {
                    cache[artwork.track.id]?.value?.asImageBitmap()?.let { Pair(it, null) }
                        ?: Pair(null, Icons.Outlined.MusicNote)
                }
                is Artwork.Icon -> {
                    Pair(null, artwork.icon)
                }
            }
        }
    if (image != null) {
        Image(
            painter = BitmapPainter(image),
            contentDescription = null,
            modifier = modifier,
            contentScale = contentScale,
        )
    } else {
        val color =
            remember(artwork, artworkColorPreference) { artwork.getColor(artworkColorPreference) }
        Box(
            modifier = modifier.background(color.contentColor()),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon!!, null, tint = color, modifier = Modifier.fillMaxSize(0.5f))
        }
    }
}
