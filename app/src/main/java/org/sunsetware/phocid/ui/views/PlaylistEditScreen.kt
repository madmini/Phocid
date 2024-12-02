@file:OptIn(ExperimentalMaterial3Api::class)

package org.sunsetware.phocid.ui.views

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.UUID
import kotlin.collections.associateBy
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import org.apache.commons.io.FilenameUtils
import org.sunsetware.phocid.Dialog
import org.sunsetware.phocid.MainViewModel
import org.sunsetware.phocid.R
import org.sunsetware.phocid.Strings
import org.sunsetware.phocid.TopLevelScreen
import org.sunsetware.phocid.UNKNOWN
import org.sunsetware.phocid.data.InvalidTrack
import org.sunsetware.phocid.data.Track
import org.sunsetware.phocid.data.sortedBy
import org.sunsetware.phocid.ui.components.Artwork
import org.sunsetware.phocid.ui.components.ArtworkImage
import org.sunsetware.phocid.ui.components.DialogBase
import org.sunsetware.phocid.ui.components.LibraryListItemHorizontal
import org.sunsetware.phocid.ui.components.MenuItem
import org.sunsetware.phocid.ui.components.OverflowMenu
import org.sunsetware.phocid.ui.components.SortingOptionPicker
import org.sunsetware.phocid.ui.components.playlistCollectionMenuItemsWithoutEdit
import org.sunsetware.phocid.utils.icuFormat
import org.sunsetware.phocid.utils.swap

@Stable
class PlaylistEditScreen(private val playlistKey: UUID) : TopLevelScreen() {
    private val lazyListState = LazyListState()

    @Composable
    override fun Compose(viewModel: MainViewModel) {
        val preferences by viewModel.preferences.collectAsStateWithLifecycle()
        val uiManager = viewModel.uiManager
        val playlistManager = viewModel.playlistManager
        val playlists by playlistManager.playlists.collectAsStateWithLifecycle()
        val playlist = playlists[playlistKey]
        val playlistName by
            remember {
                    playlistManager.playlists.map { it[playlistKey]?.displayName }.filterNotNull()
                }
                .collectAsState(playlist?.displayName)
        val artworkCache = viewModel.artworkCache

        LaunchedEffect(playlist) {
            if (playlist == null) {
                uiManager.closeTopLevelScreen(this@PlaylistEditScreen)
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            Strings[R.string.playlist_edit_screen_title].icuFormat(playlistName),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { uiManager.back() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = Strings[R.string.commons_back],
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                uiManager.openDialog(PlaylistEditScreenSortDialog(playlistKey))
                            }
                        ) {
                            Icon(Icons.Filled.SortByAlpha, Strings[R.string.playlist_sort])
                        }
                        OverflowMenu(
                            listOf(
                                MenuItem.Button(
                                    Strings[R.string.playlist_remove_invalid_tracks],
                                    Icons.Filled.DeleteSweep,
                                ) {
                                    if (playlist != null) {
                                        val trackKeys =
                                            playlist.entries
                                                .filter { it.track == null }
                                                .map { it.key }
                                                .toSet()
                                        if (trackKeys.isNotEmpty()) {
                                            uiManager.openDialog(
                                                RemoveFromPlaylistDialog(playlistKey, trackKeys)
                                            )
                                        }
                                    }
                                }
                            ) + playlistCollectionMenuItemsWithoutEdit(playlistKey, uiManager)
                        )
                    },
                )
            }
        ) { scaffoldPadding ->
            Surface(
                modifier = Modifier.fillMaxSize().padding(scaffoldPadding),
                color = MaterialTheme.colorScheme.background,
            ) {
                LazyColumn(state = lazyListState, modifier = Modifier.fillMaxSize()) {
                    playlist?.entries?.forEachIndexed { index, entry ->
                        item(entry.key) {
                            LibraryListItemHorizontal(
                                title = entry.track?.displayTitle ?: UNKNOWN,
                                subtitle =
                                    entry.track?.displayArtistWithAlbum
                                        ?: FilenameUtils.getName(entry.playlistEntry.path),
                                lead = {
                                    ArtworkImage(
                                        cache = artworkCache,
                                        artwork = Artwork.Track(entry.track ?: InvalidTrack),
                                        artworkColorPreference = preferences.artworkColorPreference,
                                    )
                                },
                                actions = {
                                    IconButton(
                                        onClick = {
                                            playlistManager.updatePlaylist(playlistKey) {
                                                if (index > 0) {
                                                    it.copy(
                                                        entries = it.entries.swap(index, index - 1)
                                                    )
                                                } else it
                                            }
                                        }
                                    ) {
                                        Icon(
                                            Icons.Filled.ArrowUpward,
                                            contentDescription = Strings[R.string.list_move_up],
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            playlistManager.updatePlaylist(playlistKey) {
                                                if (index < it.entries.size - 1) {
                                                    it.copy(
                                                        entries = it.entries.swap(index, index + 1)
                                                    )
                                                } else it
                                            }
                                        }
                                    ) {
                                        Icon(
                                            Icons.Filled.ArrowDownward,
                                            contentDescription = Strings[R.string.list_move_down],
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            uiManager.openDialog(
                                                RemoveFromPlaylistDialog(
                                                    playlistKey,
                                                    setOf(entry.key),
                                                )
                                            )
                                        }
                                    ) {
                                        Icon(
                                            Icons.Filled.Remove,
                                            contentDescription = Strings[R.string.commons_remove],
                                        )
                                    }
                                },
                                modifier =
                                    Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null),
                            )
                        }
                    }
                }
            }
        }

        // https://issuetracker.google.com/issues/209652366#comment35
        SideEffect {
            lazyListState.requestScrollToItem(
                index = lazyListState.firstVisibleItemIndex,
                scrollOffset = lazyListState.firstVisibleItemScrollOffset,
            )
        }
    }
}

@Stable
class PlaylistEditScreenSortDialog(private val playlistKey: UUID) : Dialog() {
    @Composable
    override fun Compose(viewModel: MainViewModel) {
        val playlistManager = viewModel.playlistManager
        val playlists by playlistManager.playlists.collectAsStateWithLifecycle()

        var activeSortingOptionId by remember { mutableStateOf(Track.SortingOptions.keys.first()) }
        var sortAscending by remember { mutableStateOf(true) }
        DialogBase(
            title =
                Strings[R.string.playlist_sort_screen_title].icuFormat(
                    playlists[playlistKey]?.displayName
                ),
            onConfirm = {
                playlistManager.updatePlaylist(playlistKey) {
                    val trackIndex =
                        viewModel.libraryIndex.value.tracks.values.associateBy { it.path }
                    it.copy(
                        entries =
                            it.entries.sortedBy(
                                viewModel.preferences.value.sortCollator,
                                Track.SortingOptions[activeSortingOptionId]!!.keys,
                                sortAscending,
                            ) {
                                trackIndex[it.path] ?: InvalidTrack
                            }
                    )
                }
                viewModel.uiManager.closeDialog()
            },
            onDismiss = { viewModel.uiManager.closeDialog() },
        ) {
            SortingOptionPicker(
                Track.SortingOptions,
                activeSortingOptionId = activeSortingOptionId,
                sortAscending = sortAscending,
                onSetSortingOption = { activeSortingOptionId = it },
                onSetSortAscending = { sortAscending = it },
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp),
            )
        }
    }
}
