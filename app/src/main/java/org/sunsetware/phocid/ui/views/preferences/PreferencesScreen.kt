@file:OptIn(ExperimentalMaterial3Api::class)

package org.sunsetware.phocid.ui.views.preferences

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.nio.charset.Charset
import org.sunsetware.phocid.BuildConfig
import org.sunsetware.phocid.MainViewModel
import org.sunsetware.phocid.R
import org.sunsetware.phocid.Strings
import org.sunsetware.phocid.TopLevelScreen
import org.sunsetware.phocid.data.ArtworkColorPreference
import org.sunsetware.phocid.data.DarkThemePreference
import org.sunsetware.phocid.data.LyricsDisplayPreference
import org.sunsetware.phocid.ui.components.UtilityListHeader
import org.sunsetware.phocid.ui.components.UtilityListItem
import org.sunsetware.phocid.ui.components.UtilitySwitchListItem
import org.sunsetware.phocid.utils.combine
import org.sunsetware.phocid.utils.icuFormat

@Stable
object PreferencesScreen : TopLevelScreen() {
    val scrollState: ScrollState = ScrollState(0)

    @Composable
    override fun Compose(viewModel: MainViewModel) {
        val context = LocalContext.current
        val coroutineScope = rememberCoroutineScope()
        val uiManager = viewModel.uiManager
        val preferences by viewModel.preferences.collectAsStateWithLifecycle()
        val filteredCount by
            viewModel.unfilteredTrackIndex
                .combine(coroutineScope, viewModel.libraryIndex) { unfiltered, filtered ->
                    unfiltered.tracks.size - filtered.tracks.size
                }
                .collectAsStateWithLifecycle()
        Scaffold(
            topBar = {
                key(MaterialTheme.colorScheme) {
                    TopAppBar(
                        title = { Text(Strings[R.string.preferences]) },
                        navigationIcon = {
                            IconButton(onClick = { uiManager.back() }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = Strings[R.string.commons_back],
                                )
                            }
                        },
                    )
                }
            }
        ) { scaffoldPadding ->
            Surface(
                modifier = Modifier.fillMaxSize().padding(scaffoldPadding),
                color = MaterialTheme.colorScheme.background,
            ) {
                Column(modifier = Modifier.verticalScroll(scrollState)) {
                    UtilityListHeader(Strings[R.string.preferences_interface])
                    UtilityListItem(
                        title = Strings[R.string.preferences_tabs],
                        subtitle =
                            preferences.tabs.joinToString(
                                Strings[R.string.preferences_tabs_separator]
                            ) {
                                Strings[it.type.stringId]
                            },
                        modifier =
                            Modifier.clickable { uiManager.openDialog(PreferencesTabsDialog()) },
                    )
                    UtilityListItem(
                        title = Strings[R.string.preferences_sorting_language],
                        subtitle =
                            preferences.sortingLocale?.displayName
                                ?: Strings[R.string.preferences_sorting_language_system],
                        modifier =
                            Modifier.clickable {
                                uiManager.openDialog(PreferencesSortingLocaleDialog())
                            },
                    )
                    UtilityListItem(
                        title = Strings[R.string.preferences_lyrics_display],
                        subtitle = Strings[preferences.lyricsDisplay.stringId],
                        modifier =
                            Modifier.clickable {
                                uiManager.openDialog(
                                    PreferencesSingleChoiceDialog<LyricsDisplayPreference>(
                                        title = Strings[R.string.preferences_lyrics_display],
                                        options =
                                            LyricsDisplayPreference.entries.map {
                                                it to Strings[it.stringId]
                                            },
                                        activeOption = { it.lyricsDisplay },
                                        updatePreferences = { preferences, new ->
                                            preferences.copy(lyricsDisplay = new)
                                        },
                                    )
                                )
                            },
                    )
                    UtilityListItem(
                        title = Strings[R.string.preferences_ui_scaling],
                        subtitle =
                            Strings[R.string.preferences_ui_scaling_number].icuFormat(
                                preferences.densityMultiplier
                            ),
                        modifier =
                            Modifier.clickable {
                                uiManager.openDialog(PreferencesDensityMultiplierDialog())
                            },
                    )
                    UtilityListHeader(Strings[R.string.preferences_theme])
                    UtilityListItem(
                        title = Strings[R.string.preferences_dark_theme],
                        subtitle = Strings[preferences.darkTheme.stringId],
                        modifier =
                            Modifier.clickable {
                                uiManager.openDialog(
                                    PreferencesSingleChoiceDialog<DarkThemePreference>(
                                        title = Strings[R.string.preferences_dark_theme],
                                        options =
                                            DarkThemePreference.entries.map {
                                                it to Strings[it.stringId]
                                            },
                                        activeOption = { it.darkTheme },
                                        updatePreferences = { preferences, darkTheme ->
                                            preferences.copy(darkTheme = darkTheme)
                                        },
                                    )
                                )
                            },
                    )
                    UtilityListItem(
                        title = Strings[R.string.preferences_theme_color],
                        subtitle = Strings[preferences.themeColorSource.stringId],
                        modifier =
                            Modifier.clickable {
                                uiManager.openDialog(PreferencesThemeColorDialog())
                            },
                    )
                    UtilitySwitchListItem(
                        title = Strings[R.string.preferences_pure_background_color],
                        checked = preferences.pureBackgroundColor,
                        onCheckedChange = { checked ->
                            viewModel.updatePreferences { it.copy(pureBackgroundColor = checked) }
                        },
                    )
                    UtilitySwitchListItem(
                        title = Strings[R.string.preferences_colored_cards],
                        checked = preferences.coloredCards,
                        onCheckedChange = { checked ->
                            viewModel.updatePreferences { it.copy(coloredCards = checked) }
                        },
                    )
                    UtilitySwitchListItem(
                        title = Strings[R.string.preferences_colored_player],
                        checked = preferences.coloredPlayer,
                        onCheckedChange = { checked ->
                            viewModel.updatePreferences { it.copy(coloredPlayer = checked) }
                        },
                    )
                    UtilityListItem(
                        title = Strings[R.string.preferences_artwork_color],
                        subtitle = Strings[preferences.artworkColorPreference.stringId],
                        modifier =
                            Modifier.clickable {
                                uiManager.openDialog(
                                    PreferencesSingleChoiceDialog<ArtworkColorPreference>(
                                        title = Strings[R.string.preferences_artwork_color],
                                        options =
                                            ArtworkColorPreference.entries.map {
                                                it to Strings[it.stringId]
                                            },
                                        activeOption = { it.artworkColorPreference },
                                        updatePreferences = { preferences, new ->
                                            preferences.copy(artworkColorPreference = new)
                                        },
                                    )
                                )
                            },
                    )
                    UtilityListHeader(Strings[R.string.preferences_indexing])
                    UtilityListItem(
                        title = Strings[R.string.preferences_indexing_artist_separators],
                        subtitle =
                            Strings[R.string.preferences_indexing_artist_separators_subtitle]
                                .icuFormat(preferences.artistMetadataSeparators.size),
                        modifier =
                            Modifier.clickable {
                                uiManager.openDialog(PreferencesArtistSeparatorDialog())
                            },
                    )
                    UtilityListItem(
                        title = Strings[R.string.preferences_indexing_artist_separator_exceptions],
                        subtitle =
                            Strings[
                                    R.string
                                        .preferences_indexing_artist_separator_exceptions_subtitle]
                                .icuFormat(preferences.artistMetadataSeparatorExceptions.size),
                        modifier =
                            Modifier.clickable {
                                uiManager.openDialog(PreferencesArtistSeparatorExceptionDialog())
                            },
                    )
                    UtilityListItem(
                        title = Strings[R.string.preferences_indexing_blacklist],
                        subtitle =
                            Strings[R.string.preferences_indexing_blacklist_subtitle].icuFormat(
                                preferences.blacklist.size,
                                filteredCount,
                            ),
                        modifier =
                            Modifier.clickable {
                                uiManager.openDialog(PreferencesBlacklistDialog())
                            },
                    )
                    UtilityListItem(
                        title = Strings[R.string.preferences_indexing_whitelist],
                        subtitle =
                            Strings[R.string.preferences_indexing_whitelist_subtitle].icuFormat(
                                preferences.whitelist.size
                            ),
                        modifier =
                            Modifier.clickable {
                                uiManager.openDialog(PreferencesWhitelistDialog())
                            },
                    )
                    UtilityListItem(
                        title = Strings[R.string.preferences_indexing_help],
                        modifier =
                            Modifier.clickable {
                                uiManager.openDialog(PreferencesIndexingHelpDialog())
                            },
                    )
                    UtilityListHeader(Strings[R.string.preferences_data])
                    UtilityListItem(
                        title = Strings[R.string.preferences_text_encoding],
                        subtitle =
                            preferences.charsetName
                                ?: Strings[R.string.preferences_text_encoding_auto_detect],
                        modifier =
                            Modifier.clickable {
                                uiManager.openDialog(
                                    PreferencesSingleChoiceDialog<String?>(
                                        title = Strings[R.string.preferences_text_encoding],
                                        options =
                                            listOf(
                                                null to
                                                    Strings[
                                                        R.string
                                                            .preferences_text_encoding_auto_detect]
                                            ) +
                                                Charset.availableCharsets().map {
                                                    it.value.name() to it.value.displayName()
                                                },
                                        activeOption = { it.charsetName },
                                        updatePreferences = { preferences, new ->
                                            preferences.copy(charsetName = new)
                                        },
                                    )
                                )
                            },
                    )
                    UtilityListItem(
                        title = Strings[R.string.preferences_playlist_io_settings],
                        modifier =
                            Modifier.clickable {
                                uiManager.openDialog(PreferencesPlaylistIoSettingsDialog())
                            },
                    )
                    UtilityListHeader(Strings[R.string.preferences_about])
                    UtilityListItem(
                        title = Strings[R.string.preferences_version],
                        subtitle = BuildConfig.VERSION_NAME,
                    )
                    UtilityListItem(
                        title = Strings[R.string.preferences_website],
                        subtitle = Strings[R.string.app_url],
                        modifier =
                            Modifier.clickable {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(Strings[R.string.app_url]))
                                )
                            },
                    )
                    UtilityListItem(
                        title = Strings[R.string.preferences_license],
                        subtitle = Strings[R.string.app_license_name],
                        modifier =
                            Modifier.clickable { uiManager.openDialog(PreferencesLicenseDialog()) },
                    )
                    UtilityListItem(
                        title = Strings[R.string.preferences_third_party_licenses],
                        modifier =
                            Modifier.clickable {
                                uiManager.openDialog(PreferencesThirdPartyLicensesDialog())
                            },
                    )
                }
            }
        }
    }
}
