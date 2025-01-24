@file:OptIn(ExperimentalMaterial3Api::class)

package org.sunsetware.phocid.ui.views.library

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.UUID
import kotlin.collections.plus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.sunsetware.phocid.MainViewModel
import org.sunsetware.phocid.R
import org.sunsetware.phocid.Strings
import org.sunsetware.phocid.data.Album
import org.sunsetware.phocid.data.AlbumArtist
import org.sunsetware.phocid.data.Artist
import org.sunsetware.phocid.data.ArtworkColorPreference
import org.sunsetware.phocid.data.Folder
import org.sunsetware.phocid.data.Genre
import org.sunsetware.phocid.data.InvalidTrack
import org.sunsetware.phocid.data.LibraryIndex
import org.sunsetware.phocid.data.PlaylistManager
import org.sunsetware.phocid.data.Preferences
import org.sunsetware.phocid.data.RealizedPlaylist
import org.sunsetware.phocid.data.SortingOption
import org.sunsetware.phocid.data.TabStylePreference
import org.sunsetware.phocid.data.Track
import org.sunsetware.phocid.data.albumKey
import org.sunsetware.phocid.data.search
import org.sunsetware.phocid.data.sorted
import org.sunsetware.phocid.data.sortedBy
import org.sunsetware.phocid.format
import org.sunsetware.phocid.ui.components.Artwork
import org.sunsetware.phocid.ui.components.ArtworkImage
import org.sunsetware.phocid.ui.components.DefaultPagerState
import org.sunsetware.phocid.ui.components.EmptyListIndicator
import org.sunsetware.phocid.ui.components.LibraryListItemCard
import org.sunsetware.phocid.ui.components.LibraryListItemHorizontal
import org.sunsetware.phocid.ui.components.MenuItem
import org.sunsetware.phocid.ui.components.MultiSelectManager
import org.sunsetware.phocid.ui.components.MultiSelectState
import org.sunsetware.phocid.ui.components.OverflowMenu
import org.sunsetware.phocid.ui.components.Scrollbar
import org.sunsetware.phocid.ui.components.SelectableList
import org.sunsetware.phocid.ui.components.SingleLineText
import org.sunsetware.phocid.ui.components.TabIndicator
import org.sunsetware.phocid.ui.components.collectionMenuItems
import org.sunsetware.phocid.ui.components.multiSelectClickable
import org.sunsetware.phocid.ui.components.playlistCollectionMenuItems
import org.sunsetware.phocid.ui.components.playlistCollectionMultiSelectMenuItems
import org.sunsetware.phocid.ui.components.trackMenuItems
import org.sunsetware.phocid.ui.theme.hashColor
import org.sunsetware.phocid.utils.combine

@Immutable
data class LibraryScreenHomeViewItem(
    val key: Any,
    val title: String,
    val subtitle: String,
    val artwork: Artwork,
    val tracks: List<Track>,
    val menuItems: (MainViewModel) -> List<MenuItem>,
    val multiSelectMenuItems:
        (
            others: List<LibraryScreenHomeViewItem>,
            viewModel: MainViewModel,
            continuation: () -> Unit,
        ) -> List<MenuItem.Button>,
    val onClick: (MainViewModel) -> Unit,
) : LibraryScreenItem<LibraryScreenHomeViewItem> {
    override fun getMultiSelectMenuItems(
        others: List<LibraryScreenHomeViewItem>,
        viewModel: MainViewModel,
        continuation: () -> Unit,
    ): List<MenuItem.Button> {
        return multiSelectMenuItems(others, viewModel, continuation)
    }
}

