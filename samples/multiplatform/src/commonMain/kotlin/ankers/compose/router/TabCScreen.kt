package ankers.compose.router

import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable

@Composable
fun ScreenScope<TabC>.TabCScreen() {
    val stack = rememberNavigator()

    Router(stack) {
        screen<StackNavigatorExample> { entry ->
            TabScreen("Stack navigator") {
                Text("entry ${entry.id}")
                Button(onClick = { stack.navigate(StackNavigatorExample) }) { Text("Drill down") }
                Button(onClick = { stack.replace(StackNavigatorExample) }) { Text("Replace") }
                Button(onClick = { stack.popToRoot() }) { Text("Pop to root") }
            }
        }
    }
}
