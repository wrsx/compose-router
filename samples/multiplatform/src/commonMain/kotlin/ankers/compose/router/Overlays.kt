package ankers.compose.router

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ankers.compose.router.render.ProjectedEntry

// the root chooses containers by its own markers; the library only knows "projected"
@Composable
fun Overlays(projected: List<ProjectedEntry>, dismiss: () -> Unit) {
    projected.forEach { item ->
        key(item.entry.id) {
            when (item.entry.screen) {
                is ConfirmDialog -> if (item.visible) {
                    AlertDialog(
                        onDismissRequest = dismiss,
                        title = { Text("Confirm") },
                        text = { item.content() },
                        confirmButton = { Button(onClick = dismiss) { Text("OK") } },
                    )
                }
                else -> Sheet(item, dismiss)
            }
        }
    }
}

// the entry's transition drives both edges: the scrim fades, the sheet slides, and the entry stays live until the exit ends
@Composable
private fun Sheet(item: ProjectedEntry, dismiss: () -> Unit) {
    item.transition.AnimatedVisibility(visible = { it }, enter = fadeIn(), exit = fadeOut()) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable(remember { MutableInteractionSource() }, indication = null, onClick = dismiss),
            )
            Surface(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .animateEnterExit(enter = slideInVertically { it }, exit = slideOutVertically { it }),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                elevation = 16.dp,
            ) {
                Box(Modifier.padding(24.dp)) { item.content() }
            }
        }
    }
}

@Composable
fun SheetContent(title: String, body: String, dismiss: () -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.h6)
        Text(body, Modifier.padding(vertical = 12.dp))
        Button(onClick = dismiss) { Text("Dismiss") }
    }
}

@Composable
fun TreePanel(tree: String) {
    Surface(Modifier.fillMaxWidth(), color = Color(0xFF1E1E1E)) {
        Text(
            tree,
            Modifier.padding(8.dp),
            color = Color(0xFFB5E853),
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
        )
    }
}
