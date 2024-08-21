package org.sunsetware.phocid.ui.views.preferences

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.sunsetware.phocid.Dialog
import org.sunsetware.phocid.MainViewModel
import org.sunsetware.phocid.R
import org.sunsetware.phocid.Strings
import org.sunsetware.phocid.ui.components.DialogBase
import org.sunsetware.phocid.ui.components.UtilitySwitchListItem

@Stable
class PreferencesPlaylistIoSettingsDialog : Dialog() {
    @Composable
    override fun Compose(viewModel: MainViewModel) {
        val preferences by viewModel.preferences.collectAsStateWithLifecycle()
        val (ignoreCase, ignoreLocation, removeInvalid) = preferences.playlistIoSettings

        DialogBase(
            title = Strings[R.string.preferences_playlist_io_settings],
            onConfirmOrDismiss = { viewModel.uiManager.closeDialog() },
        ) {
            Column {
                UtilitySwitchListItem(
                    Strings[R.string.preferences_playlist_io_settings_ignore_case],
                    ignoreCase,
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
                    ignoreLocation,
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
                    removeInvalid,
                    {
                        viewModel.updatePreferences { preferences ->
                            preferences.copy(
                                playlistIoSettings =
                                    preferences.playlistIoSettings.copy(removeInvalid = it)
                            )
                        }
                    },
                )
            }
        }
    }
}
