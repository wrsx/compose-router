package ankers.compose.router

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun GlobalHeader(showTree: Boolean, onToggleTree: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth()) {
            Text(
                "Compose Router",
                Modifier.align(Alignment.Center).padding(vertical = 8.dp),
                style = MaterialTheme.typography.h5,
            )
            TextButton(onClick = onToggleTree, modifier = Modifier.align(Alignment.CenterEnd)) {
                Text(if (showTree) "Hide tree" else "Tree")
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.Black))
    }
}
