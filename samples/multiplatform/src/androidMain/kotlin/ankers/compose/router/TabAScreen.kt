package ankers.compose.router

import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.runtime.Composable
import ankers.compose.router.navigator.Navigator

@Composable
fun TabAScreen(
    signedInNavigator: Navigator<SignedIn>,
    rootNavigator: Navigator<Root>
) {
    TabScreen("Tab A", Icons.Outlined.Home) {
        Button(
            onClick = { rootNavigator.navigate(ModalStack.then(ModalA)) },
        ) {
            Text("Open Modal")
        }

        Button(
            onClick = {
                signedInNavigator.navigate(
                    TabB
                        .then(NestedTabA)
                        .then(NestedTabB)
                        .then(DeepDestinationA)
                        .then(DeepDestinationB("First"))
                        .then(DeepDestinationB("Second"))
                )
            }
        ) {
            Text("Chain navigate")
        }
    }
}
