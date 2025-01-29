package ankers.compose.router

import androidx.compose.foundation.layout.Column
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ankers.compose.router.navigator.NavConfig
import ankers.compose.router.navigator.Navigator

@Composable
fun ScreenScope<SignedIn>.TabsExample(
    rootNavigator: Navigator<Root>,
    onSignOut: () -> Unit
) {
    val signedInNavigator = rememberNavigator(NavConfig.Tab())

    Column {
        Column(Modifier.weight(1f)) {
            Router(signedInNavigator) {
                screen<TabA> {
                    TabAScreen(signedInNavigator, rootNavigator)
                }
                screen<TabB> {
                    TabBScreen()
                }
                screen<TabC> {
                    TabCScreen()
                }
                screen<TabD> {
                    TabDScreen(onSignOut)
                }
            }
        }

        BottomBar(
            currentScreen = signedInNavigator.currentScreen,
            navigate = signedInNavigator::navigate,
        )
    }
}