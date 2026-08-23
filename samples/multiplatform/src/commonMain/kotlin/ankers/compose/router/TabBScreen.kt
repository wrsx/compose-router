package ankers.compose.router

import androidx.compose.foundation.layout.Column
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import ankers.compose.router.navigator.NavConfig

@Composable
fun ScreenScope<TabB>.TabBScreen() {
    val nested = rememberNavigator(NavConfig.Tab())

    Column {
        NestedTabsTopBar(nested)

        Router(nested) {
            screen<NestedTabA> {
                TabScreen("State restoration") {
                    var count by rememberSaveable { mutableIntStateOf(0) }
                    Button(onClick = { count++ }) { Text(count.toString()) }
                }
            }

            screen<NestedTabB> {
                val deep = rememberNavigator()

                Router(deep) {
                    screen<DeepDestinationA> {
                        TabScreen("Deep destination A") {
                            Button(onClick = { deep.navigate(DeepDestinationB("Example arg")) }) { Text("Go to next") }
                            Button(onClick = { deep.navigate(DeepDestinationB("First").then(DeepDestinationB("Second"))) }) {
                                Text("Chain two")
                            }
                        }
                    }

                    screen<DeepDestinationB> { entry ->
                        TabScreen("Deep destination B") {
                            Text("Received argument: ${entry.screen.arg}")
                            Button(onClick = { deep.popTo<DeepDestinationA>() }) { Text("Pop to A") }
                        }
                    }
                }
            }
        }
    }
}
