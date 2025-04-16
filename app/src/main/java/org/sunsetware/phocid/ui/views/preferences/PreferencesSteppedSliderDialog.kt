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
import org.sunsetware.phocid.ui.components.DialogBase
import org.sunsetware.phocid.ui.components.SteppedSliderWithNumber

@Stable
class PreferencesSteppedSliderDialog(
    private val title: String,
    private val initialValue: (MainViewModel) -> Int,
    private val defaultValue: Int,
    private val min: Int,
    private val max: Int,
    private val numberFormatter: (Int) -> String,
    private val onSetValue: (MainViewModel, Int) -> Unit,
) : Dialog() {
    @Composable
    override fun Compose(viewModel: MainViewModel) {
        var value by remember { mutableIntStateOf(initialValue(viewModel)) }
        DialogBase(
            title = title,
            onConfirm = {
                onSetValue(viewModel, value)
                viewModel.uiManager.closeDialog()
            },
            onDismiss = { viewModel.uiManager.closeDialog() },
        ) {
            SteppedSliderWithNumber(
                number = numberFormatter(value),
                onReset = { value = defaultValue },
                value = value.toFloat(),
                onValueChange = { value = it.roundToInt() },
                steps = max - min - 1,
                valueRange = min.toFloat()..max.toFloat(),
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
    }
}
