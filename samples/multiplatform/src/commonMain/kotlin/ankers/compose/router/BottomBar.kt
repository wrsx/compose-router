package ankers.compose.router

import androidx.compose.material.BottomNavigation
import androidx.compose.material.BottomNavigationItem
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp

@Composable
fun BottomBar(
    currentScreen: Screen?,
    navigate: (to: ChildScreenOf<SignedIn>) -> Unit,
) {
    BottomNavigation(backgroundColor = Color.White) {
        listOf(
            TabItem("Tab A", "A", TabA),
            TabItem("Tab B", "B", TabB),
            TabItem("Tab C", "C", TabC),
            TabItem("Items", "D", TabD),
        ).forEach { tab ->
            BottomNavigationItem(
                selected = currentScreen == tab.screen,
                selectedContentColor = Color.Black,
                icon = { Text(tab.glyph, style = MaterialTheme.typography.h6) },
                label = { Text(tab.label, style = MaterialTheme.typography.caption.copy(fontSize = 10.sp)) },
                onClick = { navigate(tab.screen) },
            )
        }
    }
}

private class TabItem(val label: String, val glyph: String, val screen: ChildScreenOf<SignedIn>)
