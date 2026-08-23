package ankers.compose.router

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ankers.compose.router.navigator.NavConfig
import ankers.compose.router.navigator.Navigator

@Composable
fun ScreenScope<SignedIn>.TabsExample(rootNavigator: Navigator<Root>, onSignOut: () -> Unit) {
    val tabs = rememberNavigator(NavConfig.Tab())

    Column {
        Column(Modifier.weight(1f)) {
            Router(tabs) {
                screen<TabA> { TabAScreen(tabs, rootNavigator, onSignOut) }
                screen<TabB> { TabBScreen() }
                screen<TabC> { TabCScreen() }
                screen<TabD> { TabDScreen() }
            }
        }
        BottomBar(currentScreen = tabs.selected?.screen, navigate = tabs::navigate)
    }
}
