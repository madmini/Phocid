@file:OptIn(ExperimentalFoundationApi::class)

package org.sunsetware.phocid.ui.views.preferences

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.sunsetware.phocid.Dialog
import org.sunsetware.phocid.MainViewModel
import org.sunsetware.phocid.R
import org.sunsetware.phocid.Strings
import org.sunsetware.phocid.ui.components.DialogBase
import org.sunsetware.phocid.ui.components.UtilityCheckBoxListItem
import org.sunsetware.phocid.utils.swap

@Stable
class PreferencesTabsDialog() : Dialog() {
    @Composable
    override fun Compose(viewModel: MainViewModel) {
        val preferences by viewModel.preferences.collectAsStateWithLifecycle()
        DialogBase(
            title = Strings[R.string.preferences_tabs],
            onConfirmOrDismiss = { viewModel.uiManager.closeDialog() },
        ) {
            LazyColumn {
                preferences.tabOrderAndVisibility.forEachIndexed { index, (type, visibility) ->
                    item(type) {
                        UtilityCheckBoxListItem(
                            text = Strings[type.stringId],
                            checked = visibility,
                            onCheckedChange = { newVisibility ->
                                viewModel.updatePreferences { preferences ->
                                    preferences.copy(
                                        tabOrderAndVisibility =
                                            preferences.tabOrderAndVisibility.map {
                                                it.first to
                                                    (if (it.first == type) newVisibility
                                                    else it.second)
                                            }
                                    )
                                }
                            },
                            actions = {
                                IconButton(
                                    onClick = {
                                        if (index > 0) {
                                            viewModel.updatePreferences {
                                                it.copy(
                                                    tabOrderAndVisibility =
                                                        preferences.tabOrderAndVisibility.swap(
                                                            index,
                                                            index - 1,
                                                        )
                                                )
                                            }
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
                                        if (index < preferences.tabOrderAndVisibility.size - 1) {
                                            viewModel.updatePreferences {
                                                it.copy(
                                                    tabOrderAndVisibility =
                                                        preferences.tabOrderAndVisibility.swap(
                                                            index,
                                                            index + 1,
                                                        )
                                                )
                                            }
                                        }
                                    }
                                ) {
                                    Icon(
                                        Icons.Filled.ArrowDownward,
                                        contentDescription = Strings[R.string.list_move_down],
                                    )
                                }
                            },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }
}