class LibraryScreenHomeViewState(
    coroutineScope: CoroutineScope,
    preferences: StateFlow<Preferences>,
    libraryIndex: StateFlow<LibraryIndex>,
    playlistManager: PlaylistManager,
    searchQuery: StateFlow<String>,
) : AutoCloseable {
    val pagerState = DefaultPagerState { preferences.value.tabs.size }
    val tabStates =
        LibraryScreenTabType.entries.associateWith { tabType ->
            val items =
                if (tabType != LibraryScreenTabType.PLAYLISTS) {
                    preferences.combine(
                        coroutineScope,
                        libraryIndex,
                        searchQuery,
                        transform =
                            when (tabType) {
                                LibraryScreenTabType.TRACKS -> ::trackItems
                                LibraryScreenTabType.ALBUMS -> ::albumItems
                                LibraryScreenTabType.ARTISTS -> ::artistItems
                                LibraryScreenTabType.ALBUM_ARTISTS -> ::albumArtistItems
                                LibraryScreenTabType.GENRES -> ::genreItems
                                LibraryScreenTabType.FOLDERS -> ::folderItems
                                LibraryScreenTabType.PLAYLISTS -> throw Error() // Impossible
                            },
                    )
                } else {
                    preferences.combine(
                        coroutineScope,
                        playlistManager.playlists,
                        searchQuery,
                        transform = ::playlistItems,
                    )
                }

            LibraryScreenHomeViewTabState(MultiSelectState(coroutineScope, items))
        }
    val tabRowScrollState = ScrollState(0)
    private val _activeMultiSelectState =
        MutableStateFlow(null as MultiSelectState<LibraryScreenHomeViewItem>?)
    val activeMultiSelectState = _activeMultiSelectState.asStateFlow()
    private val activeMultiSelectStateJobs =
        tabStates.map { (tabType, tabState) ->
            coroutineScope.launch {
                tabState.multiSelectState.items
                    .onEach { items ->
                        if (items.selection.isNotEmpty()) {
                            tabStates
                                .filterKeys { it != tabType }
                                .values
                                .forEach { it.multiSelectState.clearSelection() }
                            _activeMultiSelectState.update { tabState.multiSelectState }
                        }
                    }
                    .collect()
            }
        }

    override fun close() {
        activeMultiSelectStateJobs.forEach { it.cancel() }
        tabStates.values.forEach { it.close() }
    }

    private fun trackItems(
        preferences: Preferences,
        libraryIndex: LibraryIndex,
        searchQuery: String,
    ): List<LibraryScreenHomeViewItem> {
        val tab = preferences.tabSettings[LibraryScreenTabType.TRACKS]!!
        val tracks =
            libraryIndex.tracks.values
                .search(searchQuery, preferences.searchCollator)
                .sorted(preferences.sortCollator, tab.sortingKeys, tab.sortAscending)
        return tracks.mapIndexed { index, track ->
            LibraryScreenHomeViewItem(
                key = track.id,
                title = track.displayTitle,
                subtitle = track.displayArtistWithAlbum,
                artwork = Artwork.Track(track),
                tracks = listOf(track),
                menuItems = { trackMenuItems(track, it.playerManager, it.uiManager) },
                multiSelectMenuItems = { others, viewModel, continuation ->
                    collectionMenuItems(
                        { listOf(track) + others.flatMap { it.tracks } },
                        viewModel.playerManager,
                        viewModel.uiManager,
                        continuation,
                    )
                },
            ) {
                it.playerManager.setTracks(tracks, index)
            }
        }
    }

    private fun albumItems(
        preferences: Preferences,
        libraryIndex: LibraryIndex,
        searchQuery: String,
    ): List<LibraryScreenHomeViewItem> {
        val tab = preferences.tabSettings[LibraryScreenTabType.ALBUMS]!!
        val albums =
            libraryIndex.albums
                .asIterable()
                .search(searchQuery, preferences.searchCollator) { it.value }
                .sortedBy(preferences.sortCollator, tab.sortingKeys, tab.sortAscending) { it.value }
        return albums.map { (key, album) ->
            LibraryScreenHomeViewItem(
                key = key.composeKey,
                title = album.name,
                subtitle = album.displayAlbumArtist,
                artwork = Artwork.Track(album.tracks.firstOrNull() ?: InvalidTrack),
                tracks = album.tracks,
                menuItems = {
                    collectionMenuItems({ album.tracks }, it.playerManager, it.uiManager)
                },
                multiSelectMenuItems = { others, viewModel, continuation ->
                    collectionMenuItems(
                        { album.tracks + others.flatMap { it.tracks } },
                        viewModel.playerManager,
                        viewModel.uiManager,
                        continuation,
                    )
                },
            ) {
                it.uiManager.openAlbumCollectionView(album.albumKey)
            }
        }
    }

    private fun artistItems(
        preferences: Preferences,
        libraryIndex: LibraryIndex,
        searchQuery: String,
    ): List<LibraryScreenHomeViewItem> {
        val tab = preferences.tabSettings[LibraryScreenTabType.ARTISTS]!!
        val artists =
            libraryIndex.artists.values
                .search(searchQuery, preferences.searchCollator)
                .sorted(preferences.sortCollator, tab.sortingKeys, tab.sortAscending)
        return artists.map { artist ->
            LibraryScreenHomeViewItem(
                key = artist.name,
                title = artist.name,
                subtitle = artist.displayStatistics,
                artwork = Artwork.Track(artist.tracks.firstOrNull() ?: InvalidTrack),
                tracks = artist.tracks,
                menuItems = {
                    collectionMenuItems({ artist.tracks }, it.playerManager, it.uiManager)
                },
                multiSelectMenuItems = { others, viewModel, continuation ->
                    collectionMenuItems(
                        { artist.tracks + others.flatMap { it.tracks } },
                        viewModel.playerManager,
                        viewModel.uiManager,
                        continuation,
                    )
                },
            ) {
                it.uiManager.openArtistCollectionView(artist.name)
            }
        }
    }

    private fun albumArtistItems(
        preferences: Preferences,
        libraryIndex: LibraryIndex,
        searchQuery: String,
    ): List<LibraryScreenHomeViewItem> {
        val tab = preferences.tabSettings[LibraryScreenTabType.ALBUM_ARTISTS]!!
        val albumArtists =
            libraryIndex.albumArtists.values
                .search(searchQuery, preferences.searchCollator)
                .sorted(preferences.sortCollator, tab.sortingKeys, tab.sortAscending)
        return albumArtists.map { albumArtist ->
            LibraryScreenHomeViewItem(
                key = albumArtist.name,
                title = albumArtist.name,
                subtitle = albumArtist.displayStatistics,
                artwork = Artwork.Track(albumArtist.tracks.firstOrNull() ?: InvalidTrack),
                tracks = albumArtist.tracks,
                menuItems = {
                    collectionMenuItems({ albumArtist.tracks }, it.playerManager, it.uiManager)
                },
                multiSelectMenuItems = { others, viewModel, continuation ->
                    collectionMenuItems(
                        { albumArtist.tracks + others.flatMap { it.tracks } },
                        viewModel.playerManager,
                        viewModel.uiManager,
                        continuation,
                    )
                },
            ) {
                it.uiManager.openAlbumArtistCollectionView(albumArtist.name)
            }
        }
    }

    private fun genreItems(
        preferences: Preferences,
        libraryIndex: LibraryIndex,
        searchQuery: String,
    ): List<LibraryScreenHomeViewItem> {
        val tab = preferences.tabSettings[LibraryScreenTabType.GENRES]!!
        val genres =
            libraryIndex.genres.values
                .search(searchQuery, preferences.searchCollator)
                .sorted(preferences.sortCollator, tab.sortingKeys, tab.sortAscending)
        return genres.map { genre ->
            LibraryScreenHomeViewItem(
                key = genre.name,
                title = genre.name,
                subtitle = genre.displayStatistics,
                artwork = Artwork.Track(genre.tracks.firstOrNull() ?: InvalidTrack),
                tracks = genre.tracks,
                menuItems = {
                    collectionMenuItems({ genre.tracks }, it.playerManager, it.uiManager)
                },
                multiSelectMenuItems = { others, viewModel, continuation ->
                    collectionMenuItems(
                        { genre.tracks + others.flatMap { it.tracks } },
                        viewModel.playerManager,
                        viewModel.uiManager,
                        continuation,
                    )
                },
            ) {
                it.uiManager.openGenreCollectionView(genre.name)
            }
        }
    }

    private fun folderItems(
        preferences: Preferences,
        libraryIndex: LibraryIndex,
        searchQuery: String,
    ): List<LibraryScreenHomeViewItem> {
        val tab = preferences.tabSettings[LibraryScreenTabType.FOLDERS]!!
        val rootFolder = libraryIndex.folders[libraryIndex.rootFolder]!!
        val filteredChildFolders =
            rootFolder.childFolders
                .map { libraryIndex.folders[it]!! }
                .search(searchQuery, preferences.searchCollator)
                .sorted(preferences.sortCollator, tab.sortingKeys, tab.sortAscending)
        // Sorting is required here because onClick is "baked" with this order.
        val filteredSortedChildTracks =
            rootFolder.childTracks
                .search(searchQuery, preferences.searchCollator)
                .sorted(preferences.sortCollator, tab.sortingKeys, tab.sortAscending)
        val folderItems =
            filteredChildFolders.map { folder ->
                folder to
                    LibraryScreenHomeViewItem(
                        key = folder.path,
                        title = folder.fileName,
                        subtitle = folder.displayStatistics,
                        artwork = Artwork.Icon(Icons.Outlined.Folder, folder.path.hashColor()),
                        tracks = folder.childTracks,
                        menuItems = {
                            collectionMenuItems(
                                { folder.childTracks },
                                it.playerManager,
                                it.uiManager,
                            )
                        },
                        multiSelectMenuItems = { others, viewModel, continuation ->
                            collectionMenuItems(
                                { folder.childTracks + others.flatMap { it.tracks } },
                                viewModel.playerManager,
                                viewModel.uiManager,
                                continuation,
                            )
                        },
                    ) {
                        it.uiManager.openFolderCollectionView(folder.path)
                    }
            }
        val trackItems =
            filteredSortedChildTracks.mapIndexed { index, track ->
                track to
                    LibraryScreenHomeViewItem(
                        key = track.id,
                        title = track.fileName,
                        subtitle = track.duration.format(),
                        artwork = Artwork.Track(track),
                        tracks = listOf(track),
                        menuItems = { trackMenuItems(track, it.playerManager, it.uiManager) },
                        multiSelectMenuItems = { others, viewModel, continuation ->
                            collectionMenuItems(
                                { listOf(track) + others.flatMap { it.tracks } },
                                viewModel.playerManager,
                                viewModel.uiManager,
                                continuation,
                            )
                        },
                    ) {
                        it.playerManager.setTracks(filteredSortedChildTracks, index)
                    }
            }
        return (folderItems + trackItems)
            .sortedBy(preferences.sortCollator, tab.sortingKeys, tab.sortAscending) { it.first }
            .map { it.second }
    }

    private fun playlistItems(
        preferences: Preferences,
        playlists: Map<UUID, RealizedPlaylist>,
        searchQuery: String,
    ): List<LibraryScreenHomeViewItem> {
        val tab = preferences.tabSettings[LibraryScreenTabType.PLAYLISTS]!!
        val filteredSortedPlaylists =
            playlists
                .asIterable()
                .search(searchQuery, preferences.searchCollator) { it.value }
                .sortedBy(preferences.sortCollator, tab.sortingKeys, tab.sortAscending) { it.value }
        return filteredSortedPlaylists.map { (key, playlist) ->
            LibraryScreenHomeViewItem(
                key = key,
                title = playlist.displayName,
                subtitle = playlist.displayStatistics,
                artwork =
                    playlist.specialType?.let { Artwork.Icon(it.icon, it.color) }
                        ?: Artwork.Track(playlist.entries.firstOrNull()?.track ?: InvalidTrack),
                tracks = playlist.validTracks,
                menuItems = {
                    collectionMenuItems({ playlist.validTracks }, it.playerManager, it.uiManager) +
                        MenuItem.Divider +
                        playlistCollectionMenuItems(key, it.uiManager)
                },
                multiSelectMenuItems = { others, viewModel, continuation ->
                    collectionMenuItems(
                        { playlist.validTracks + others.flatMap { it.tracks } },
                        viewModel.playerManager,
                        viewModel.uiManager,
                        continuation,
                    ) +
                        playlistCollectionMultiSelectMenuItems(
                            { setOf(key) + others.map { it.key as UUID } },
                            viewModel.uiManager,
                            continuation,
                        )
                },
            ) {
                it.uiManager.openPlaylistCollectionView(key)
            }
        }
    }
}

