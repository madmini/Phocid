package org.sunsetware.phocid.ui.views.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.sunsetware.phocid.data.Lyrics
import org.sunsetware.phocid.data.LyricsDisplayPreference
import org.sunsetware.phocid.data.Preferences
import org.sunsetware.phocid.ui.theme.EXIT_DURATION
import org.sunsetware.phocid.ui.theme.INACTIVE_ALPHA
import org.sunsetware.phocid.ui.theme.Typography
import org.sunsetware.phocid.ui.theme.emphasizedExit

@Immutable
sealed class PlayerScreenLyrics {
    @Composable
    abstract fun Compose(
        lyrics: Lyrics?,
        currentPosition: () -> Long,
        preferences: Preferences,
        containerColor: Color,
        contentColor: Color,
    )
}

@Immutable
object PlayerScreenLyricsOverlay : PlayerScreenLyrics() {
    @Composable
    override fun Compose(
        lyrics: Lyrics?,
        currentPosition: () -> Long,
        preferences: Preferences,
        containerColor: Color,
        contentColor: Color,
    ) {
        fun getLineIndex(): Int? {
            return lyrics?.getLineIndex((currentPosition() + EXIT_DURATION).milliseconds)
        }

        fun getLine(index: Int?): String {
            return index?.let { lyrics?.lines?.getOrNull(it)?.second } ?: ""
        }

        var currentLineIndex by remember { mutableStateOf(getLineIndex()) }
        var currentLine by remember { mutableStateOf(getLine(currentLineIndex)) }
        var nextLine by remember { mutableStateOf(getLine(currentLineIndex?.let { it + 1 })) }
        var visibility by remember { mutableStateOf(false) }
        val alpha = animateFloatAsState(if (visibility) 1f else 0f)

        LaunchedEffect(lyrics, preferences) {
            if (lyrics != null && preferences.lyricsDisplay != LyricsDisplayPreference.DISABLED) {
                while (isActive) {
                    currentLineIndex = getLineIndex()
                    currentLine = getLine(currentLineIndex)
                    nextLine = getLine(currentLineIndex?.let { it + 1 })
                    visibility =
                        when (preferences.lyricsDisplay) {
                            LyricsDisplayPreference.DISABLED -> throw Error() // impossible
                            LyricsDisplayPreference.DEFAULT -> currentLine.isNotEmpty()
                            LyricsDisplayPreference.TWO_LINES ->
                                currentLine.isNotEmpty() || nextLine.isNotEmpty()
                        }

                    delay(42.milliseconds) // 24 fps
                }
            } else {
                currentLineIndex = null
                currentLine = ""
                nextLine = ""
                visibility = false
            }
        }

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Box(
                modifier =
                    Modifier.alpha(alpha.value)
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                        .background(containerColor, RoundedCornerShape(4.dp))
                        .padding(8.dp)
            ) {
                when (preferences.lyricsDisplay) {
                    LyricsDisplayPreference.DISABLED -> {}
                    LyricsDisplayPreference.DEFAULT -> {
                        AnimatedContent(
                            currentLine,
                            transitionSpec = {
                                slideInVertically(emphasizedExit()) togetherWith
                                        slideOutVertically(
                                            emphasizedExit(),
                                            targetOffsetY = { it / 2 })
                            },
                            contentAlignment = Alignment.TopStart,
                        ) { animatedLine ->
                            Text(animatedLine, style = Typography.bodyLarge, color = contentColor)
                        }
                    }

                    LyricsDisplayPreference.TWO_LINES -> {
                        AnimatedContent(
                            Pair(currentLine, nextLine),
                            transitionSpec = {
                                slideInVertically(emphasizedExit()) togetherWith
                                        slideOutVertically(
                                            emphasizedExit(),
                                            targetOffsetY = { it / 2 })
                            },
                            contentAlignment = Alignment.TopCenter,
                        ) { (animatedCurrentLine, animatedNextLine) ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                if (animatedCurrentLine.isNotEmpty()) {
                                    Text(
                                        animatedCurrentLine,
                                        style = Typography.bodyLarge,
                                        color = contentColor,
                                    )
                                }
                                if (animatedNextLine.isNotEmpty()) {
                                    Text(
                                        animatedNextLine,
                                        style = Typography.bodyLarge,
                                        color = contentColor.copy(alpha = INACTIVE_ALPHA),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
