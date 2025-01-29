package ankers.compose.router

import androidx.compose.foundation.layout.Column
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Place
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import ankers.compose.router.navigator.NavConfig

@Composable
fun ScreenScope<TabB>.TabBScreen() {
    val tabBNavigator = rememberNavigator(navConfig = NavConfig.Tab())

    Column {
        NestedTabsTopBar(tabBNavigator)

        Router(tabBNavigator) {
            screen<NestedTabA> {
                TabScreen("State Restoration", Icons.Outlined.DateRange) {
                    var count by rememberSaveable { mutableIntStateOf(0) }

                    Button(onClick = { count++ }) {
                        Text(count.toString())
                    }
                }
            }

            screen<NestedTabB> {
                val deepDestinationNavigator = rememberNavigator()

                Router(deepDestinationNavigator) {
                    screen<DeepDestinationA> {
                        TabScreen("Deep Destination A", Icons.Outlined.Place) {
                            Button(onClick = {
                                deepDestinationNavigator.navigate(DeepDestinationB("Example arg"))
                            }) {
                                Text("Go to next")
                            }
                            Button(onClick = {
                                deepDestinationNavigator.navigate(
                                    DeepDestinationB("First").then(DeepDestinationB("Second"))
                                )
                            }) {
                                Text("Test deep link")
                            }
                        }
                    }

                    screen<DeepDestinationB> { entry ->
                        TabScreen("Deep Destination B", Icons.Outlined.Place) {
                            Text("Received argument: ${entry.screen.arg}")
                        }
                    }
                }
            }
        }
    }
}