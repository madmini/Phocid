package org.sunsetware.phocid.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.sunsetware.phocid.ui.theme.Typography

@Composable
fun IndefiniteSnackbar(text: String) {
    ElevatedCard(
        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(4.dp),
        colors =
            CardColors(
                containerColor = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                disabledContainerColor = Color.Unspecified,
                disabledContentColor = Color.Unspecified,
            ),
        elevation = CardDefaults.cardElevation(6.dp, 6.dp, 6.dp, 6.dp, 6.dp, 6.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(text, style = Typography.bodyMedium)
        }
    }
}
