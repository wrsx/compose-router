package ankers.compose.router

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.Icon
import androidx.compose.material.Tab
import androidx.compose.material.TabRow
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Place
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ankers.compose.router.navigator.Navigator

@Composable
fun NestedTabsTopBar(
    navigator: Navigator<TabB>
) {
    TabRow(
        selectedTabIndex = if (navigator.currentScreen is NestedTabB) 1 else 0
    ) {
        Tab(
            selected = navigator.currentScreen is NestedTabA,
            onClick = {
                navigator.navigate(NestedTabA)
            }
        ) {
            Row(
                Modifier.padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.DateRange,
                    modifier = Modifier
                        .height(21.dp)
                        .padding(end = 8.dp),
                    contentDescription = "",
                )
                Text("Tab A")
            }
        }
        Tab(
            selected = navigator.currentScreen is NestedTabB,
            onClick = {
                println("ankers onClick")
                navigator.navigate(NestedTabB)
            }
        ) {
            Row(
                Modifier.padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Place,
                    modifier = Modifier
                        .height(21.dp)
                        .padding(end = 8.dp),
                    contentDescription = "",
                )
                Text("Tab B")
            }
        }
    }
}