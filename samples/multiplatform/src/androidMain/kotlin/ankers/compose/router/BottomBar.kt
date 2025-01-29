package ankers.compose.router

import androidx.compose.material.BottomNavigation
import androidx.compose.material.BottomNavigationItem
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.sp

@Composable
fun BottomBar(
    currentScreen: Screen?,
    navigate: (to: ChildScreenOf<SignedIn>) -> Unit,
) {
    BottomNavigation(backgroundColor = Color.White) {
        listOf(
            Tab("Tab A", Icons.Outlined.Home, TabA),
            Tab("Tab B", Icons.Outlined.Call, TabB),
            Tab("Tab C", Icons.Outlined.MailOutline, TabC),
            Tab("Tab D", Icons.Outlined.Settings, TabD)
        ).forEach { tab ->
            BottomNavigationItem(
                selected = currentScreen == tab.screen,
                selectedContentColor = Color.Black,
                icon = {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = "",
                    )
                },
                label = {
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.caption.copy(fontSize = 10.sp)
                    )
                },
                onClick = {
                    navigate(tab.screen)
                }
            )
        }
    }
}

private class Tab(
    val label: String,
    val icon: ImageVector,
    val screen: ChildScreenOf<SignedIn>,
)