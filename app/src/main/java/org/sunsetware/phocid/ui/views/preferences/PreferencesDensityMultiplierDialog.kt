@file:OptIn(ExperimentalMaterial3Api::class)

package org.sunsetware.phocid.ui.views.preferences

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import org.sunsetware.phocid.Dialog
import org.sunsetware.phocid.MainViewModel
import org.sunsetware.phocid.R
import org.sunsetware.phocid.Strings
import org.sunsetware.phocid.ui.components.DialogBase
import org.sunsetware.phocid.ui.components.SteppedSliderWithNumber
import org.sunsetware.phocid.utils.icuFormat

@Stable
class PreferencesDensityMultiplierDialog() : Dialog() {
    @Composable
    override fun Compose(viewModel: MainViewModel) {
        var newMultiplierTimes100 by remember {
            mutableIntStateOf((viewModel.preferences.value.densityMultiplier * 100).roundToInt())
        }
        DialogBase(
            title = Strings[R.string.preferences_ui_scaling],
            onConfirm = {
                viewModel.updatePreferences { preferences ->
                    preferences.copy(densityMultiplier = newMultiplierTimes100 / 100f)
                }
                viewModel.uiManager.closeDialog()
            },
            onDismiss = { viewModel.uiManager.closeDialog() },
        ) {
            SteppedSliderWithNumber(
                number =
                    Strings[R.string.preferences_ui_scaling_number].icuFormat(
                        newMultiplierTimes100 / 100f
                    ),
                onReset = { newMultiplierTimes100 = 100 },
                value = newMultiplierTimes100.toFloat(),
                onValueChange = { newMultiplierTimes100 = it.roundToInt() },
                steps = 200 - 50 - 1,
                valueRange = 50f..200f,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
    }
}
