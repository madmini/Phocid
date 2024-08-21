package org.sunsetware.phocid.ui.views.preferences

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ibm.icu.text.Collator
import org.sunsetware.phocid.Dialog
import org.sunsetware.phocid.MainViewModel
import org.sunsetware.phocid.R
import org.sunsetware.phocid.Strings
import org.sunsetware.phocid.ui.components.DialogBase
import org.sunsetware.phocid.ui.components.UtilityRadioButtonListItem

@Stable
class PreferencesSortingLocaleDialog : Dialog() {
    @Composable
    override fun Compose(viewModel: MainViewModel) {
        val preferences by viewModel.preferences.collectAsStateWithLifecycle()
        val availableLocales = rememberSaveable {
            listOf(null) + Collator.getAvailableLocales().sortedBy { it.toLanguageTag() }
        }
        var selectedIndex by rememberSaveable {
            mutableIntStateOf(availableLocales.indexOf(preferences.sortingLocale).coerceAtLeast(0))
        }
        DialogBase(
            title = Strings[R.string.preferences_sorting_language],
            onConfirm = {
                viewModel.updatePreferences {
                    it.copy(
                        sortingLocaleLanguageTag = availableLocales[selectedIndex]?.toLanguageTag()
                    )
                }
                viewModel.uiManager.closeDialog()
            },
            onDismiss = { viewModel.uiManager.closeDialog() },
        ) {
            LazyColumn {
                availableLocales.forEachIndexed { index, locale ->
                    item {
                        UtilityRadioButtonListItem(
                            text =
                                locale?.let { "${it.displayName} (${it.toLanguageTag()})" }
                                    ?: Strings[R.string.preferences_sorting_language_system],
                            selected = selectedIndex == index,
                            onSelect = { selectedIndex = index },
                        )
                    }
                }
            }
        }
    }
}
