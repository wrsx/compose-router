package ankers.compose.router

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Button
import androidx.compose.material.Divider
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ankers.compose.router.render.RouterRenderer

// two permanent slots: neither pane ever changes call site, so resizing keeps both intact
fun listDetailRenderer(wide: Boolean): RouterRenderer = {
    val list = entries.firstOrNull()
    val detail = entries.getOrNull(1)
    Row(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = wide || detail == null,
            modifier = Modifier.weight(if (wide) 0.4f else 1f),
        ) {
            list?.let { render(it, active = wide || detail == null) }
        }
        AnimatedContent(
            targetState = detail,
            modifier = Modifier.weight(if (wide) 0.6f else 1f),
            contentKey = { it?.id },
        ) { d -> d?.let { render(it) } }
    }
    projectedEntries.forEach { projectToRoot(it) }
}

@Composable
fun ScreenScope<TabD>.TabDScreen() {
    val items = rememberNavigator()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth > 600.dp
        Router(items, renderer = remember(wide) { listDetailRenderer(wide) }) {
            screen<Items> {
                LazyColumn(Modifier.fillMaxSize()) {
                    items((1..30).toList()) { id ->
                        Text(
                            "Item $id",
                            Modifier.fillMaxWidth().clickable { items.navigate(ItemDetail(id), singleTop = true) }.padding(16.dp),
                        )
                        Divider()
                    }
                }
            }
            screen<ItemDetail> { entry ->
                var note by remember { mutableStateOf("") }
                Column(Modifier.fillMaxSize().padding(16.dp)) {
                    Text("Item ${entry.screen.id}")
                    TextField(note, onValueChange = { note = it }, label = { Text("A note that survives resizing") })
                    Button(onClick = { items.navigate(ItemActions(entry.screen.id)) }) { Text("Actions (local sheet)") }
                }
            }
            // owned by this section: typed against it, removed with it, rendered at the root
            projected<ItemActions> { entry ->
                SheetContent("Item ${entry.screen.id}", "Owned by the Items section; back pops it here.") { items.back() }
            }
        }
    }
}
