package org.sunsetware.phocid.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistRemove
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.vector.ImageVector
import java.util.UUID
import org.sunsetware.phocid.R
import org.sunsetware.phocid.Strings
import org.sunsetware.phocid.UiManager
import org.sunsetware.phocid.data.PlayerWrapper
import org.sunsetware.phocid.data.SpecialPlaylistLookup
import org.sunsetware.phocid.data.Track
import org.sunsetware.phocid.data.albumKey
import org.sunsetware.phocid.ui.views.AddToPlaylistDialog
import org.sunsetware.phocid.ui.views.DeletePlaylistDialog
import org.sunsetware.phocid.ui.views.PlaylistEditScreen
import org.sunsetware.phocid.ui.views.PlaylistIoScreen
import org.sunsetware.phocid.ui.views.RemoveFromPlaylistDialog
import org.sunsetware.phocid.ui.views.RenamePlaylistDialog
import org.sunsetware.phocid.ui.views.TrackDetailsDialog
import org.sunsetware.phocid.ui.views.openAlbumCollectionView
import org.sunsetware.phocid.ui.views.openArtistCollectionView
import org.sunsetware.phocid.utils.icuFormat

@Immutable
sealed class MenuItem {
    @Immutable
    data class Button(
        val text: String,
        val icon: ImageVector,
        val dangerous: Boolean = false,
        val onClick: () -> Unit,
    ) : MenuItem()

    @Immutable object Divider : MenuItem()
}

@Stable
fun trackMenuItems(
    track: Track,
    playerWrapper: PlayerWrapper,
    uiManager: UiManager,
): List<MenuItem> {
    val queue =
        listOf(
            MenuItem.Button(Strings[R.string.track_play_next], Icons.Filled.ChevronRight) {
                playerWrapper.playNext(listOf(track))
                uiManager.toast(Strings[R.string.toast_track_queued].icuFormat(1))
            },
            MenuItem.Button(Strings[R.string.track_add_to_queue], Icons.Filled.Add) {
                playerWrapper.addTracks(listOf(track))
                uiManager.toast(Strings[R.string.toast_track_queued].icuFormat(1))
            },
        )
    val playlist =
        MenuItem.Button(Strings[R.string.playlist_add_to], Icons.AutoMirrored.Filled.PlaylistAdd) {
            uiManager.openDialog(AddToPlaylistDialog(listOf(track)))
        }
    val artists =
        track.artists.map { artist ->
            MenuItem.Button(
                Strings[R.string.track_view_artist].icuFormat(artist),
                Icons.Filled.Person,
            ) {
                uiManager.openArtistCollectionView(artist)
            }
        }
    val album =
        listOfNotNull(
            track.albumKey?.let { albumKey ->
                MenuItem.Button(
                    Strings[R.string.track_view_album].icuFormat(albumKey.name),
                    Icons.Filled.Album,
                ) {
                    uiManager.openAlbumCollectionView(albumKey)
                }
            }
        )
    val details =
        MenuItem.Button(Strings[R.string.track_details], Icons.Filled.Info) {
            uiManager.openDialog(TrackDetailsDialog(track))
        }

    return queue + playlist + MenuItem.Divider + artists + album + details
}

@Stable
inline fun collectionMenuItemsWithoutPlay(
    crossinline tracks: () -> List<Track>,
    playerWrapper: PlayerWrapper,
    uiManager: UiManager,
    crossinline continuation: () -> Unit = {},
): List<MenuItem.Button> {
    return listOf(
        MenuItem.Button(Strings[R.string.track_play_next], Icons.Filled.ChevronRight) {
            val tracks = tracks()
            playerWrapper.playNext(tracks)
            uiManager.toast(Strings[R.string.toast_track_queued].icuFormat(tracks.size))
        },
        MenuItem.Button(Strings[R.string.track_add_to_queue], Icons.Filled.Add) {
            val tracks = tracks()
            playerWrapper.addTracks(tracks)
            uiManager.toast(Strings[R.string.toast_track_queued].icuFormat(tracks.size))
            continuation()
        },
        MenuItem.Button(Strings[R.string.playlist_add_to], Icons.AutoMirrored.Filled.PlaylistAdd) {
            uiManager.openDialog(AddToPlaylistDialog(tracks()))
            continuation()
        },
    )
}

@Stable
inline fun collectionMenuItems(
    crossinline tracks: () -> List<Track>,
    playerWrapper: PlayerWrapper,
    uiManager: UiManager,
    crossinline continuation: () -> Unit = {},
): List<MenuItem.Button> {
    return listOf(
        MenuItem.Button(Strings[R.string.track_play], Icons.Filled.PlayArrow) {
            playerWrapper.setTracks(tracks(), null)
            continuation()
        }
    ) + collectionMenuItemsWithoutPlay(tracks, playerWrapper, uiManager, continuation)
}

@Stable
fun playlistCollectionMenuItemsWithoutEdit(key: UUID, uiManager: UiManager): List<MenuItem.Button> {
    return listOfNotNull(
        if (!SpecialPlaylistLookup.containsKey(key)) {
            MenuItem.Button(
                Strings[R.string.playlist_rename],
                Icons.Filled.DriveFileRenameOutline,
            ) {
                uiManager.openDialog(RenamePlaylistDialog(key))
            }
        } else null,
        MenuItem.Button(Strings[R.string.playlist_export], Icons.Outlined.FileUpload) {
            uiManager.openTopLevelScreen(PlaylistIoScreen.export(setOf(key)))
        },
        MenuItem.Button(Strings[R.string.playlist_delete], Icons.Filled.Delete, dangerous = true) {
            uiManager.openDialog(DeletePlaylistDialog(key))
        },
    )
}

@Stable
fun playlistCollectionMenuItems(key: UUID, uiManager: UiManager): List<MenuItem.Button> {
    return listOf(
        MenuItem.Button(Strings[R.string.playlist_edit], Icons.Filled.Edit) {
            uiManager.openTopLevelScreen(PlaylistEditScreen(key))
        }
    ) + playlistCollectionMenuItemsWithoutEdit(key, uiManager)
}

@Stable
inline fun playlistCollectionMultiSelectMenuItems(
    crossinline keys: () -> Set<UUID>,
    uiManager: UiManager,
    crossinline continuation: () -> Unit,
): List<MenuItem.Button> {
    return listOf(
        MenuItem.Button(Strings[R.string.playlist_export], Icons.Outlined.FileUpload) {
            uiManager.openTopLevelScreen(PlaylistIoScreen.export(keys()))
            continuation()
        },
        MenuItem.Button(Strings[R.string.playlist_delete], Icons.Filled.Delete, dangerous = true) {
            uiManager.openDialog(DeletePlaylistDialog(keys()))
            continuation()
        },
    )
}

@Stable
inline fun playlistTrackMenuItems(
    playlistKey: UUID,
    crossinline trackKeys: () -> Set<UUID>,
    uiManager: UiManager,
    crossinline continuation: () -> Unit = {},
): List<MenuItem.Button> {
    return listOf(
        MenuItem.Button(Strings[R.string.playlist_remove_from], Icons.Filled.PlaylistRemove) {
            uiManager.openDialog(RemoveFromPlaylistDialog(playlistKey, trackKeys()))
            continuation()
        }
    )
}

@Stable
fun playlistTrackMenuItems(
    playlistKey: UUID,
    trackKey: UUID,
    uiManager: UiManager,
): List<MenuItem.Button> {
    return playlistTrackMenuItems(playlistKey, { setOf(trackKey) }, uiManager)
}
