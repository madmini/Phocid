package org.sunsetware.phocid.ui.views.player

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.core.view.ViewCompat
import org.sunsetware.phocid.R
import org.sunsetware.phocid.Strings
import org.sunsetware.phocid.data.*
import org.sunsetware.phocid.ui.components.*
import org.sunsetware.phocid.ui.theme.contentColor
import org.sunsetware.phocid.ui.theme.darken
import org.sunsetware.phocid.utils.*
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Immutable
sealed class PlayerScreenQueue {
    @Composable
    abstract fun Compose(
        playQueue: List<Pair<Any, Track>>,
        currentTrackIndex: Int,
        lazyListState: LazyListState,
        trackOverflowMenuItems: (Track, Int) -> List<MenuItem>,
        dragModifier: Modifier,
        nestedScrollConnection: NestedScrollConnection,
        containerColor: Color,
        contentColor: Color,
        onTogglePlayQueue: () -> Unit,
        onMoveTrack: (Int, Int) -> Unit,
        onSeekTo: (Int) -> Unit,
    )
}

val PlayerScreenQueueDefault =
    PlayerScreenQueueDefaultBase(
        { colorScheme, containerColor, contentColor -> colorScheme.surfaceContainerLow },
        { colorScheme, containerColor, contentColor -> colorScheme.onSurface },
    )
val PlayerScreenQueueColored =
    PlayerScreenQueueDefaultBase(
        { colorScheme, containerColor, contentColor -> containerColor.darken() },
        { colorScheme, containerColor, contentColor -> containerColor.darken().contentColor() },
    )

@Immutable
class PlayerScreenQueueDefaultBase(
    private val getContainerColor: (ColorScheme, Color, Color) -> Color,
    private val getContentColor: (ColorScheme, Color, Color) -> Color,
) : PlayerScreenQueue() {
    @Composable
    override fun Compose(
        playQueue: List<Pair<Any, Track>>,
        currentTrackIndex: Int,
        lazyListState: LazyListState,
        trackOverflowMenuItems: (Track, Int) -> List<MenuItem>,
        dragModifier: Modifier,
        nestedScrollConnection: NestedScrollConnection,
        containerColor: Color,
        contentColor: Color,
        onTogglePlayQueue: () -> Unit,
        onMoveTrack: (Int, Int) -> Unit,
        onSeekTo: (Int) -> Unit,
    ) {
        val view = LocalView.current

        val upNextCount = playQueue.size - currentTrackIndex - 1
        val upNextDuration =
            remember(playQueue, currentTrackIndex) {
                playQueue.drop(currentTrackIndex + 1).sumOf { it.second.duration }.toShortString()
            }

        var reorderableQueue by remember { mutableStateOf(null as List<Pair<Any, Track>>?) }
        var reorderInfo by remember { mutableStateOf(null as Pair<Int, Int>?) }
        val reorderableLazyListState =
            rememberReorderableLazyListState(lazyListState) { from, to ->
                ViewCompat.performHapticFeedback(
                    view,
                    HapticFeedbackConstantsCompat.SEGMENT_FREQUENT_TICK,
                )
                reorderInfo =
                    if (reorderInfo == null)
                        playQueue.indexOfFirst { it.first == from.key } to to.index
                    else reorderInfo!!.first to to.index

                reorderableQueue =
                    reorderableQueue?.toMutableList()?.apply { add(to.index, removeAt(from.index)) }
            }

        LaunchedEffect(playQueue) { reorderableQueue = null }

        Surface(
            modifier = Modifier.fillMaxHeight(),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Column(verticalArrangement = Arrangement.Center) {
                Surface(
                    color =
                        getContainerColor(MaterialTheme.colorScheme, containerColor, contentColor),
                    contentColor =
                        getContentColor(MaterialTheme.colorScheme, containerColor, contentColor),
                ) {
                    LibraryListHeader(
                        Strings.separate(
                            Strings[R.string.player_up_next],
                            Strings[R.string.count_track].icuFormat(upNextCount),
                            upNextDuration,
                        ),
                        modifier = dragModifier,
                        onClick = onTogglePlayQueue,
                    )
                }

                Scrollbar(lazyListState) {
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier.fillMaxSize().nestedScroll(nestedScrollConnection),
                    ) {
                        itemsIndexed(reorderableQueue ?: playQueue, { _, (key, _) -> key }) {
                            index,
                            (key, track) ->
                            ReorderableItem(
                                reorderableLazyListState,
                                key,
                                animateItemModifier =
                                    Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null),
                            ) {
                                LibraryListItemHorizontal(
                                    title = track.displayTitle,
                                    subtitle = track.displayArtistWithAlbum,
                                    lead = {
                                        AnimatedContent(
                                            targetState = (index - currentTrackIndex).toString(),
                                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                                            modifier =
                                                Modifier.draggableHandle(
                                                    onDragStarted = {
                                                        ViewCompat.performHapticFeedback(
                                                            view,
                                                            HapticFeedbackConstantsCompat.DRAG_START,
                                                        )
                                                        reorderInfo = null
                                                        reorderableQueue = playQueue
                                                    },
                                                    onDragStopped = {
                                                        ViewCompat.performHapticFeedback(
                                                            view,
                                                            HapticFeedbackConstantsCompat
                                                                .GESTURE_END,
                                                        )
                                                        reorderInfo?.let { (from, to) ->
                                                            onMoveTrack(from, to)
                                                        }
                                                    },
                                                ),
                                        ) {
                                            Text(
                                                text = it,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                        }
                                    },
                                    actions = {
                                        OverflowMenu(trackOverflowMenuItems(track, index))
                                    },
                                    deemphasized = index <= currentTrackIndex,
                                    modifier =
                                        Modifier.clickable { onSeekTo(index) }
                                            .background(
                                                MaterialTheme.colorScheme.surfaceContainerLow
                                            ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
