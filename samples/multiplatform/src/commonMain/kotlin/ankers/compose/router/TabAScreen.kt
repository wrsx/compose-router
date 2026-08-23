package ankers.compose.router

import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import ankers.compose.router.navigator.Navigator

/** Scoped to the Tab A entry: survives rotation by instance and process death through its handle. */
class CounterViewModel(private val handle: SavedStateHandle) : ViewModel() {
    val instance = instances++
    val count = handle.getStateFlow("count", 0)
    fun increment() { handle["count"] = count.value + 1 }

    private companion object { var instances = 0 }
}

@Composable
fun TabAScreen(tabs: Navigator<SignedIn>, root: Navigator<Root>, onSignOut: () -> Unit) {
    // the entry supplies the extras createSavedStateHandle needs; on Android and desktop viewModel<CounterViewModel>() would also work
    val counter = viewModel { CounterViewModel(createSavedStateHandle()) }
    val count by counter.count.collectAsState()
    TabScreen("Tab A") {
        Button(onClick = counter::increment) { Text("Counter ${counter.instance}: $count") }
        Button(onClick = { root.navigate(ConnectSheet) }) { Text("Global sheet") }
        Button(onClick = { root.navigate(ConfirmDialog) }) { Text("Dialog") }
        Button(onClick = { root.navigate(ModalStack.then(ModalA)) }) { Text("Modal stack") }
        Button(
            onClick = {
                tabs.navigate(
                    TabB.then(NestedTabA).then(NestedTabB)
                        .then(DeepDestinationA)
                        .then(DeepDestinationB("First"))
                        .then(DeepDestinationB("Second")),
                )
            },
        ) { Text("Chain navigate") }
        Button(onClick = onSignOut) { Text("Sign out") }
    }
}
