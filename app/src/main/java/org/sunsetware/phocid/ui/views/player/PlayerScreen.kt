@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package org.sunsetware.phocid.ui.views.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.Player
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.sunsetware.phocid.MainViewModel
import org.sunsetware.phocid.R
import org.sunsetware.phocid.Strings
import org.sunsetware.phocid.UiManager
import org.sunsetware.phocid.data.*
import org.sunsetware.phocid.ui.components.*
import org.sunsetware.phocid.ui.theme.LocalThemeAccent
import org.sunsetware.phocid.ui.theme.contentColor
import org.sunsetware.phocid.ui.theme.customColorScheme
import org.sunsetware.phocid.ui.theme.pureBackgroundColor
import org.sunsetware.phocid.ui.views.LyricsDialog
import org.sunsetware.phocid.ui.views.NewPlaylistDialog
import org.sunsetware.phocid.ui.views.SpeedAndPitchDialog
import org.sunsetware.phocid.ui.views.TimerDialog
import org.sunsetware.phocid.utils.*

@Composable
fun PlayerScreen(dragLock: DragLock, viewModel: MainViewModel = viewModel()) {
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val playerManager = viewModel.playerManager
    val uiManager = viewModel.uiManager
    val playerScreenDragState = uiManager.playerScreenDragState
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val libraryIndex by viewModel.libraryIndex.collectAsStateWithLifecycle()

    val playerState by playerManager.state.collectAsStateWithLifecycle()
    val playerTransientStateVersion by
        playerManager.transientState
            .map(coroutineScope) { it.version }
            .collectAsStateWithLifecycle()
    val playQueue by
        playerManager.state
            .combine(coroutineScope, viewModel.libraryIndex) { state, library ->
                val trackCounts = mutableMapOf<Long, Int>()
                state.actualPlayQueue
                    .mapIndexed { index, id -> library.tracks[id] ?: InvalidTrack }
                    .map { track ->
                        val occurrence = trackCounts.getOrPut(track.id) { 0 }
                        trackCounts[track.id] = trackCounts[track.id]!! + 1
                        (Pair(track.id, occurrence) as Any) to track
                    }
            }
            .collectAsStateWithLifecycle()
    val currentTrack by
        remember {
                playerManager.state
                    .combine(viewModel.libraryIndex) { state, library ->
                        if (state.actualPlayQueue.isEmpty()) null
                        else library.tracks[state.actualPlayQueue[state.currentIndex]]
                    }
                    .filterNotNull()
            }
            .collectAsStateWithLifecycle(
                initialValue =
                    if (playerState.actualPlayQueue.isEmpty()) InvalidTrack
                    else
                        libraryIndex.tracks[playerState.actualPlayQueue[playerState.currentIndex]]
                            ?: InvalidTrack
            )
    val currentTrackIndex = playerState.currentIndex
    val playlists by viewModel.playlistManager.playlists.collectAsStateWithLifecycle()
    val currentTrackIsFavorite =
        remember(currentTrack, playlists) {
            playlists[SpecialPlaylist.FAVORITES.key]?.entries?.any {
                it.track?.id == currentTrack.id
            } == true
        }
    val currentTrackLyrics =
        remember(currentTrack, preferences) {
            if (preferences.lyricsDisplay == LyricsDisplayPreference.DISABLED) return@remember null

            val cachedLyrics = viewModel.lyricsCache.get()
            if (cachedLyrics != null && cachedLyrics.first == currentTrack.id) {
                cachedLyrics.second
            } else {
                val externalLyrics = loadLyrics(currentTrack, preferences.charsetName)
                if (externalLyrics != null)
                    viewModel.lyricsCache.set(Pair(currentTrack.id, externalLyrics))
                externalLyrics
                    ?: if (preferences.treatEmbeddedLyricsAsLrc) {
                        currentTrack.unsyncedLyrics
                            ?.let { parseLrc(it) }
                            ?.takeIf { it.lines.isNotEmpty() }
                    } else {
                        null
                    }
            }
        }
    val isPlaying by
        playerManager.transientState
            .map(coroutineScope) { it.isPlaying }
            .collectAsStateWithLifecycle()
    val repeat by
        playerManager.state.map(coroutineScope) { it.repeat }.collectAsStateWithLifecycle()
    val shuffle by
        playerManager.state.map(coroutineScope) { it.shuffle }.collectAsStateWithLifecycle()

    val defaultColor = LocalThemeAccent.current
    val animatedContainerColor = remember {
        Animatable(
            if (preferences.coloredPlayer)
                currentTrack.getArtworkColor(preferences.artworkColorPreference)
            else defaultColor
        )
    }
    val animatedContentColor = animatedContainerColor.value.contentColor()

    val controlsDragLock = remember { DragLock() }
    val playQueueDragLock = remember { DragLock() }
    val playQueueLazyListState = rememberLazyListState()
    suspend fun scrollPlayQueueToNextTrack() {
        val state = playerManager.state.value
        val currentIndex = state.currentIndex
        val nextIndex =
            (currentIndex + 1).wrap(playQueue.size, state.repeat != Player.REPEAT_MODE_OFF)
                ?: currentIndex
        playQueueLazyListState.animateScrollToItem(nextIndex)
    }
    val playQueueDragState = remember {
        BinaryDragState(
            WeakReference(coroutineScope),
            0f,
            onSnapToZero = { coroutineScope.launch { scrollPlayQueueToNextTrack() } },
        )
    }
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (playQueueDragState.position < 1 && available.y < 0) {
                    playQueueDragState.onDrag(playQueueDragLock, available.y)
                    return available
                } else {
                    return Offset.Zero
                }
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (consumed.y > 0f && available.y > 0f) return Offset.Zero
                playQueueDragState.onDrag(playQueueDragLock, available.y)

                return available
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                return if (available.y < 0f && playQueueDragState.position < 1) {
                    available
                } else {
                    Velocity.Zero
                }
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                return available
            }
        }
    }
    val controlsDragModifier =
        Modifier.pointerInput(Unit) {
                // Block non-vertical gestures
                detectHorizontalDragGestures { _, _ -> }
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = {
                        coroutineScope.launch { playQueueLazyListState.stopScroll() }
                        playQueueDragState.onDragStart(controlsDragLock)
                    },
                    onDragCancel = {
                        coroutineScope.launch { playQueueLazyListState.stopScroll() }
                        playQueueDragState.onDragEnd(controlsDragLock, density)
                    },
                    onDragEnd = {
                        coroutineScope.launch { playQueueLazyListState.stopScroll() }
                        playQueueDragState.onDragEnd(controlsDragLock, density)
                    },
                ) { _, dragAmount ->
                    playQueueDragState.onDrag(controlsDragLock, dragAmount)
                }
            }

    val lyricsButtonEnabled = currentTrack.unsyncedLyrics != null || currentTrackLyrics != null

    fun showLyrics() {
        (currentTrackLyrics?.lines?.map { it.second } ?: currentTrack.unsyncedLyrics?.lines())
            ?.let { uiManager.openDialog(LyricsDialog(currentTrack.displayTitle, it)) }
    }

    BackHandler(playerScreenDragState.position >= 1) {
        if (playQueueDragState.position >= 1) {
            coroutineScope.launch {
                playQueueLazyListState.stopScroll()
                playQueueDragState.animateTo(0f)
            }
        } else {
            playerScreenDragState.animateTo(0f)
        }
    }

    // Auto close on playQueue clear
    LaunchedEffect(playQueue) {
        if (playQueue.isEmpty()) {
            playerScreenDragState.animateTo(0f)
        }
    }

    // Change colors
    // TODO: Fix this synchronization
    val disposing = remember { AtomicBoolean(false) }
    LaunchedEffect(currentTrack, preferences.coloredPlayer) {
        if (!disposing.get()) {
            val color =
                if (preferences.coloredPlayer)
                    currentTrack.getArtworkColor(preferences.artworkColorPreference)
                else defaultColor
            uiManager.overrideStatusBarLightColor.update { color.luminance() >= 0.5 }
            animatedContainerColor.animateTo(color)
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            disposing.set(true)
            uiManager.overrideStatusBarLightColor.update { null }
        }
    }

    // Start/end listener for NestedScrollConnection
    LaunchedEffect(playQueueLazyListState.isScrollInProgress) {
        if (playQueueLazyListState.isScrollInProgress) {
            playQueueDragState.onDragStart(playQueueDragLock)
        } else {
            playQueueDragState.onDragEnd(playQueueDragLock, density)
        }
    }

    // Scroll playQueue to next track on track change
    LaunchedEffect(playQueue, currentTrack) {
        if (playQueueDragState.position <= 0) scrollPlayQueueToNextTrack()
    }

    val playerLayout = preferences.playerScreenLayout.layout
    val components = preferences.playerScreenLayout.components

    MaterialTheme(
        colorScheme =
            if (preferences.coloredPlayer)
                customColorScheme(
                        color = currentTrack.getArtworkColor(preferences.artworkColorPreference),
                        darkTheme = preferences.darkTheme.boolean ?: isSystemInDarkTheme(),
                    )
                    .let { if (preferences.pureBackgroundColor) it.pureBackgroundColor() else it }
            else MaterialTheme.colorScheme,
        shapes = MaterialTheme.shapes,
        typography = MaterialTheme.typography,
    ) {
        Scaffold(
            topBar = {
                Box(
                    modifier =
                        Modifier.fillMaxWidth()
                            .windowInsetsTopHeight(WindowInsets.statusBars)
                            .background(animatedContainerColor.value)
                )
            },
            bottomBar = {
                Box(
                    modifier =
                        Modifier.fillMaxWidth()
                            .windowInsetsBottomHeight(WindowInsets.navigationBars)
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                )
            },
            contentWindowInsets = WindowInsets(0.dp),
        ) { scaffoldPadding ->
            Box(
                modifier =
                    Modifier.fillMaxSize()
                        .padding(scaffoldPadding)
                        .consumeWindowInsets(scaffoldPadding)
                        .systemBarsPadding()
            ) {
                Layout(
                    content = {
                        Box {
                            components.topBarStandalone.Compose(
                                containerColor = animatedContainerColor.value,
                                contentColor = animatedContentColor,
                                lyricsButtonEnabled = lyricsButtonEnabled,
                                onBack = { uiManager.back() },
                                onShowLyrics = { showLyrics() },
                            )
                        }
                        Box {
                            components.topBarOverlay.Compose(
                                containerColor = animatedContainerColor.value,
                                contentColor = animatedContentColor,
                                lyricsButtonEnabled = lyricsButtonEnabled,
                                onBack = { uiManager.back() },
                                onShowLyrics = { showLyrics() },
                            )
                        }
                        Box {
                            components.artwork.Compose(
                                playerTransientStateVersion = playerTransientStateVersion,
                                artworkColorPreference = preferences.artworkColorPreference,
                                playerState = playerState,
                                playerScreenDragState = playerScreenDragState,
                                dragLock = dragLock,
                                onGetTrackAtIndex = { state, index ->
                                    state.actualPlayQueue.getOrNull(index)?.let {
                                        libraryIndex.tracks[it]
                                    } ?: InvalidTrack
                                },
                                onPrevious = { playerManager.seekToPrevious() },
                                onNext = { playerManager.seekToNext() },
                            )
                        }
                        Box {
                            components.lyricsOverlay.Compose(
                                lyrics = currentTrackLyrics,
                                currentPosition = { playerManager.currentPosition },
                                preferences = preferences,
                                containerColor = animatedContainerColor.value,
                                contentColor = animatedContentColor,
                            )
                        }
                        Box {
                            components.controls.Compose(
                                currentTrack = currentTrack,
                                currentTrackIsFavorite = currentTrackIsFavorite,
                                isPlaying = isPlaying,
                                repeat = repeat,
                                shuffle = shuffle,
                                currentPosition = { playerManager.currentPosition },
                                overflowMenuItems =
                                    playerMenuItems(
                                        playerManager,
                                        uiManager,
                                        libraryIndex,
                                        currentTrack,
                                        currentTrackIndex,
                                    ),
                                dragModifier = controlsDragModifier,
                                containerColor = animatedContainerColor.value,
                                contentColor = animatedContentColor,
                                onSeekToFraction = { playerManager.seekToFraction(it) },
                                onToggleRepeat = { playerManager.toggleRepeat() },
                                onSeekToPreviousSmart = { playerManager.seekToPreviousSmart() },
                                onTogglePlay = { playerManager.togglePlay() },
                                onSeekToNext = { playerManager.seekToNext() },
                                onToggleShuffle = { playerManager.toggleShuffle(libraryIndex) },
                                onTogglePlayQueue = {
                                    playQueueDragState.animateTo(
                                        if (playQueueDragState.position <= 0) 1f else 0f
                                    )
                                },
                                onToggleCurrentTrackIsFavorite = {
                                    viewModel.playlistManager.updatePlaylist(
                                        SpecialPlaylist.FAVORITES.key
                                    ) { playlist ->
                                        if (playlist.entries.any { it.path == currentTrack.path }) {
                                            playlist.copy(
                                                entries =
                                                    playlist.entries.filter {
                                                        it.path != currentTrack.path
                                                    }
                                            )
                                        } else {
                                            playlist.addTracks(listOf(currentTrack))
                                        }
                                    }
                                },
                            )
                        }
                        Box {
                            components.queue.Compose(
                                playQueue = playQueue,
                                currentTrackIndex = currentTrackIndex,
                                lazyListState = playQueueLazyListState,
                                trackOverflowMenuItems = { track, index ->
                                    queueMenuItems(playerManager, uiManager, track, index)
                                },
                                dragModifier = controlsDragModifier,
                                nestedScrollConnection = nestedScrollConnection,
                                containerColor = animatedContainerColor.value,
                                contentColor = animatedContentColor,
                                onTogglePlayQueue = {
                                    playQueueDragState.animateTo(
                                        if (playQueueDragState.position <= 0) 1f else 0f
                                    )
                                },
                                onMoveTrack = { from, to -> playerManager.moveTrack(from, to) },
                                onSeekTo = { playerManager.seekTo(it) },
                            )
                        }
                        Box {
                            // Scrim
                            val scrimColor = MaterialTheme.colorScheme.scrim
                            Box(
                                modifier =
                                    Modifier.fillMaxSize().drawBehind {
                                        drawRect(scrimColor, alpha = playQueueDragState.position)
                                    }
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                ) { measurables, constraints ->
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        with(playerLayout) {
                            place(
                                topBarStandalone = measurables[0],
                                topBarOverlay = measurables[1],
                                artwork = measurables[2],
                                lyricsOverlay = measurables[3],
                                controls = measurables[4],
                                queue = measurables[5],
                                scrim = measurables[6],
                                width = constraints.maxWidth,
                                height = constraints.maxHeight,
                                density = density,
                                queueDragState = playQueueDragState,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun playerMenuItems(
    playerManager: PlayerManager,
    uiManager: UiManager,
    libraryIndex: LibraryIndex,
    currentTrack: Track,
    currentTrackIndex: Int,
): List<MenuItem> {
    return listOf(
        MenuItem.Button(Strings[R.string.player_clear_queue], Icons.Filled.Clear) {
            playerManager.clearTracks()
        },
        MenuItem.Button(Strings[R.string.player_save_queue], Icons.Filled.AddBox) {
            val state = playerManager.state.value
            val tracks =
                state.unshuffledPlayQueueMapping?.mapNotNull {
                    libraryIndex.tracks[state.actualPlayQueue[it]]
                } ?: state.actualPlayQueue.mapNotNull { libraryIndex.tracks[it] }
            uiManager.openDialog(NewPlaylistDialog(tracks))
        },
        MenuItem.Button(Strings[R.string.player_timer], Icons.Filled.Timer) {
            uiManager.openDialog(TimerDialog())
        },
        MenuItem.Button(Strings[R.string.player_speed_and_pitch], Icons.Filled.Speed) {
            uiManager.openDialog(SpeedAndPitchDialog())
        },
    ) +
        MenuItem.Divider +
        MenuItem.Button(Strings[R.string.track_remove_from_queue], Icons.Filled.Remove) {
            playerManager.removeTrack(currentTrackIndex)
        } +
        trackMenuItems(currentTrack, playerManager, uiManager)
}

private fun queueMenuItems(
    playerManager: PlayerManager,
    uiManager: UiManager,
    track: Track,
    index: Int,
): List<MenuItem> {
    return listOf(
        MenuItem.Button(Strings[R.string.track_remove_from_queue], Icons.Filled.Remove) {
            playerManager.removeTrack(index)
        }
    ) + trackMenuItems(track, playerManager, uiManager)
}

@Immutable
data class PlayerScreenComponents(
    val topBarStandalone: PlayerScreenTopBar,
    val topBarOverlay: PlayerScreenTopBar,
    val artwork: PlayerScreenArtwork,
    val lyricsOverlay: PlayerScreenLyrics,
    val controls: PlayerScreenControls,
    val queue: PlayerScreenQueue,
)

@Serializable
enum class PlayerScreenLayoutType(
    val stringId: Int,
    val layout: PlayerScreenLayout,
    val components: PlayerScreenComponents,
) {
    DEFAULT(
        R.string.preferences_player_screen_layout_default,
        PlayerScreenLayoutDefault,
        PlayerScreenComponents(
            PlayerScreenTopBarDefaultStandalone,
            PlayerScreenTopBarDefaultOverlay,
            PlayerScreenArtworkDefault,
            PlayerScreenLyricsOverlay,
            PlayerScreenControlsDefault,
            PlayerScreenQueueDefault,
        ),
    ),
    NO_QUEUE(
        R.string.preferences_player_screen_layout_no_queue,
        PlayerScreenLayoutNoQueue,
        PlayerScreenComponents(
            PlayerScreenTopBarDefaultStandalone,
            PlayerScreenTopBarDefaultOverlay,
            PlayerScreenArtworkDefault,
            PlayerScreenLyricsOverlay,
            PlayerScreenControlsNoQueue,
            PlayerScreenQueueColored,
        ),
    ),
}
