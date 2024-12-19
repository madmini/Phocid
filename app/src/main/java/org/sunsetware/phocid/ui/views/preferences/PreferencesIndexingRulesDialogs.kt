package org.sunsetware.phocid.ui.views.preferences

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import org.sunsetware.phocid.data.UnfilteredTrackIndex
import org.sunsetware.phocid.ui.components.DialogBase
import org.sunsetware.phocid.ui.components.SingleLineText
import org.sunsetware.phocid.ui.components.UtilityListItem
import org.sunsetware.phocid.ui.theme.Typography
import org.sunsetware.phocid.utils.icuFormat
import org.sunsetware.phocid.utils.removeAt

@Stable
class PreferencesBlacklistDialog() : Dialog() {
    @Composable
    override fun Compose(viewModel: MainViewModel) {
        val preferences by viewModel.preferences.collectAsStateWithLifecycle()
        val unfilteredTrackIndex by viewModel.unfilteredTrackIndex.collectAsStateWithLifecycle()
        IndexRulesDialog(
            title = Strings[R.string.preferences_indexing_blacklist],
            onConfirmOrDismiss = { viewModel.uiManager.closeDialog() },
            rules = preferences.blacklist,
            unfilteredTrackIndex = unfilteredTrackIndex,
            onRemoveRule = { index ->
                viewModel.updatePreferences { it.copy(blacklist = it.blacklist.removeAt(index)) }
            },
            onAddRule = { rule ->
                viewModel.updatePreferences { it.copy(blacklist = it.blacklist + rule) }
            },
            validate = ::regexValidate,
            matchCount = ::regexMatchCount,
            errorText = Strings[R.string.preferences_indexing_invalid_regex],
            footnote = null,
        )
    }
}

@Stable
class PreferencesWhitelistDialog() : Dialog() {
    @Composable
    override fun Compose(viewModel: MainViewModel) {
        val preferences by viewModel.preferences.collectAsStateWithLifecycle()
        val unfilteredTrackIndex by viewModel.unfilteredTrackIndex.collectAsStateWithLifecycle()
        IndexRulesDialog(
            title = Strings[R.string.preferences_indexing_whitelist],
            onConfirmOrDismiss = { viewModel.uiManager.closeDialog() },
            rules = preferences.whitelist,
            unfilteredTrackIndex = unfilteredTrackIndex,
            onRemoveRule = { index ->
                viewModel.updatePreferences { it.copy(whitelist = it.whitelist.removeAt(index)) }
            },
            onAddRule = { rule ->
                viewModel.updatePreferences { it.copy(whitelist = it.whitelist + rule) }
            },
            validate = ::regexValidate,
            matchCount = ::regexMatchCount,
            errorText = Strings[R.string.preferences_indexing_invalid_regex],
            footnote = null,
        )
    }
}

@Stable
class PreferencesArtistSeparatorDialog() : Dialog() {
    @Composable
    override fun Compose(viewModel: MainViewModel) {
        val preferences by viewModel.preferences.collectAsStateWithLifecycle()
        val unfilteredTrackIndex by viewModel.unfilteredTrackIndex.collectAsStateWithLifecycle()
        IndexRulesDialog(
            title = Strings[R.string.preferences_indexing_artist_separators],
            onConfirmOrDismiss = { viewModel.uiManager.closeDialog() },
            rules = preferences.artistMetadataSeparators,
            unfilteredTrackIndex = unfilteredTrackIndex,
            onRemoveRule = { index ->
                viewModel.updatePreferences {
                    it.copy(artistMetadataSeparators = it.artistMetadataSeparators.removeAt(index))
                }
            },
            onAddRule = { rule ->
                viewModel.updatePreferences {
                    it.copy(artistMetadataSeparators = it.artistMetadataSeparators + rule)
                }
            },
            validate = { true },
            matchCount = null,
            errorText = null,
            footnote = Strings[R.string.preferences_indexing_rescan_footnote],
        )
    }
}

@Stable
class PreferencesArtistSeparatorExceptionDialog() : Dialog() {
    @Composable
    override fun Compose(viewModel: MainViewModel) {
        val preferences by viewModel.preferences.collectAsStateWithLifecycle()
        val unfilteredTrackIndex by viewModel.unfilteredTrackIndex.collectAsStateWithLifecycle()
        IndexRulesDialog(
            title = Strings[R.string.preferences_indexing_artist_separator_exceptions],
            onConfirmOrDismiss = { viewModel.uiManager.closeDialog() },
            rules = preferences.artistMetadataSeparatorExceptions,
            unfilteredTrackIndex = unfilteredTrackIndex,
            onRemoveRule = { index ->
                viewModel.updatePreferences {
                    it.copy(
                        artistMetadataSeparatorExceptions =
                            it.artistMetadataSeparatorExceptions.removeAt(index)
                    )
                }
            },
            onAddRule = { rule ->
                viewModel.updatePreferences {
                    it.copy(
                        artistMetadataSeparatorExceptions =
                            it.artistMetadataSeparatorExceptions + rule
                    )
                }
            },
            validate = { true },
            matchCount = null,
            errorText = null,
            footnote = Strings[R.string.preferences_indexing_rescan_footnote],
        )
    }
}

