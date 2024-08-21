package org.sunsetware.phocid.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import org.sunsetware.phocid.R
import org.sunsetware.phocid.Strings
import org.sunsetware.phocid.utils.negativePadding

// Don't mark these functions as inline unless you want a "Compose internal error"

@Composable
fun DialogBase(
    title: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmText: String = Strings[R.string.commons_ok],
    dismissText: String = Strings[R.string.commons_cancel],
    confirmEnabled: Boolean = true,
    properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit,
) {
    AlertDialog(
        title = { Text(title) },
        text = {
            Box(modifier = Modifier.negativePadding(horizontal = 24.dp).fillMaxWidth()) {
                content()
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = confirmEnabled) { Text(confirmText) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismissText) } },
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.padding(vertical = 16.dp),
        properties = properties,
    )
}

@Composable
fun DialogBase(
    title: String,
    onConfirmOrDismiss: () -> Unit,
    confirmText: String = Strings[R.string.commons_ok],
    properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit,
) {
    AlertDialog(
        title = { Text(title) },
        text = {
            Box(modifier = Modifier.negativePadding(horizontal = 24.dp).fillMaxWidth()) {
                content()
            }
        },
        confirmButton = { TextButton(onClick = onConfirmOrDismiss) { Text(confirmText) } },
        onDismissRequest = onConfirmOrDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.padding(vertical = 16.dp),
        properties = properties,
    )
}
