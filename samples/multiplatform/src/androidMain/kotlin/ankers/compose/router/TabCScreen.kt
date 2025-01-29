package ankers.compose.router

import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable

@Composable
fun ScreenScope<TabC>.TabCScreen() {
    val stackNavigatorExample = rememberNavigator()

    Router(stackNavigatorExample) {
        screen<StackNavigatorExample> { screenEntry ->
            TabScreen("Stack Navigator") {
                Button(onClick = {
                    stackNavigatorExample.navigate(StackNavigatorExample)
                }) {
                    Text("Drill Down (id=${screenEntry.id})")
                }
            }
        }
    }
}