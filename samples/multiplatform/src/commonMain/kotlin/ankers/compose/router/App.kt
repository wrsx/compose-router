package ankers.compose.router

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import ankers.compose.router.navigator.rememberNavigator
import ankers.compose.router.render.OverlayHost
import ankers.compose.router.render.PredictiveBackRenderer

@Composable
fun App() {
    MaterialTheme {
        val root = rememberNavigator<Root>()
        var signedIn by rememberSaveable { mutableStateOf(true) }
        var showTree by rememberSaveable { mutableStateOf(false) }

        OverlayHost(
            modifier = Modifier.fillMaxSize(),
            overlay = { projected -> Overlays(projected, dismiss = { root.back() }) },
        ) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                GlobalHeader(showTree = showTree, onToggleTree = { showTree = !showTree })
                Column(Modifier.weight(1f).fillMaxWidth()) {
                    // the graph reacts to app state: the signed-in section only exists while signed in
                    Router(root, renderer = PredictiveBackRenderer) {
                        if (signedIn) {
                            screen<SignedIn> { TabsExample(root, onSignOut = { signedIn = false }) }
                        } else {
                            screen<SignedOut> {
                                TabScreen("Signed out") {
                                    Button(onClick = { signedIn = true }) { Text("Sign in") }
                                }
                            }
                        }
                        screen<ModalStack> {
                            val modals = rememberNavigator()
                            Router(modals, renderer = PredictiveBackRenderer) { modalScreens(modals) }
                        }
                        // global overlays render at the OverlayHost while the root owns them
                        projected<ConnectSheet> {
                            SheetContent("Connect a device", "Owned by the root: reachable from anywhere.") { root.back() }
                        }
                        projected<ConfirmDialog> {
                            Text("This dialog is a navigation entry: back dismisses it and it survives process death.")
                        }
                    }
                }
                if (showTree) TreePanel(root.describe())
            }
        }
    }
}
