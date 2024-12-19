@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package org.sunsetware.phocid.ui.views

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.core.view.ViewCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.Player
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.sunsetware.phocid.MainViewModel
import org.sunsetware.phocid.R
import org.sunsetware.phocid.Strings
import org.sunsetware.phocid.TNUM
import org.sunsetware.phocid.UiManager
import org.sunsetware.phocid.data.*
import org.sunsetware.phocid.ui.components.*
import org.sunsetware.phocid.ui.theme.EXIT_DURATION
import org.sunsetware.phocid.ui.theme.INACTIVE_ALPHA
import org.sunsetware.phocid.ui.theme.LocalThemeAccent
import org.sunsetware.phocid.ui.theme.Typography
import org.sunsetware.phocid.ui.theme.contentColor
import org.sunsetware.phocid.ui.theme.customColorScheme
import org.sunsetware.phocid.ui.theme.emphasizedExit
import org.sunsetware.phocid.ui.theme.pureBackgroundColor
import org.sunsetware.phocid.utils.*
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private const val LAYOUT_ASPECT_RATIO_THRESHOLD = 1.5f

@Composable
fun PlayerScreen(dragLock: DragLock, viewModel: MainViewModel = viewModel()) {
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val playerWrapper = viewModel.playerWrapper
    val uiManager = viewModel.uiManager
    val playerScreenDragState = uiManager.playerScreenDragState
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val libraryIndex by viewModel.libraryIndex.collectAsStateWithLifecycle()

    val playerState by playerWrapper.state.collectAsStateWithLifecycle()
    val playQueue by
        playerWrapper.state
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
    val currentTrackIndex by
        playerWrapper.state.map(coroutineScope) { it.currentIndex }.collectAsStateWithLifecycle()
    val currentTrack by
        remember {
                playerWrapper.state
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
                val lyrics = loadLyrics(currentTrack, preferences.charsetName)
                if (lyrics != null) viewModel.lyricsCache.set(Pair(currentTrack.id, lyrics))
                lyrics
            }
        }

    val defaultColor = LocalThemeAccent.current
    val animatedContainerColor = remember {
        Animatable(
            if (preferences.coloredPlayer)
                currentTrack.getArtworkColor(preferences.artworkColorPreference)
            else defaultColor
        )
    }
    val animatedContentColor = animatedContainerColor.value.contentColor()

    var layoutType by remember { mutableStateOf(LayoutType.PORTRAIT) }
    val controlsDragLock = remember { DragLock() }
    val playQueueDragLock = remember { DragLock() }
    val playQueueLazyListState = rememberLazyListState()
    suspend fun scrollPlayQueueToNextTrack() {
        val state = playerWrapper.state.value
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
                PlayerLayout(
                    topBar = {
                        // Hack to remove animation delay
                        // https://stackoverflow.com/q/77928923
                        key(animatedContainerColor.value) {
                            TopAppBar(
                                title = { Text(Strings[R.string.player_now_playing]) },
                                navigationIcon = {
                                    IconButton(onClick = { uiManager.back() }) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = Strings[R.string.commons_back],
                                        )
                                    }
                                },
                                colors =
                                    TopAppBarDefaults.topAppBarColors(
                                        containerColor = animatedContainerColor.value,
                                        scrolledContainerColor = animatedContainerColor.value,
                                        navigationIconContentColor = animatedContentColor,
                                        titleContentColor = animatedContentColor,
                                        actionIconContentColor = Color.Unspecified,
                                    ),
                            )
                        }
                    },
                    carousel = {
                        val scrimColor = MaterialTheme.colorScheme.scrim
                        Box(
                            modifier =
                                Modifier.drawWithContent {
                                    drawContent()
                                    if (layoutType == LayoutType.PORTRAIT) {
                                        drawRect(scrimColor, alpha = playQueueDragState.position)
                                    }
                                }
                        ) {
                            Artwork(
                                playerWrapper = playerWrapper,
                                libraryIndex = libraryIndex,
                                artworkColorPreference = preferences.artworkColorPreference,
                                playerState = playerState,
                                playerScreenDragState = playerScreenDragState,
                                dragLock = dragLock,
                                tapKey = currentTrackLyrics,
                                onTap = {
                                    if (currentTrackLyrics != null) {
                                        uiManager.openDialog(
                                            LyricsDialog(
                                                currentTrack.displayTitle,
                                                currentTrackLyrics,
                                            )
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxSize(),
                            )
                            LyricsOverlay(
                                currentTrackLyrics,
                                playerWrapper,
                                preferences,
                                animatedContainerColor.value,
                                animatedContentColor,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    },
                    main = {
                        Column(
                            modifier =
                                Modifier.pointerInput(Unit) {
                                        // Block non-vertical gestures
                                        detectHorizontalDragGestures { _, _ -> }
                                    }
                                    .pointerInput(layoutType) {
                                        if (layoutType == LayoutType.PORTRAIT) {
                                            detectVerticalDragGestures(
                                                onDragStart = {
                                                    coroutineScope.launch {
                                                        playQueueLazyListState.stopScroll()
                                                    }
                                                    playQueueDragState.onDragStart(controlsDragLock)
                                                },
                                                onDragCancel = {
                                                    coroutineScope.launch {
                                                        playQueueLazyListState.stopScroll()
                                                    }
                                                    playQueueDragState.onDragEnd(
                                                        controlsDragLock,
                                                        density,
                                                    )
                                                },
                                                onDragEnd = {
                                                    coroutineScope.launch {
                                                        playQueueLazyListState.stopScroll()
                                                    }
                                                    playQueueDragState.onDragEnd(
                                                        controlsDragLock,
                                                        density,
                                                    )
                                                },
                                            ) { _, dragAmount ->
                                                playQueueDragState.onDrag(
                                                    controlsDragLock,
                                                    dragAmount,
                                                )
                                            }
                                        }
                                    }
                                    .nestedScroll(nestedScrollConnection)
                        ) {
                            Controls(
                                playerWrapper,
                                uiManager,
                                libraryIndex,
                                currentTrack,
                                currentTrackIndex,
                                currentTrackIsFavorite,
                                onToggleCurrentTrackFavorite = {
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
                                onToggleShuffle = { playerWrapper.toggleShuffle(libraryIndex) },
                                onTogglePlayQueue = {
                                    playQueueDragState.animateTo(
                                        if (playQueueDragState.position <= 0) 1f else 0f
                                    )
                                },
                                animatedContainerColor.value,
                                animatedContentColor,
                            )
                            PlayQueue(
                                playerWrapper,
                                uiManager,
                                playQueue,
                                playQueueLazyListState,
                                onTogglePlayQueue = {
                                    playQueueDragState.animateTo(
                                        if (playQueueDragState.position <= 0) 1f else 0f
                                    )
                                },
                            )
                        }
                    },
                    onSetLayoutType = { layoutType = it },
                    onSetWidth = { playQueueDragState.length = it.toFloat() },
                    dragState = playQueueDragState,
                )
            }
        }
    }
}

/** @param topBarHeight temporary hack for incorrectly measured height */
@Composable
private inline fun PlayerLayout(
    crossinline topBar: @Composable () -> Unit,
    crossinline carousel: @Composable () -> Unit,
    crossinline main: @Composable () -> Unit,
    crossinline onSetLayoutType: (LayoutType) -> Unit,
    crossinline onSetWidth: (Int) -> Unit,
    dragState: BinaryDragState,
    topBarHeight: Dp = 64.dp,
) {
    Layout(
        content = {
            Box { topBar() }
            Box { carousel() }
            Box { main() }
        },
        modifier = Modifier.fillMaxSize(),
    ) { measurables, constraints ->
        val type = layoutType(constraints.maxWidth, constraints.maxHeight)

        val width = constraints.maxWidth
        val height = constraints.maxHeight

        layout(width, height) {
            when (type) {
                LayoutType.LANDSCAPE -> {
                    measurables[1]
                        .measure(Constraints(maxWidth = height, maxHeight = height))
                        .placeRelative(0, 0)
                    measurables[2]
                        .measure(Constraints(maxWidth = width - height, maxHeight = height))
                        .placeRelative(height, 0)
                }
                LayoutType.SQUARE -> {
                    val topBarPlaceable = measurables[0].measure(constraints)
                    topBarPlaceable.placeRelative(0, 0)
                    val topBarHeightPx = topBarHeight.roundToPx()
                    measurables[2]
                        .measure(
                            Constraints(
                                maxWidth = width,
                                maxHeight = (height - topBarHeightPx).coerceAtLeast(0),
                            )
                        )
                        .placeRelative(0, topBarHeightPx)
                }
                LayoutType.PORTRAIT -> {
                    val offset = (dragState.length * dragState.position).roundToInt()
                    measurables[1]
                        .measure(Constraints(maxWidth = width, maxHeight = width))
                        .placeRelative(0, 0)
                    measurables[2]
                        .measure(Constraints(maxWidth = width, maxHeight = height - width + offset))
                        .placeRelative(0, width - offset)
                }
            }

            onSetLayoutType(type)
            onSetWidth(width)
        }
    }
}

@Composable
private fun Artwork(
    playerWrapper: PlayerWrapper,
    libraryIndex: LibraryIndex,
    artworkColorPreference: ArtworkColorPreference,
    playerState: PlayerState,
    playerScreenDragState: BinaryDragState,
    dragLock: DragLock,
    tapKey: Any?,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val playerTransientStateVersion by
        playerWrapper.transientState
            .map(coroutineScope) { it.version }
            .collectAsStateWithLifecycle()
    Box(modifier = modifier) {
        TrackCarousel(
            state = playerState,
            key = playerTransientStateVersion,
            countSelector = { it.actualPlayQueue.size },
            indexSelector = { it.currentIndex },
            repeatSelector = { it.repeat != Player.REPEAT_MODE_OFF },
            indexEqualitySelector = { state, index ->
                if (state.shuffle) state.unshuffledPlayQueueMapping!!.indexOf(index) else index
            },
            tapKey = tapKey,
            onTap = onTap,
            onVerticalDrag = {
                detectVerticalDragGestures(
                    onDragStart = { playerScreenDragState.onDragStart(dragLock) },
                    onDragCancel = { playerScreenDragState.onDragEnd(dragLock, density) },
                    onDragEnd = { playerScreenDragState.onDragEnd(dragLock, density) },
                ) { _, dragAmount ->
                    playerScreenDragState.onDrag(dragLock, dragAmount)
                }
            },
            onPrevious = { playerWrapper.seekToPrevious() },
            onNext = { playerWrapper.seekToNext() },
            modifier = Modifier.aspectRatio(1f, matchHeightConstraintsFirst = true),
        ) { state, index ->
            ArtworkImage(
                artwork =
                    Artwork.Track(
                        state.actualPlayQueue.getOrNull(index)?.let { libraryIndex.tracks[it] }
                            ?: InvalidTrack
                    ),
                artworkColorPreference = artworkColorPreference,
                shape = RoundedCornerShape(0.dp),
                modifier = Modifier.fillMaxSize(),
                async = false,
            )
        }
        FilledTonalIconButton(
            onClick = { playerScreenDragState.animateTo(0f) },
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
    }
}

@Composable
private fun LyricsOverlay(
    lyrics: Lyrics?,
    playerWrapper: PlayerWrapper,
    preferences: Preferences,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    fun getLineIndex(): Int? {
        return lyrics?.getLineIndex((playerWrapper.currentPosition + EXIT_DURATION).milliseconds)
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

    Box(modifier = modifier, contentAlignment = Alignment.BottomCenter) {
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
                            fadeIn(emphasizedExit()) togetherWith fadeOut(emphasizedExit())
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
                            fadeIn(emphasizedExit()) togetherWith fadeOut(emphasizedExit())
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

@Composable
private fun Controls(
    playerWrapper: PlayerWrapper,
    uiManager: UiManager,
    libraryIndex: LibraryIndex,
    currentTrack: Track,
    currentTrackIndex: Int?,
    currentTrackIsFavorite: Boolean,
    onToggleCurrentTrackFavorite: () -> Unit,
    onToggleShuffle: () -> Unit,
    onTogglePlayQueue: () -> Unit,
    containerColor: Color,
    contentColor: Color,
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val isPlaying by
        playerWrapper.transientState
            .map(coroutineScope) { it.isPlaying }
            .collectAsStateWithLifecycle()
    val repeat by
        playerWrapper.state.map(coroutineScope) { it.repeat }.collectAsStateWithLifecycle()
    val shuffle by
        playerWrapper.state.map(coroutineScope) { it.shuffle }.collectAsStateWithLifecycle()
    var progress by remember { mutableFloatStateOf(0f) }
    val progressSeconds by
        remember(currentTrack) {
            derivedStateOf {
                (progress * (currentTrack.duration.inWholeSeconds))
                    .let { if (it.isNaN()) 0f else it }
                    .roundToInt()
            }
        }
    var isDraggingProgressSlider by remember { mutableStateOf(false) }

    // Update progress
    LaunchedEffect(currentTrack) {
        val frameTime = (1f / context.display.refreshRate).toDouble().milliseconds

        while (isActive) {
            val currentPosition = playerWrapper.currentPosition
            if (!isDraggingProgressSlider) {
                progress =
                    (currentPosition.toFloat() / (currentTrack.duration.inWholeMilliseconds))
                        .takeIf { !it.isNaN() } ?: 0f
            }
            delay(frameTime)
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Surface(color = containerColor, contentColor = contentColor) {
            Column {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SingleLineText(
                        progressSeconds.seconds.toShortString(),
                        style = Typography.labelMedium.copy(fontFeatureSettings = TNUM),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(36.dp),
                    )
                    ProgressSlider(
                        value = progress,
                        onValueChange = {
                            isDraggingProgressSlider = true
                            progress = it
                        },
                        onValueChangeFinished = {
                            isDraggingProgressSlider = false
                            playerWrapper.seekToFraction(progress)
                        },
                        animate = isPlaying && !isDraggingProgressSlider,
                        modifier = Modifier.padding(horizontal = 16.dp).weight(1f),
                    )
                    SingleLineText(
                        currentTrack.duration.toShortString(),
                        style = Typography.labelMedium.copy(fontFeatureSettings = TNUM),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(36.dp),
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { playerWrapper.toggleRepeat() }) {
                        when (repeat) {
                            Player.REPEAT_MODE_ALL ->
                                Icon(
                                    Icons.Filled.Repeat,
                                    contentDescription = Strings[R.string.player_repeat_mode_all],
                                )
                            Player.REPEAT_MODE_ONE ->
                                Icon(
                                    Icons.Filled.RepeatOne,
                                    contentDescription = Strings[R.string.player_repeat_mode_one],
                                )
                            else ->
                                Icon(
                                    Icons.Filled.Repeat,
                                    contentDescription = Strings[R.string.player_repeat_mode_off],
                                    modifier = Modifier.alpha(INACTIVE_ALPHA),
                                )
                        }
                    }
                    IconButton(onClick = { playerWrapper.seekToPreviousSmart() }) {
                        Icon(
                            Icons.Filled.SkipPrevious,
                            contentDescription = Strings[R.string.player_previous],
                        )
                    }
                    FloatingActionButton(
                        onClick = { playerWrapper.togglePlay() },
                        containerColor = contentColor,
                        contentColor = containerColor,
                    ) {
                        AnimatedContent(targetState = isPlaying) { animatedIsPlaying ->
                            if (animatedIsPlaying) {
                                Icon(
                                    Icons.Filled.Pause,
                                    contentDescription = Strings[R.string.player_pause],
                                )
                            } else {
                                Icon(
                                    Icons.Filled.PlayArrow,
                                    contentDescription = Strings[R.string.player_play],
                                )
                            }
                        }
                    }
                    IconButton(onClick = { playerWrapper.seekToNext() }) {
                        Icon(
                            Icons.Filled.SkipNext,
                            contentDescription = Strings[R.string.player_next],
                        )
                    }

                    IconButton(onClick = onToggleShuffle) {
                        if (shuffle) {
                            Icon(
                                Icons.Filled.Shuffle,
                                contentDescription = Strings[R.string.player_shuffle_on],
                            )
                        } else {
                            Icon(
                                Icons.Filled.Shuffle,
                                contentDescription = Strings[R.string.player_shuffle_off],
                                modifier = Modifier.alpha(INACTIVE_ALPHA),
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    val currentTrackColor =
        lerp(
            containerColor,
            Color.Black,
            containerColor.luminance().let { if (it < 0.5f) it * 0.05f + 0.2f else 0.4f },
        )
    Surface(color = currentTrackColor, contentColor = currentTrackColor.contentColor()) {
        LibraryListItemHorizontal(
            title = currentTrack.displayTitle,
            subtitle = currentTrack.displayArtistWithAlbum,
            lead = {
                IconButton(onClick = onToggleCurrentTrackFavorite) {
                    AnimatedContent(currentTrackIsFavorite) { animatedIsFavorite ->
                        if (animatedIsFavorite) {
                            Icon(
                                Icons.Filled.Favorite,
                                Strings[R.string.player_now_playing_remove_favorites],
                            )
                        } else {
                            Icon(
                                Icons.Filled.FavoriteBorder,
                                Strings[R.string.player_now_playing_add_favorites],
                            )
                        }
                    }
                }
            },
            actions = {
                OverflowMenu(
                    currentTrack.let {
                        playerMenuItems(playerWrapper, uiManager, libraryIndex) +
                            MenuItem.Divider +
                            removeFromQueueMenuItem(playerWrapper, currentTrackIndex) +
                            trackMenuItems(it, playerWrapper, uiManager)
                    }
                )
            },
            modifier = Modifier.clickable { onTogglePlayQueue() },
            marquee = true,
        )
    }
}

@Composable
private fun PlayQueue(
    playerWrapper: PlayerWrapper,
    uiManager: UiManager,
    playQueue: List<Pair<Any, Track>>,
    lazyListState: LazyListState,
    onTogglePlayQueue: () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val view = LocalView.current

    val currentIndex by
        playerWrapper.state.map(coroutineScope) { it.currentIndex }.collectAsStateWithLifecycle()

    val upNextCount = playQueue.size - currentIndex - 1
    val upNextDuration =
        remember(playQueue, currentIndex) {
            playQueue.drop(currentIndex + 1).sumOf { it.second.duration }.toShortString()
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
                if (reorderInfo == null) playQueue.indexOfFirst { it.first == from.key } to to.index
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
            LibraryListHeader(
                Strings.separate(
                    Strings[R.string.player_up_next],
                    Strings[R.string.count_track].icuFormat(upNextCount),
                    upNextDuration,
                ),
                onClick = onTogglePlayQueue,
            )

            Scrollbar(lazyListState) {
                LazyColumn(state = lazyListState, modifier = Modifier.fillMaxSize()) {
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
                                        targetState = (index - currentIndex).toString(),
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
                                                        HapticFeedbackConstantsCompat.GESTURE_END,
                                                    )
                                                    reorderInfo?.let { (from, to) ->
                                                        playerWrapper.moveTrack(from, to)
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
                                    OverflowMenu(
                                        listOf(removeFromQueueMenuItem(playerWrapper, index)) +
                                            trackMenuItems(track, playerWrapper, uiManager)
                                    )
                                },
                                deemphasized = index <= currentIndex,
                                modifier =
                                    Modifier.clickable { playerWrapper.seekTo(index) }
                                        .background(MaterialTheme.colorScheme.surfaceContainerLow),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun playerMenuItems(
    playerWrapper: PlayerWrapper,
    uiManager: UiManager,
    libraryIndex: LibraryIndex,
): List<MenuItem> {
    return listOf(
        MenuItem.Button(Strings[R.string.player_clear_queue], Icons.Filled.Clear) {
            playerWrapper.clearTracks()
        },
        MenuItem.Button(Strings[R.string.player_save_queue], Icons.Filled.AddBox) {
            val state = playerWrapper.state.value
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
    )
}

private fun removeFromQueueMenuItem(playerWrapper: PlayerWrapper, index: Int?): MenuItem.Button {
    return MenuItem.Button(Strings[R.string.track_remove_from_queue], Icons.Filled.Remove) {
        if (index != null) playerWrapper.removeTrack(index)
    }
}

private enum class LayoutType {
    LANDSCAPE,
    SQUARE,
    PORTRAIT,
}

@Stable
private fun layoutType(width: Int, height: Int): LayoutType {
    return when {
        width.toFloat() / height >= LAYOUT_ASPECT_RATIO_THRESHOLD -> LayoutType.LANDSCAPE
        height.toFloat() / width >= LAYOUT_ASPECT_RATIO_THRESHOLD -> LayoutType.PORTRAIT
        else -> LayoutType.SQUARE
    }
}