@Stable
data class LibraryScreenHomeViewTabState(
    val multiSelectState: MultiSelectState<LibraryScreenHomeViewItem>,
    val lazyGridState: LazyGridState = LazyGridState(0, 0),
) : AutoCloseable {
    override fun close() {
        multiSelectState.close()
    }
}

@Composable
fun LibraryScreenHomeView(
    state: LibraryScreenHomeViewState,
    viewModel: MainViewModel = viewModel(),
) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()

    val pagerState = state.pagerState

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column {
            ViewTabRow(preferences, state)
            HorizontalPager(state = pagerState) { i ->
                if (preferences.tabs.size > i && state.tabStates.size > i) {
                    val tab = preferences.tabs[i]
                    val (multiSelectState, lazyGridState) = state.tabStates[tab.type]!!
                    val items by multiSelectState.items.collectAsStateWithLifecycle()
                    LibraryList(
                        gridState = lazyGridState,
                        gridSize = tab.gridSize,
                        items = items,
                        multiSelectManager = multiSelectState,
                        artworkColorPreference = preferences.artworkColorPreference,
                        artworkShape = preferences.shapePreference.artworkShape,
                        cardShape = preferences.shapePreference.cardShape,
                        coloredCards = preferences.coloredCards,
                    )
                } else {
                    // Not providing a composable will cause internal crash in pager
                    Box {}
                }
            }
        }
    }
}

