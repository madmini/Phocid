package org.sunsetware.phocid.ui.views.preferences

import android.os.Environment
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.sunsetware.phocid.Dialog
import org.sunsetware.phocid.MainViewModel
import org.sunsetware.phocid.R
import org.sunsetware.phocid.Strings
import org.sunsetware.phocid.ui.components.DialogBase
import org.sunsetware.phocid.ui.components.UtilitySwitchListItem
import org.sunsetware.phocid.utils.icuFormat

@Stable
class PreferencesPlaylistIoSettingsDialog : Dialog() {
    @Composable
    override fun Compose(viewModel: MainViewModel) {
        val preferences by viewModel.preferences.collectAsStateWithLifecycle()
        var relativeBaseBuffer by remember {
            mutableStateOf(preferences.playlistIoSettings.relativeBase)
        }
        LaunchedEffect(relativeBaseBuffer) {
            viewModel.updatePreferences { preferences ->
                preferences.copy(
                    playlistIoSettings =
                        preferences.playlistIoSettings.copy(relativeBase = relativeBaseBuffer)
                )
            }
        }

        DialogBase(
            title = Strings[R.string.preferences_playlist_io_settings],
            onConfirmOrDismiss = { viewModel.uiManager.closeDialog() },
        ) {
            Column {
                UtilitySwitchListItem(
                    Strings[R.string.preferences_playlist_io_settings_ignore_case],
                    preferences.playlistIoSettings.ignoreCase,
                    {
                        viewModel.updatePreferences { preferences ->
                            preferences.copy(
                                playlistIoSettings =
                                    preferences.playlistIoSettings.copy(ignoreCase = it)
                            )
                        }
                    },
                )
                UtilitySwitchListItem(
                    Strings[R.string.preferences_playlist_io_settings_ignore_location],
                    preferences.playlistIoSettings.ignoreLocation,
                    {
                        viewModel.updatePreferences { preferences ->
                            preferences.copy(
                                playlistIoSettings =
                                    preferences.playlistIoSettings.copy(ignoreLocation = it)
                            )
                        }
                    },
                )
                UtilitySwitchListItem(
                    Strings[R.string.preferences_playlist_io_settings_remove_invalid],
                    preferences.playlistIoSettings.removeInvalid,
                    {
                        viewModel.updatePreferences { preferences ->
                            preferences.copy(
                                playlistIoSettings =
                                    preferences.playlistIoSettings.copy(removeInvalid = it)
                            )
                        }
                    },
                )
                UtilitySwitchListItem(
                    Strings[R.string.preferences_playlist_io_settings_export_relative],
                    preferences.playlistIoSettings.exportRelative,
                    {
                        viewModel.updatePreferences { preferences ->
                            preferences.copy(
                                playlistIoSettings =
                                    preferences.playlistIoSettings.copy(exportRelative = it)
                            )
                        }
                    },
                )
                TextField(
                    modifier = Modifier.padding(horizontal = 24.dp),
                    value = relativeBaseBuffer,
                    onValueChange = { relativeBaseBuffer = it },
                    label = {
                        Text(
                            Strings[R.string.preferences_playlist_io_settings_export_relative_base]
                        )
                    },
                    supportingText = {
                        Text(
                            Strings[
                                    R.string
                                        .preferences_playlist_io_settings_export_relative_base_hint]
                                .icuFormat(
                                    Environment.getExternalStoragePublicDirectory(
                                        Environment.DIRECTORY_MUSIC
                                    )
                                )
                        )
                    },
                    singleLine = true,
                    keyboardOptions =
                        KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            autoCorrectEnabled = false,
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Done,
                        ),
                )
            }
        }
    }
}
