package ankers.compose.router

import androidx.compose.material.Tab
import androidx.compose.material.TabRow
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import ankers.compose.router.navigator.Navigator

@Composable
fun NestedTabsTopBar(navigator: Navigator<TabB>) {
    val current = navigator.selected?.screen
    TabRow(selectedTabIndex = if (current is NestedTabB) 1 else 0) {
        Tab(selected = current is NestedTabA, onClick = { navigator.navigate(NestedTabA) }) { Text("Nested A") }
        Tab(selected = current is NestedTabB, onClick = { navigator.navigate(NestedTabB) }) { Text("Nested B") }
    }
}
