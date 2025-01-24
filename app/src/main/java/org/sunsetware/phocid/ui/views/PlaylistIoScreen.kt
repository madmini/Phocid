@file:OptIn(ExperimentalMaterial3Api::class)

package org.sunsetware.phocid.ui.views

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MimeTypes
import java.util.UUID
import kotlin.collections.forEach
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.apache.commons.io.FilenameUtils
import org.sunsetware.phocid.MainViewModel
import org.sunsetware.phocid.R
import org.sunsetware.phocid.Strings
import org.sunsetware.phocid.TopLevelScreen
import org.sunsetware.phocid.data.RealizedPlaylist
import org.sunsetware.phocid.data.parseM3u
import org.sunsetware.phocid.data.toM3u
import org.sunsetware.phocid.ui.components.EmptyListIndicator
import org.sunsetware.phocid.ui.components.SingleLineText
import org.sunsetware.phocid.ui.components.UtilityCheckBoxListItem
import org.sunsetware.phocid.ui.theme.Typography
import org.sunsetware.phocid.ui.views.preferences.PreferencesPlaylistIoSettingsDialog
import org.sunsetware.phocid.utils.icuFormat

@Stable
class PlaylistIoScreen
private constructor(isImportTab: Boolean, initialExportSelection: Set<UUID>) : TopLevelScreen() {
    var isImportTab by mutableStateOf(isImportTab)

    val importLazyListState = LazyListState()
    var importSelection by mutableStateOf(emptySet<Uri>())
    val exportLazyListState = LazyListState()
    var exportSelection by mutableStateOf(initialExportSelection)

    var m3uFiles by mutableStateOf(emptyList<DocumentFile>())

    companion object {
        fun import(): PlaylistIoScreen {
            return PlaylistIoScreen(true, emptySet())
        }

        fun export(initialExportSelection: Set<UUID>): PlaylistIoScreen {
            return PlaylistIoScreen(false, initialExportSelection)
        }
    }

    @Composable
    override fun Compose(viewModel: MainViewModel) {
        val context = LocalContext.current
        val playlistManager = viewModel.playlistManager
        val playlists by playlistManager.playlists.collectAsStateWithLifecycle()
        val uiManager = viewModel.uiManager
        val playlistIoDirectoryUri by viewModel.playlistIoDirectory.collectAsStateWithLifecycle()
        val playlistIoDirectory =
            remember(playlistIoDirectoryUri) {
                playlistIoDirectoryUri?.let { DocumentFile.fromTreeUri(context, it) }
            }

        LaunchedEffect(playlistIoDirectory) {
            withContext(Dispatchers.IO) {
                while (isActive) {
                    m3uFiles =
                        playlistIoDirectory?.listFiles()?.filter {
                            val name = it.name
                            name != null &&
                                (name.endsWith(".m3u", true) || name.endsWith(".m3u8", true))
                        } ?: emptyList()
                    delay(1.seconds)
                }
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(Strings[R.string.playlist_import_export]) },
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
                                uiManager.openDialog(PreferencesPlaylistIoSettingsDialog())
                            }
                        ) {
                            Icon(
                                Icons.Filled.Settings,
                                contentDescription =
                                    Strings[R.string.preferences_playlist_io_settings],
                            )
                        }
                    },
                )
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = isImportTab,
                        onClick = { isImportTab = true },
                        icon = {
                            Icon(Icons.Outlined.FileDownload, Strings[R.string.commons_import])
                        },
                        label = { Text(Strings[R.string.commons_import]) },
                    )
                    NavigationBarItem(
                        selected = !isImportTab,
                        onClick = { isImportTab = false },
                        icon = {
                            Icon(Icons.Outlined.FileUpload, Strings[R.string.commons_export])
                        },
                        label = { Text(Strings[R.string.commons_export]) },
                    )
                }
            },
        ) { scaffoldPadding ->
            Surface(
                modifier = Modifier.fillMaxSize().padding(scaffoldPadding),
                color = MaterialTheme.colorScheme.background,
            ) {
                Column {
                    DirectoryPicker(
                        playlistIoDirectoryName = playlistIoDirectory?.name,
                        onLaunchOpenDocumentTreeIntent = {
                            viewModel.uiManager.intentLauncher.get()?.openDocumentTree { uri ->
                                if (uri != null) viewModel.playlistIoDirectory.update { uri }
                            }
                        },
                    )
                    if (isImportTab)
                        ImportTab(
                            enabled = playlistIoDirectory != null && importSelection.isNotEmpty(),
                            onImport = { files ->
                                files.forEach { file ->
                                    context.contentResolver.openInputStream(file.uri)?.use {
                                        inputStream ->
                                        playlistManager.addPlaylist(
                                            parseM3u(
                                                FilenameUtils.getBaseName(file.name),
                                                inputStream.readBytes(),
                                                viewModel.libraryIndex.value.tracks.values
                                                    .map { it.path }
                                                    .toSet(),
                                                viewModel.preferences.value.playlistIoSettings,
                                                if (
                                                    FilenameUtils.getExtension(file.name)
                                                        .equals("m3u8", true)
                                                )
                                                    Charsets.UTF_8.name()
                                                else viewModel.preferences.value.charsetName,
                                            )
                                        )
                                    }
                                }

                                uiManager.toast(
                                    Strings[R.string.toast_playlist_imported].icuFormat(files.size)
                                )
                                importSelection = emptySet()
                            },
                        )
                    else
                        ExportTab(
                            enabled = playlistIoDirectory != null && exportSelection.isNotEmpty(),
                            playlists = playlists,
                            onExport = { playlists ->
                                var failureCount = 0
                                playlists.forEach { playlist ->
                                    if (playlistIoDirectory != null) {
                                        val file =
                                            playlistIoDirectory.createFile(
                                                MimeTypes.APPLICATION_M3U8,
                                                playlist.displayName,
                                            )
                                        if (file != null) {
                                            context.contentResolver
                                                .openOutputStream(file.uri)
                                                ?.use { outputStream ->
                                                    outputStream.write(
                                                        playlist
                                                            .toM3u(
                                                                viewModel.preferences.value
                                                                    .playlistIoSettings
                                                            )
                                                            .toByteArray(Charsets.UTF_8)
                                                    )
                                                }
                                        } else {
                                            failureCount++
                                        }
                                    }
                                }
                                if (failureCount == 0) {
                                    uiManager.toast(
                                        Strings[R.string.toast_playlist_exported].icuFormat(
                                            playlists.size
                                        )
                                    )
                                } else {
                                    uiManager.toast(
                                        Strings[R.string.toast_playlist_exported_with_failure]
                                            .icuFormat(playlists.size - failureCount, failureCount)
                                    )
                                }
                                exportSelection = emptySet()
                            },
                        )
                }
            }
        }
    }

    @Composable
    private inline fun ImportTab(
        enabled: Boolean,
        crossinline onImport: (List<DocumentFile>) -> Unit,
    ) {
        Column {
            Box(modifier = Modifier.weight(1f)) {
                if (m3uFiles.isEmpty()) {
                    EmptyListIndicator()
                } else {
                    LazyColumn(state = importLazyListState, modifier = Modifier.fillMaxSize()) {
                        items(m3uFiles, { it.uri }) { file ->
                            UtilityCheckBoxListItem(
                                text = file.name ?: file.uri.toString(),
                                checked = importSelection.contains(file.uri),
                                onCheckedChange = {
                                    if (it) {
                                        importSelection += file.uri
                                    } else {
                                        importSelection -= file.uri
                                    }
                                },
                            )
                        }
                    }
                }
            }

            Button(
                onClick = { onImport(m3uFiles.filter { importSelection.contains(it.uri) }) },
                enabled = enabled,
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
            ) {
                Text(Strings[R.string.playlist_io_import_count].icuFormat(importSelection.size))
            }
        }
    }

    @Composable
    private inline fun ExportTab(
        enabled: Boolean,
        playlists: Map<UUID, RealizedPlaylist>,
        crossinline onExport: (List<RealizedPlaylist>) -> Unit,
    ) {
        Column {
            Box(modifier = Modifier.weight(1f)) {
                if (playlists.isEmpty()) {
                    EmptyListIndicator()
                } else {
                    LazyColumn(state = exportLazyListState, modifier = Modifier.fillMaxSize()) {
                        items(playlists.toList(), { (key, _) -> key }) { (key, playlist) ->
                            UtilityCheckBoxListItem(
                                text = playlist.displayName,
                                checked = exportSelection.contains(key),
                                onCheckedChange = {
                                    if (it) {
                                        exportSelection += key
                                    } else {
                                        exportSelection -= key
                                    }
                                },
                            )
                        }
                    }
                }
            }

            Button(
                onClick = {
                    onExport(playlists.filter { exportSelection.contains(it.key) }.map { it.value })
                },
                enabled = enabled,
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
            ) {
                Text(Strings[R.string.playlist_io_export_count].icuFormat(exportSelection.size))
            }
        }
    }

    @Composable
    private fun DirectoryPicker(
        playlistIoDirectoryName: String?,
        onLaunchOpenDocumentTreeIntent: () -> Unit,
    ) {
        Surface(
            modifier = Modifier.padding(16.dp).height(40.dp),
            shape = CircleShape,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            onClick = onLaunchOpenDocumentTreeIntent,
        ) {
            Row(
                modifier = Modifier.padding(start = 12.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.FolderOpen,
                    Strings[R.string.playlist_io_select_folder],
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(12.dp))
                SingleLineText(
                    playlistIoDirectoryName?.let {
                        Strings[R.string.playlist_io_location].icuFormat(it)
                    } ?: Strings[R.string.playlist_io_location_not_set],
                    style = Typography.labelLarge,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