@Stable
class PreferencesIndexingHelpDialog() : Dialog() {
    @Composable
    override fun Compose(viewModel: MainViewModel) {
        DialogBase(
            title = Strings[R.string.preferences_indexing_help],
            onConfirmOrDismiss = { viewModel.uiManager.closeDialog() },
        ) {
            Column(
                modifier =
                    Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    Strings[R.string.preferences_indexing_help_artist_separators_title],
                    style = Typography.titleMedium,
                )
                Text(Strings[R.string.preferences_indexing_help_artist_separators_body])
                Text(
                    Strings[R.string.preferences_indexing_help_artist_separator_exceptions_title],
                    style = Typography.titleMedium,
                )
                Text(Strings[R.string.preferences_indexing_help_artist_separator_exceptions_body])
                Text(
                    Strings[R.string.preferences_indexing_help_blacklist_title],
                    style = Typography.titleMedium,
                )
                Text(Strings[R.string.preferences_indexing_help_blacklist_body])
                Text(
                    Strings[R.string.preferences_indexing_help_whitelist_title],
                    style = Typography.titleMedium,
                )
                Text(Strings[R.string.preferences_indexing_help_whitelist_body])
                Text(
                    Strings[R.string.preferences_indexing_help_syntax_title],
                    style = Typography.titleMedium,
                )
                Text(Strings[R.string.preferences_indexing_help_syntax_body])
            }
        }
    }
}

private fun regexValidate(string: String): Boolean {
    return try {
        Regex(string, RegexOption.IGNORE_CASE)
        true
    } catch (_: Exception) {
        false
    }
}

private fun regexMatchCount(string: String, unfilteredTrackIndex: UnfilteredTrackIndex): Int {
    return try {
        val regex = Regex(string, RegexOption.IGNORE_CASE)
        unfilteredTrackIndex.tracks.values.count { regex.containsMatchIn(it.path) }
    } catch (_: Exception) {
        0
    }
}

@Composable
private inline fun IndexRulesDialog(
    title: String,
    noinline onConfirmOrDismiss: () -> Unit,
    rules: List<String>,
    unfilteredTrackIndex: UnfilteredTrackIndex,
    crossinline onRemoveRule: (Int) -> Unit,
    crossinline onAddRule: (String) -> Unit,
    crossinline validate: (String) -> Boolean,
    noinline matchCount: ((String, UnfilteredTrackIndex) -> Int)?,
    errorText: String?,
    footnote: String?,
) {
    var textFieldValue by rememberSaveable { mutableStateOf("") }
    val error = remember(textFieldValue) { !validate(textFieldValue) }
    val matchCount =
        remember(unfilteredTrackIndex, textFieldValue) {
            matchCount?.invoke(textFieldValue, unfilteredTrackIndex)
        }
    DialogBase(title, onConfirmOrDismiss) {
        Column {
            TextField(
                modifier = Modifier.padding(horizontal = 24.dp),
                value = textFieldValue,
                onValueChange = { textFieldValue = it },
                placeholder = {
                    Text(Strings[R.string.preferences_indexing_dialog_input_placeholder])
                },
                trailingIcon = {
                    if (error) {
                        Icon(
                            Icons.Filled.Error,
                            contentDescription = Strings[R.string.commons_error],
                        )
                    } else {
                        IconButton(
                            onClick = {
                                onAddRule(textFieldValue)
                                textFieldValue = ""
                            },
                            enabled = textFieldValue.isNotEmpty(),
                        ) {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = Strings[R.string.commons_add],
                            )
                        }
                    }
                },
                supportingText = {
                    SingleLineText(
                        when {
                            textFieldValue.isEmpty() -> ""
                            error && errorText != null -> errorText
                            matchCount != null ->
                                Strings[R.string.preferences_indexing_dialog_match_count].icuFormat(
                                    matchCount
                                )
                            else -> ""
                        }
                    )
                },
                isError = error,
                singleLine = true,
                keyboardOptions =
                    KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done,
                    ),
                keyboardActions =
                    KeyboardActions {
                        if (!error && textFieldValue.isNotEmpty()) {
                            onAddRule(textFieldValue)
                            textFieldValue = ""
                        } else if (textFieldValue.isEmpty()) {
                            onConfirmOrDismiss()
                        }
                    },
            )

            LazyColumn(modifier = Modifier.weight(1f)) {
                itemsIndexed(rules) { index, rule ->
                    UtilityListItem(
                        title = rule,
                        actions = {
                            IconButton(onClick = { onRemoveRule(index) }) {
                                Icon(
                                    Icons.Filled.Remove,
                                    contentDescription = Strings[R.string.commons_remove],
                                )
                            }
                        },
                    )
                }
            }

            if (footnote != null) {
                Text(
                    footnote,
                    style = Typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp),
                )
            }
        }
    }
}
