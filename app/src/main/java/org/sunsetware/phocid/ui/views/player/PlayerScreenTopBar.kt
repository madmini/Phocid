@file:OptIn(ExperimentalMaterial3Api::class)

package org.sunsetware.phocid.ui.views.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.sunsetware.phocid.R
import org.sunsetware.phocid.Strings
import org.sunsetware.phocid.ui.components.SingleLineText

@Immutable
sealed class PlayerScreenTopBar {
    @Composable
    abstract fun Compose(
        containerColor: Color,
        contentColor: Color,
        lyricsButtonEnabled: Boolean,
        onBack: () -> Unit,
        onShowLyrics: () -> Unit,
    )
}

@Immutable
object PlayerScreenTopBarDefaultOverlay : PlayerScreenTopBar() {
    @Composable
    override fun Compose(
        containerColor: Color,
        contentColor: Color,
        lyricsButtonEnabled: Boolean,
        onBack: () -> Unit,
        onShowLyrics: () -> Unit,
    ) {
        Box(modifier = Modifier.fillMaxWidth().height((48 + 8 * 2).dp)) {
            FilledTonalIconButton(
                onClick = onBack,
                modifier = Modifier.padding(start = 8.dp, top = 8.dp),
                colors =
                    IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
            ) {
                Icon(
                    Icons.AutoMirrored.Default.ArrowBack,
                    contentDescription = Strings[R.string.commons_back],
                )
            }
            AnimatedVisibility(
                lyricsButtonEnabled,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopEnd).padding(end = 8.dp, top = 8.dp),
            ) {
                FilledTonalIconButton(
                    onClick = onShowLyrics,
                    colors =
                        IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = containerColor,
                            contentColor = contentColor,
                        ),
                ) {
                    Icon(
                        Icons.Outlined.Subtitles,
                        contentDescription = Strings[R.string.player_lyrics],
                    )
                }
            }
        }
    }
}

@Immutable
object PlayerScreenTopBarDefaultStandalone : PlayerScreenTopBar() {
    @Composable
    override fun Compose(
        containerColor: Color,
        contentColor: Color,
        lyricsButtonEnabled: Boolean,
        onBack: () -> Unit,
        onShowLyrics: () -> Unit,
    ) {
        // Hack to remove animation delay
        // https://stackoverflow.com/q/77928923
        key(containerColor) {
            // Explicit height to prevent incorrect measured height in outer layout
            // Setting the height as a modifier on TopAppBar will result in displaced title
            Box(modifier = Modifier.height(64.dp)) {
                TopAppBar(
                    title = { SingleLineText(Strings[R.string.player_now_playing]) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = Strings[R.string.commons_back],
                            )
                        }
                    },
                    actions = {
                        IconButton(enabled = lyricsButtonEnabled, onClick = onShowLyrics) {
                            Icon(
                                Icons.Outlined.Subtitles,
                                contentDescription = Strings[R.string.player_lyrics],
                            )
                        }
                    },
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = containerColor,
                            scrolledContainerColor = containerColor,
                            navigationIconContentColor = contentColor,
                            titleContentColor = contentColor,
                            actionIconContentColor = contentColor,
                        ),
                )
            }
        }
    }
}
