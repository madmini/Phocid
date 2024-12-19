package org.sunsetware.phocid.ui.views

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.sunsetware.phocid.Dialog
import org.sunsetware.phocid.MainViewModel
import org.sunsetware.phocid.data.Lyrics
import org.sunsetware.phocid.ui.components.DialogBase

@Stable
class LyricsDialog(val title: String, val lyrics: Lyrics) : Dialog() {
    @Composable
    override fun Compose(viewModel: MainViewModel) {
        DialogBase(title = title, onConfirmOrDismiss = { viewModel.uiManager.closeDialog() }) {
            LazyColumn(modifier = Modifier.padding(horizontal = 24.dp)) {
                items(lyrics.lines) { (_, text) -> Text(text) }
            }
        }
    }
}