@Composable
private fun ViewTabRow(preferences: Preferences, state: LibraryScreenHomeViewState) {
    val coroutineScope = rememberCoroutineScope()
    val currentTabIndex = state.pagerState.targetPage.coerceIn(0, preferences.tabs.size - 1)

    @Composable
    fun tabs() {
        preferences.tabs.forEachIndexed { i, tab ->
            Tab(
                selected = i == currentTabIndex,
                onClick = {
                    if (state.pagerState.targetPage == i) {
                        coroutineScope.launch {
                            state.tabStates[tab.type]?.lazyGridState?.animateScrollToItem(0)
                        }
                    } else {
                        coroutineScope.launch { state.pagerState.animateScrollToPage(i) }
                    }
                },
                text =
                    if (preferences.tabStyle == TabStylePreference.ICON_ONLY) {
                        null
                    } else {
                        {
                            CompositionLocalProvider(
                                LocalContentColor provides
                                    if (i == currentTabIndex) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (preferences.tabStyle == TabStylePreference.TEXT_AND_ICON) {
                                        Icon(
                                            tab.type.icon,
                                            null,
                                            modifier = Modifier.padding(end = 8.dp),
                                        )
                                    }

                                    SingleLineText(
                                        Strings[tab.type.stringId],
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    },
                icon =
                    if (preferences.tabStyle != TabStylePreference.ICON_ONLY) {
                        null
                    } else {
                        {
                            Icon(
                                tab.type.icon,
                                contentDescription = Strings[tab.type.stringId],
                                tint =
                                    if (i == currentTabIndex) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
            )
        }
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        if (preferences.scrollableTabs) {
            HorizontalDivider(modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth())
            PrimaryScrollableTabRow(
                scrollState = state.tabRowScrollState,
                selectedTabIndex = currentTabIndex,
                indicator = { TabIndicator(state.pagerState) },
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                tabs()
            }
        } else {
            PrimaryTabRow(
                selectedTabIndex = currentTabIndex,
                indicator = { TabIndicator(state.pagerState) },
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                tabs()
            }
        }
    }
}

@Composable
private fun LibraryList(
    gridState: LazyGridState,
    gridSize: Int,
    items: SelectableList<LibraryScreenHomeViewItem>,
    multiSelectManager: MultiSelectManager,
    artworkColorPreference: ArtworkColorPreference,
    artworkShape: Shape,
    cardShape: Shape,
    coloredCards: Boolean,
) {
    val viewModel = viewModel<MainViewModel>()
    val haptics = LocalHapticFeedback.current

    if (items.isEmpty()) {
        EmptyListIndicator()
    } else if (gridSize == 0) {
        Scrollbar(gridState) {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(1),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(items, { _, (info, _) -> info.key }) { index, (info, selected) ->
                    with(info) {
                        LibraryListItemHorizontal(
                            title = title,
                            subtitle = subtitle,
                            lead = {
                                ArtworkImage(
                                    artwork = artwork,
                                    artworkColorPreference = artworkColorPreference,
                                    shape = artworkShape,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            },
                            actions = { OverflowMenu(menuItems(viewModel)) },
                            modifier =
                                Modifier.multiSelectClickable(
                                        items,
                                        index,
                                        multiSelectManager,
                                        haptics,
                                    ) {
                                        info.onClick(viewModel)
                                    }
                                    .animateItem(fadeInSpec = null, fadeOutSpec = null),
                            selected = selected,
                        )
                    }
                }
            }
        }
    } else {
        Scrollbar(gridState) {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(gridSize),
                contentPadding = PaddingValues(2.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(items, { _, (info, _) -> info.key }) { index, (info, selected) ->
                    with(info) {
                        LibraryListItemCard(
                            title = title,
                            subtitle = subtitle,
                            color =
                                if (coloredCards) artwork.getColor(artworkColorPreference)
                                else MaterialTheme.colorScheme.surfaceContainerHighest,
                            shape = cardShape,
                            image = {
                                ArtworkImage(
                                    artwork = artwork,
                                    artworkColorPreference = artworkColorPreference,
                                    shape = RoundedCornerShape(0.dp),
                                    modifier = Modifier.fillMaxSize(),
                                )
                            },
                            menuItems = menuItems(viewModel),
                            modifier =
                                Modifier.padding(2.dp)
                                    .multiSelectClickable(
                                        items,
                                        index,
                                        multiSelectManager,
                                        haptics,
                                    ) {
                                        info.onClick(viewModel)
                                    }
                                    .animateItem(fadeInSpec = null, fadeOutSpec = null),
                            selected = selected,
                        )
                    }
                }
            }
        }
    }

    // https://issuetracker.google.com/issues/209652366#comment35
    SideEffect {
        gridState.requestScrollToItem(
            index = gridState.firstVisibleItemIndex,
            scrollOffset = gridState.firstVisibleItemScrollOffset,
        )
    }
}

@Immutable
@Serializable
data class LibraryScreenTabInfo(
    val type: LibraryScreenTabType,
    val gridSize: Int = 0,
    val sortingOptionId: String = type.sortingOptions.keys.first(),
    val sortAscending: Boolean = true,
) {
    val sortingKeys
        get() = (type.sortingOptions[sortingOptionId] ?: type.sortingOptions.values.first()).keys
}

@Immutable
@Serializable
enum class LibraryScreenTabType(
    val stringId: Int,
    val sortingOptions: Map<String, SortingOption>,
    val icon: ImageVector,
) {
    TRACKS(R.string.tab_tracks, Track.SortingOptions, Icons.Outlined.MusicNote),
    ALBUMS(R.string.tab_albums, Album.CollectionSortingOptions, Icons.Outlined.Album),
    ARTISTS(R.string.tab_artists, Artist.CollectionSortingOptions, Icons.Outlined.PersonOutline),
    ALBUM_ARTISTS(
        R.string.tab_album_artists,
        AlbumArtist.CollectionSortingOptions,
        Icons.Outlined.AccountCircle,
    ),
    GENRES(R.string.tab_genres, Genre.CollectionSortingOptions, Icons.Outlined.Category),
    PLAYLISTS(
        R.string.tab_playlists,
        RealizedPlaylist.CollectionSortingOptions,
        Icons.AutoMirrored.Outlined.QueueMusic,
    ),
    FOLDERS(R.string.tab_folders, Folder.SortingOptions, Icons.Outlined.Folder),
}
